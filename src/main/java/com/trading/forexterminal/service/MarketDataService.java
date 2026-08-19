package com.trading.forexterminal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.forexterminal.model.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Service
public class MarketDataService {

    @Autowired
    private SmcAnalysisService smcAnalysisService;

    @Autowired
    private TradeHistoryService tradeHistoryService;

    @Autowired
    private SuggestionHistoryService suggestionHistoryService;

    @Autowired
    private com.trading.forexterminal.repository.TradeOrderRepository tradeOrderRepository;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private VantageBridgeService vantageBridgeService;

    private volatile double demoBalance = 50000.0;
    private volatile double initialDemoBalance = 50000.0;

    private final Map<String, Map<String, List<Candle>>> candleData = new ConcurrentHashMap<>();
    private final Map<String, Double> basePrices = new ConcurrentHashMap<>();
    private final Map<String, Double> currentPrices = new ConcurrentHashMap<>();
    private final Map<String, Double> dailyHighs = new ConcurrentHashMap<>();
    private final Map<String, Double> dailyLows = new ConcurrentHashMap<>();
    private final Map<String, Double> spreads = new ConcurrentHashMap<>();

    private final List<TradeOrder> activeOrders = new CopyOnWriteArrayList<>();
    private final List<Consumer<Map<String, Object>>> tickListeners = new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    public static final List<String> SUPPORTED_PAIRS = List.of("XAUUSD", "EURUSD", "GBPUSD", "USDJPY", "BTCUSD");
    public static final List<String> SUPPORTED_TIMEFRAMES = List.of("1m", "3m", "5m", "15m", "30m", "1h", "4h", "1d");

    @PostConstruct
    public void init() {
        basePrices.put("XAUUSD", 4488.50);
        basePrices.put("EURUSD", 1.1675);
        basePrices.put("GBPUSD", 1.1800);
        basePrices.put("USDJPY", 154.56);
        basePrices.put("BTCUSD", 68060.00);

        spreads.put("XAUUSD", 0.15);
        spreads.put("EURUSD", 0.00012);
        spreads.put("GBPUSD", 0.00015);
        spreads.put("USDJPY", 0.015);
        spreads.put("BTCUSD", 5.0);

        for (String pair : SUPPORTED_PAIRS) {
            candleData.put(pair, new ConcurrentHashMap<>());
            double base = basePrices.get(pair);
            currentPrices.put(pair, base);
            dailyHighs.put(pair, base * 1.006);
            dailyLows.put(pair, base * 0.994);

            for (String tf : SUPPORTED_TIMEFRAMES) {
                candleData.get(pair).put(tf, generateFallbackCandles(pair, tf, base, 350));
            }
        }

        // 🗄️ Load persistent active & pending limit orders from Database
        try {
            List<com.trading.forexterminal.entity.TradeOrderEntity> entities = tradeOrderRepository.findByStatusInOrderByOpenTimeDesc(List.of("OPEN", "PENDING_LIMIT", "PENDING_HTF_QUEUE"));
            for (com.trading.forexterminal.entity.TradeOrderEntity e : entities) {
                activeOrders.add(toOrderModel(e));
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load active orders from DB: " + e.getMessage());
        }

        fetchRealMarketData();
        scheduler.scheduleAtFixedRate(this::onMarketTick, 1000, 600, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::fetchRealMarketData, 10, 10, TimeUnit.SECONDS);
    }

    public void addTickListener(Consumer<Map<String, Object>> listener) {
        this.tickListeners.add(listener);
    }

    public void fetchRealMarketData() {
        try {
            fetchBinanceKlines("BTCUSD", "BTCUSDT", 1.0);
            fetchBinanceKlines("XAUUSD", "PAXGUSDT", 1.00226);
            fetchBinanceKlines("EURUSD", "EURUSDT", 1.0);
            fetchBinanceKlines("GBPUSD", "GBPUSDT", 1.0);
        } catch (Exception ignored) {}
    }

    private void fetchBinanceKlines(String internalSymbol, String binanceSymbol, double multiplier) {
        for (String tf : SUPPORTED_TIMEFRAMES) {
            try {
                String binanceInterval = switch (tf) {
                    case "3m" -> "3m";
                    case "5m" -> "5m";
                    case "15m" -> "15m";
                    case "30m" -> "30m";
                    case "1h" -> "1h";
                    case "4h" -> "4h";
                    case "1d" -> "1d";
                    default -> "1m";
                };

                String url = "https://api.binance.com/api/v3/klines?symbol=" + binanceSymbol + "&interval=" + binanceInterval + "&limit=350";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(4))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonNode array = objectMapper.readTree(response.body());
                    if (array.isArray() && array.size() > 10) {
                        List<Candle> liveList = new ArrayList<>();
                        for (JsonNode item : array) {
                            long ts = item.get(0).asLong();
                            double open = item.get(1).asDouble() * multiplier;
                            double high = item.get(2).asDouble() * multiplier;
                            double low = item.get(3).asDouble() * multiplier;
                            double close = item.get(4).asDouble() * multiplier;
                            double vol = item.get(5).asDouble();

                            liveList.add(new Candle(ts, round(open, 5), round(high, 5), round(low, 5), round(close, 5), round(vol, 2)));
                        }

                        // ⚡ Real-Time Forward Fill: Ensure candles always reach 100% live current time
                        long now = System.currentTimeMillis();
                        long interval = getIntervalMs(tf);
                        if (!liveList.isEmpty()) {
                            Candle last = liveList.get(liveList.size() - 1);
                            long lastTs = last.getTimestamp();
                            while (now - lastTs >= interval && (now - lastTs < 24 * 3600 * 1000L)) {
                                lastTs += interval;
                                double prevClose = last.getClose();
                                double step = getVolatilityStep(internalSymbol);
                                double delta = (random.nextDouble() - 0.495) * step * 0.15;
                                double nextClose = round(prevClose + delta, 5);
                                last = new Candle(
                                        lastTs,
                                        prevClose,
                                        Math.max(prevClose, nextClose),
                                        Math.min(prevClose, nextClose),
                                        nextClose,
                                        100.0
                                );
                                liveList.add(last);
                                if (liveList.size() > 500) liveList.remove(0);
                            }
                        }

                        candleData.get(internalSymbol).put(tf, liveList);

                        if ("1m".equals(tf) && !liveList.isEmpty()) {
                            double lastClose = liveList.get(liveList.size() - 1).getClose();
                            currentPrices.put(internalSymbol, lastClose);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void fetchLivePriceQuote(String internalSymbol, String url, double multiplier) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                if (node.has("price")) {
                    double p = node.get("price").asDouble() * multiplier;
                    currentPrices.put(internalSymbol, round(p, 5));
                }
            }
        } catch (Exception ignored) {}
    }

    private List<Candle> generateFallbackCandles(String symbol, String timeframe, double startPrice, int count) {
        List<Candle> list = new ArrayList<>();
        long intervalMs = getIntervalMs(timeframe);
        long now = System.currentTimeMillis();
        long startTime = now - (count * intervalMs);

        double price = startPrice;
        double step = getVolatilityStep(symbol);
        double trendDirection = 1.0;
        int trendSteps = 12;
        int currentTrendCount = 0;

        for (int i = 0; i < count; i++) {
            long time = startTime + (i * intervalMs);
            if (++currentTrendCount > trendSteps) {
                trendDirection *= -1.0;
                currentTrendCount = 0;
                trendSteps = 8 + random.nextInt(12);
            }

            double delta = (trendDirection * step * 0.7) + ((random.nextDouble() - 0.48) * step * 1.5);
            double open = price;
            double close = open + delta;

            if (i % 15 == 0) {
                close = open + (trendDirection * step * 3.8);
            }

            double high = Math.max(open, close) + random.nextDouble() * step * 0.7;
            double low = Math.min(open, close) - random.nextDouble() * step * 0.7;
            double volume = 400 + random.nextDouble() * 2000;

            list.add(new Candle(time, round(open, 5), round(high, 5), round(low, 5), round(close, 5), round(volume, 1)));
            price = close;
        }

        return list;
    }

    private synchronized void onMarketTick() {
        for (String pair : SUPPORTED_PAIRS) {
            double current = currentPrices.get(pair);
            double step = getVolatilityStep(pair);
            double delta = (random.nextDouble() - 0.495) * step * 0.2;
            double newPrice = round(current + delta, 5);
            currentPrices.put(pair, newPrice);

            if (newPrice > dailyHighs.get(pair)) dailyHighs.put(pair, newPrice);
            if (newPrice < dailyLows.get(pair)) dailyLows.put(pair, newPrice);

            long now = System.currentTimeMillis();
            for (String tf : SUPPORTED_TIMEFRAMES) {
                List<Candle> list = candleData.get(pair).get(tf);
                if (list != null && !list.isEmpty()) {
                    Candle last = list.get(list.size() - 1);
                    long interval = getIntervalMs(tf);

                    if (now - last.getTimestamp() >= interval) {
                        Candle newCandle = new Candle(
                                now,
                                last.getClose(),
                                Math.max(last.getClose(), newPrice),
                                Math.min(last.getClose(), newPrice),
                                newPrice,
                                50.0
                        );
                        list.add(newCandle);
                        if (list.size() > 500) {
                            list.remove(0);
                        }
                    } else {
                        last.setClose(newPrice);
                        if (newPrice > last.getHigh()) last.setHigh(newPrice);
                        if (newPrice < last.getLow()) last.setLow(newPrice);
                        last.setVolume(last.getVolume() + random.nextDouble() * 5);
                    }
                }
            }

            checkActiveOrders(pair, newPrice);
            checkSuggestionsEvaluation(pair, newPrice);
        }

        if (!tickListeners.isEmpty()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "TICK");
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("prices", currentPrices);

            for (Consumer<Map<String, Object>> listener : tickListeners) {
                try {
                    listener.accept(payload);
                } catch (Exception ignored) {}
            }
        }
    }

    private void checkSuggestionsEvaluation(String pair, double currentPrice) {
        List<SuggestionHistoryItem> suggestions = suggestionHistoryService.getAllSuggestions();
        for (SuggestionHistoryItem sugg : suggestions) {
            if (pair.equals(sugg.getSymbol())) {
                String state = sugg.getTriggerState();
                if ("PENDING_WAIT".equals(state)) {
                    // Check if price reaches/taps 50% FVG entry zone
                    boolean reached = false;
                    if ("BUY".equals(sugg.getSignal()) && currentPrice <= sugg.getEntryPrice()) {
                        reached = true;
                    } else if ("SELL".equals(sugg.getSignal()) && currentPrice >= sugg.getEntryPrice()) {
                        reached = true;
                    }
                    if (reached) {
                        suggestionHistoryService.updateSuggestionExecution(sugg.getId(), sugg.getEntryPrice(), 0.0, "READY_EXECUTED");
                    }
                } else if ("READY_EXECUTED".equals(state)) {
                    // Trade is active: check Pass (TP Hit) vs Fail (SL Hit)
                    double mult = "BTCUSD".equals(pair) ? 1.0 : ("XAUUSD".equals(pair) ? 100.0 : 100000.0);
                    double defaultLots = 0.10;

                    if ("BUY".equals(sugg.getSignal())) {
                        if (currentPrice >= sugg.getTakeProfit()) {
                            // ✅ PASSED: 1:3+ TP HIT!
                            double profit = (sugg.getTakeProfit() - sugg.getEntryPrice()) * mult * defaultLots;
                            suggestionHistoryService.updateSuggestionExecution(sugg.getId(), sugg.getTakeProfit(), round(profit, 2), "TP_HIT");
                        } else if (currentPrice <= sugg.getStopLoss()) {
                            // ❌ FAILED: SL HIT
                            double loss = (sugg.getStopLoss() - sugg.getEntryPrice()) * mult * defaultLots;
                            suggestionHistoryService.updateSuggestionExecution(sugg.getId(), sugg.getStopLoss(), round(loss, 2), "SL_HIT");
                        }
                    } else if ("SELL".equals(sugg.getSignal())) {
                        if (currentPrice <= sugg.getTakeProfit()) {
                            // ✅ PASSED: 1:3+ TP HIT!
                            double profit = (sugg.getEntryPrice() - sugg.getTakeProfit()) * mult * defaultLots;
                            suggestionHistoryService.updateSuggestionExecution(sugg.getId(), sugg.getTakeProfit(), round(profit, 2), "TP_HIT");
                        } else if (currentPrice >= sugg.getStopLoss()) {
                            // ❌ FAILED: SL HIT
                            double loss = (sugg.getEntryPrice() - sugg.getStopLoss()) * mult * defaultLots;
                            suggestionHistoryService.updateSuggestionExecution(sugg.getId(), sugg.getStopLoss(), round(loss, 2), "SL_HIT");
                        }
                    }
                }
            }
        }
    }

    private void checkActiveOrders(String pair, double currentPrice) {
        double spread = spreads.getOrDefault(pair, 0.1);
        for (TradeOrder order : activeOrders) {
            if (pair.equals(order.getSymbol())) {
                // 1. If Order is PENDING_LIMIT: Check if price reaches/taps Entry Zone
                if ("PENDING_LIMIT".equals(order.getStatus())) {
                    boolean triggered = false;
                    if ("BUY".equals(order.getType()) && currentPrice <= order.getEntryPrice()) {
                        triggered = true;
                    } else if ("SELL".equals(order.getType()) && currentPrice >= order.getEntryPrice()) {
                        triggered = true;
                    }

                    if (triggered) {
                        order.setStatus("OPEN");
                        order.setOpenTime(System.currentTimeMillis());
                        order.setTrailingStatus("STANDARD");
                        order.updatePnL(currentPrice, spread);
                    }
                    continue; // Do not check SL/TP on unfilled limit orders
                }

                // 2. If Order is OPEN: Check Dual Trailing SL and TP/SL Exits
                if ("OPEN".equals(order.getStatus())) {
                    order.updatePnL(currentPrice, spread);

                    // =========================================================================
                    // 🛡️ DUAL PROBABILITY-AWARE TRAILING SL ENGINE:
                    // Rule 1: High TP Chance (>= 80%): Move SL to 0 Risk (Break-Even = Entry)
                    // Rule 2: Low/Moderate TP Chance (< 80%): Move SL to PLUS SL (Lock In Positive Profit)
                    // =========================================================================
                    double entry = order.getEntryPrice();
                    double sl = order.getStopLoss();
                    double tp = order.getTakeProfit();

                    // Check symbol TP probability from active analysis
                    int tpChance = 75;
                    try {
                        List<Candle> m5Candles = candleData.getOrDefault(pair, Collections.emptyMap()).getOrDefault("5m", Collections.emptyList());
                        if (!m5Candles.isEmpty()) {
                            AnalysisResult a = smcAnalysisService.analyzeMarket(pair, "5m", "SCALP", new ArrayList<>(m5Candles), spread);
                            if (a != null && a.getTradeSetup() != null) {
                                tpChance = a.getTradeSetup().getConfidence();
                            }
                        }
                    } catch (Exception ignored) {}

                    if ("BUY".equals(order.getType())) {
                        double totalTarget = tp - entry;
                        double currentGain = currentPrice - entry;
                        double progress = totalTarget > 0 ? (currentGain / totalTarget) : 0.0;

                        // When trade is positively in profit (>= 25% towards TP)
                        if (progress >= 0.25) {
                            if (tpChance >= 80) {
                                // High TP Probability (>= 80%): Trail to 0 Risk (Break-Even)
                                if (sl < entry) {
                                    order.setStopLoss(round(entry, 5));
                                    order.setTrailingStatus("🛡️ BREAK-EVEN (0 RISK | 80%+ TP CHANCE)");
                                }
                            } else {
                                // Moderate/Low TP Probability (< 80%): Move to PLUS SL (Profit-Lock)
                                double plusSl = entry + (currentGain * 0.50);
                                if (plusSl > sl) {
                                    order.setStopLoss(round(plusSl, 5));
                                    order.setTrailingStatus("🔥 PLUS SL (+PROFIT LOCKED | <80% TP CHANCE)");
                                }
                            }
                        }

                        // BUY is closed at current Bid
                        double currentBid = currentPrice;
                        if (currentBid >= order.getTakeProfit()) {
                            order.setStatus("CLOSED_TP");
                            order.setClosePrice(currentBid);
                            order.setCloseTime(System.currentTimeMillis());
                            order.updatePnL(currentBid, spread);
                            tradeHistoryService.recordClosedTrade(order);
                            activeOrders.remove(order);
                        } else if (currentBid <= order.getStopLoss()) {
                            order.setStatus("CLOSED_SL");
                            order.setClosePrice(currentBid);
                            order.setCloseTime(System.currentTimeMillis());
                            order.updatePnL(currentBid, spread);
                            tradeHistoryService.recordClosedTrade(order);
                            activeOrders.remove(order);
                        }
                    } else if ("SELL".equals(order.getType())) {
                        double totalTarget = entry - tp;
                        double currentGain = entry - currentPrice;
                        double progress = totalTarget > 0 ? (currentGain / totalTarget) : 0.0;

                        // When trade is positively in profit (>= 25% towards TP)
                        if (progress >= 0.25) {
                            if (tpChance >= 80) {
                                // High TP Probability (>= 80%): Trail to 0 Risk (Break-Even)
                                if (sl > entry) {
                                    order.setStopLoss(round(entry, 5));
                                    order.setTrailingStatus("🛡️ BREAK-EVEN (0 RISK | 80%+ TP CHANCE)");
                                }
                            } else {
                                // Moderate/Low TP Probability (< 80%): Move to PLUS SL (Profit-Lock)
                                double plusSl = entry - (currentGain * 0.50);
                                if (plusSl < sl) {
                                    order.setStopLoss(round(plusSl, 5));
                                    order.setTrailingStatus("🔥 PLUS SL (+PROFIT LOCKED | <80% TP CHANCE)");
                                }
                            }
                        }

                        // SELL is closed at current Ask (currentPrice + spread)
                        double currentAsk = currentPrice + spread;
                        if (currentAsk <= order.getTakeProfit()) {
                            order.setStatus("CLOSED_TP");
                            order.setClosePrice(currentAsk);
                            order.setCloseTime(System.currentTimeMillis());
                            order.updatePnL(currentPrice, spread);
                            tradeHistoryService.recordClosedTrade(order);
                            activeOrders.remove(order);
                            checkAndReleaseQueuedHtfOrders(order.getSymbol());
                        } else if (currentAsk >= order.getStopLoss()) {
                            order.setStatus("CLOSED_SL");
                            order.setClosePrice(currentAsk);
                            order.setCloseTime(System.currentTimeMillis());
                            order.updatePnL(currentPrice, spread);
                            tradeHistoryService.recordClosedTrade(order);
                            activeOrders.remove(order);
                            checkAndReleaseQueuedHtfOrders(order.getSymbol());
                        }
                    }
                }
            }
        }
    }

    public AnalysisResult getAnalysis(String symbol, String timeframe, String tradeMode) {
        String pair = symbol.toUpperCase();
        if (!SUPPORTED_PAIRS.contains(pair)) pair = "XAUUSD";
        if (!SUPPORTED_TIMEFRAMES.contains(timeframe)) timeframe = "1m";

        List<Candle> candles = candleData.get(pair).get(timeframe);
        double spread = spreads.getOrDefault(pair, 0.1);

        return smcAnalysisService.analyzeMarket(pair, timeframe, tradeMode, new ArrayList<>(candles), spread);
    }

    public AnalysisResult getAnalysis(String symbol, String timeframe) {
        return getAnalysis(symbol, timeframe, "SCALP");
    }

    public TradeAdvisorResult getTradeAdvisor(String symbol, String timeframe, String tradeMode, double lotSize) {
        String pair = symbol.toUpperCase();
        if (!SUPPORTED_PAIRS.contains(pair)) pair = "XAUUSD";
        if (!SUPPORTED_TIMEFRAMES.contains(timeframe)) timeframe = "1m";
        if (lotSize <= 0) lotSize = 0.10;

        List<Candle> candles = candleData.get(pair).get(timeframe);
        double spread = spreads.getOrDefault(pair, 0.1);

        return smcAnalysisService.generateTradeAdvisor(pair, timeframe, tradeMode, lotSize, new ArrayList<>(candles), spread);
    }

    public void checkAndReleaseQueuedHtfOrders(String symbol) {
        // Check if there are any remaining active OPEN or PENDING_LIMIT orders in the opposite direction
        for (TradeOrder htfOrder : activeOrders) {
            if (symbol.equals(htfOrder.getSymbol()) && "PENDING_HTF_QUEUE".equals(htfOrder.getStatus())) {
                boolean hasOppositeActive = activeOrders.stream().anyMatch(o ->
                        symbol.equals(o.getSymbol()) &&
                        !htfOrder.getType().equalsIgnoreCase(o.getType()) &&
                        ("OPEN".equals(o.getStatus()) || "PENDING_LIMIT".equals(o.getStatus()))
                );

                if (!hasOppositeActive) {
                    double curPrice = currentPrices.getOrDefault(symbol, htfOrder.getEntryPrice());
                    double spread = spreads.getOrDefault(symbol, 0.1);
                    boolean isLimit = Math.abs(htfOrder.getEntryPrice() - curPrice) > (spread * 1.5);

                    if (isLimit) {
                        htfOrder.setStatus("PENDING_LIMIT");
                        htfOrder.setTrailingStatus("⏳ PENDING LIMIT (LTF PULLBACK FINISHED)");
                    } else {
                        htfOrder.setStatus("OPEN");
                        htfOrder.setOpenTime(System.currentTimeMillis());
                        htfOrder.setTrailingStatus("🚀 HTF RELEASED (LTF PULLBACK FINISHED)");
                        htfOrder.updatePnL(curPrice, spread);
                    }

                    try {
                        tradeOrderRepository.save(toOrderEntity(htfOrder));
                    } catch (Exception ignored) {}

                    // Forward to Vantage MT5 Bridge now that conflicting LTF trade is finished!
                    try {
                        if (vantageBridgeService != null) {
                            vantageBridgeService.queueOrder(
                                    htfOrder.getSymbol(),
                                    htfOrder.getType(),
                                    htfOrder.getLotSize(),
                                    htfOrder.getStopLoss(),
                                    htfOrder.getTakeProfit()
                            );
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public int getTimeframeRank(String tf) {
        if (tf == null) return 4;
        return switch (tf.toLowerCase().trim()) {
            case "1m" -> 1;
            case "3m" -> 2;
            case "5m" -> 3;
            case "15m" -> 4;
            case "30m" -> 5;
            case "1h" -> 6;
            case "4h" -> 7;
            case "1d" -> 8;
            default -> 4;
        };
    }

    public List<TradeRadarItem> getFutureSetupRadar() {
        List<TradeRadarItem> list = smcAnalysisService.scanFutureSetups(candleData, currentPrices, spreads);
        for (TradeRadarItem item : list) {
            suggestionHistoryService.recordSuggestion(item);
        }
        return list;
    }

    public List<SuggestionHistoryItem> getSuggestionHistory() {
        return suggestionHistoryService.getAllSuggestions();
    }

    public int clearSuggestionHistory() {
        return suggestionHistoryService.clearSuggestionHistory();
    }

    public List<Candle> getCandles(String symbol, String timeframe) {
        if (candleData.containsKey(symbol) && candleData.get(symbol).containsKey(timeframe)) {
            return new ArrayList<>(candleData.get(symbol).get(timeframe));
        }
        return Collections.emptyList();
    }

    public TradeOrder placeOrder(String symbol, String type, double lotSize, double entry, double sl, double tp) {
        return placeOrder(symbol, type, "15m", lotSize, entry, sl, tp);
    }

    /**
     * Timeframe Priority Anti-Hedging & Spread-Aware Order Placement
     */
    public TradeOrder placeOrder(String symbol, String type, String timeframe, double lotSize, double entry, double sl, double tp) {
        double currentPrice = currentPrices.getOrDefault(symbol, entry);
        double spread = spreads.getOrDefault(symbol, 0.1);
        int incomingRank = getTimeframeRank(timeframe);

        // Check if this is a pending limit order waiting for price pullback to 50% FVG
        boolean isLimitOrder = entry > 0 && Math.abs(entry - currentPrice) > (spread * 1.5);
        double actualEntry = isLimitOrder ? entry : ("BUY".equals(type) ? (currentPrice + spread) : currentPrice);

        TradeOrder order = new TradeOrder(
                "ORD-" + System.currentTimeMillis(),
                symbol,
                type,
                timeframe,
                round(actualEntry, 5),
                round(sl, 5),
                round(tp, 5),
                lotSize,
                spread,
                System.currentTimeMillis()
        );

        // =========================================================================
        // 🎯 TIMEFRAME PRIORITY ANTI-HEDGING & CONFLICT EXECUTION RULE:
        // Rule 1: Same Direction -> No Conflict! Both allowed.
        // Rule 2: Opposite Direction (BUY vs SELL on same pair):
        //         - Lower Timeframe (LTF) is executed immediately / active limit!
        //         - Higher Timeframe (HTF) is queued into PENDING_HTF_QUEUE!
        //         - When LTF closes, HTF is automatically released and executed!
        // =========================================================================
        List<TradeOrder> oppositeActiveOrders = activeOrders.stream()
                .filter(o -> symbol.equals(o.getSymbol()) &&
                        !type.equalsIgnoreCase(o.getType()) &&
                        ("OPEN".equals(o.getStatus()) || "PENDING_LIMIT".equals(o.getStatus())))
                .toList();

        boolean shouldQueueForHtf = false;

        if (!oppositeActiveOrders.isEmpty()) {
            for (TradeOrder opp : oppositeActiveOrders) {
                int oppRank = getTimeframeRank(opp.getTimeframe());
                if (incomingRank > oppRank) {
                    // Incoming order is HIGHER timeframe (e.g. 4h BUY vs active 15m SELL)
                    shouldQueueForHtf = true;
                    order.setStatus("PENDING_HTF_QUEUE");
                    order.setTrailingStatus("⏳ HTF QUEUED (Waiting for LTF " + opp.getTimeframe() + " " + opp.getType() + " to finish)");
                    order.setPnl(0.0);
                    break;
                } else if (incomingRank < oppRank) {
                    // Incoming order is LOWER timeframe (e.g. 15m SELL vs existing 4h BUY)
                    // Suspend existing HTF order to queue, let incoming LTF execute!
                    opp.setStatus("PENDING_HTF_QUEUE");
                    opp.setTrailingStatus("⏳ HTF QUEUED (Waiting for active LTF " + timeframe + " " + type + " to finish)");
                    try {
                        tradeOrderRepository.save(toOrderEntity(opp));
                    } catch (Exception ignored) {}
                }
            }
        }

        if (!shouldQueueForHtf) {
            if (isLimitOrder) {
                order.setStatus("PENDING_LIMIT");
                order.setTrailingStatus("⏳ PENDING LIMIT (WAITING FOR FVG TAP)");
                order.setPnl(0.0);
            } else {
                order.setStatus("OPEN");
                order.updatePnL(currentPrice, spread);
            }
        }

        try {
            tradeOrderRepository.save(toOrderEntity(order));
        } catch (Exception ignored) {}

        activeOrders.add(0, order);
        return order;
    }

    public TradeOrder closeOrder(String orderId) {
        for (TradeOrder order : activeOrders) {
            if (order.getId().equals(orderId) && ("OPEN".equals(order.getStatus()) || "PENDING_LIMIT".equals(order.getStatus()) || "PENDING_HTF_QUEUE".equals(order.getStatus()))) {
                if ("PENDING_LIMIT".equals(order.getStatus()) || "PENDING_HTF_QUEUE".equals(order.getStatus())) {
                    order.setStatus("CANCELLED");
                    activeOrders.remove(order);
                    try {
                        tradeOrderRepository.deleteById(orderId);
                    } catch (Exception ignored) {}
                    checkAndReleaseQueuedHtfOrders(order.getSymbol());
                    return order;
                }
                order.setStatus("CLOSED_MANUAL");
                double currentPrice = currentPrices.getOrDefault(order.getSymbol(), order.getEntryPrice());
                double spread = spreads.getOrDefault(order.getSymbol(), 0.1);
                double closePrice = "BUY".equals(order.getType()) ? currentPrice : (currentPrice + spread);

                order.setClosePrice(round(closePrice, 5));
                order.setCloseTime(System.currentTimeMillis());
                order.updatePnL(currentPrice, spread);
                tradeHistoryService.recordClosedTrade(order);
                activeOrders.remove(order);
                checkAndReleaseQueuedHtfOrders(order.getSymbol());
                return order;
            }
        }
        return null;
    }

    public boolean deleteOrder(String orderId) {
        TradeOrder found = null;
        for (TradeOrder o : activeOrders) {
            if (o.getId().equals(orderId)) {
                found = o;
                break;
            }
        }
        boolean removedFromActive = activeOrders.removeIf(order -> order.getId().equals(orderId));
        try {
            tradeOrderRepository.deleteById(orderId);
        } catch (Exception ignored) {}
        if (found != null) {
            checkAndReleaseQueuedHtfOrders(found.getSymbol());
        }
        return removedFromActive;
    }

    public int clearTradeHistory() {
        activeOrders.removeIf(order -> !"OPEN".equals(order.getStatus()) && !"PENDING_LIMIT".equals(order.getStatus()) && !"PENDING_HTF_QUEUE".equals(order.getStatus()));
        return tradeHistoryService.clearHistory();
    }

    public void clearAllOrders() {
        activeOrders.clear();
        try {
            tradeOrderRepository.deleteAll();
        } catch (Exception ignored) {}
        tradeHistoryService.clearHistory();
    }

    public List<TradeOrder> getOrders() {
        List<TradeOrder> merged = new ArrayList<>();
        // Add active open, pending limit, and queued HTF orders first
        for (TradeOrder o : activeOrders) {
            if ("OPEN".equals(o.getStatus()) || "PENDING_LIMIT".equals(o.getStatus()) || "PENDING_HTF_QUEUE".equals(o.getStatus())) {
                merged.add(o);
            }
        }
        // Add persistent closed history
        merged.addAll(tradeHistoryService.getAllHistory());
        return merged;
    }

    public double getDemoBalance() {
        return demoBalance;
    }

    public void setDemoBalance(double balance) {
        this.demoBalance = Math.max(10.0, balance);
        this.initialDemoBalance = this.demoBalance;
    }

    public double getInitialDemoBalance() {
        return initialDemoBalance;
    }

    public double getSpread(String symbol) {
        return spreads.getOrDefault(symbol, 0.1);
    }

    private double getVolatilityStep(String symbol) {
        if ("XAUUSD".equals(symbol)) return 1.25;
        if ("EURUSD".equals(symbol)) return 0.00025;
        if ("GBPUSD".equals(symbol)) return 0.00035;
        if ("USDJPY".equals(symbol)) return 0.045;
        if ("BTCUSD".equals(symbol)) return 65.0;
        return 0.5;
    }

    public Map<String, List<Candle>> getAllTimeframeCandles(String symbol) {
        if (candleData.containsKey(symbol)) {
            return new HashMap<>(candleData.get(symbol));
        }
        return Collections.emptyMap();
    }

    private long getIntervalMs(String timeframe) {
        return switch (timeframe) {
            case "1m" -> 60_000L;
            case "3m" -> 180_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "30m" -> 1_800_000L;
            case "1h" -> 3_600_000L;
            case "4h" -> 14_400_000L;
            case "1d" -> 86_400_000L;
            default -> 60_000L;
        };
    }

    private double round(double val, int decimals) {
        double p = Math.pow(10, decimals);
        return Math.round(val * p) / p;
    }

    private com.trading.forexterminal.entity.TradeOrderEntity toOrderEntity(TradeOrder o) {
        return new com.trading.forexterminal.entity.TradeOrderEntity(
                o.getId(),
                o.getSymbol(),
                o.getType(),
                o.getTimeframe(),
                o.getEntryPrice(),
                o.getStopLoss(),
                o.getTakeProfit(),
                o.getLotSize(),
                o.getSpreadAtEntry(),
                o.getOpenTime(),
                o.getCloseTime(),
                o.getClosePrice(),
                o.getPnl(),
                o.getStatus(),
                o.getTrailingStatus()
        );
    }

    private TradeOrder toOrderModel(com.trading.forexterminal.entity.TradeOrderEntity e) {
        TradeOrder o = new TradeOrder(
                e.getId(),
                e.getSymbol(),
                e.getType(),
                e.getTimeframe(),
                e.getEntryPrice(),
                e.getStopLoss(),
                e.getTakeProfit(),
                e.getLotSize(),
                e.getSpreadAtEntry(),
                e.getOpenTime()
        );
        o.setCloseTime(e.getCloseTime());
        o.setClosePrice(e.getClosePrice());
        o.setPnl(e.getPnl());
        o.setStatus(e.getStatus());
        o.setTrailingStatus(e.getTrailingStatus());
        return o;
    }
}

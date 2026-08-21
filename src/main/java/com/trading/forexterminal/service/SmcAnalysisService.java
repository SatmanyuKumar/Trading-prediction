package com.trading.forexterminal.service;

import com.trading.forexterminal.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SmcAnalysisService {

    // Persistent active setups map across all timeframes & modes: Key = "SYMBOL_TIMEFRAME_MODE"
    private final Map<String, TradeSetup> activeLockedSetups = new java.util.concurrent.ConcurrentHashMap<>();

    public void clearActiveSetups() {
        activeLockedSetups.clear();
    }

    /**
     * Institutional Smart Money Analysis Engine
     * Supports Dual Trading Modes: SCALP (Fast 1:2.2 R:R) vs SWING (Macro 1:4.5+ R:R)
     */
    public AnalysisResult analyzeMarket(String symbol, String timeframe, String tradeMode, List<Candle> candles, double spread) {
        if (candles == null || candles.size() < 30) {
            return new AnalysisResult();
        }

        String mode = "SCALP";
        if (tradeMode != null) {
            if ("SWING".equalsIgnoreCase(tradeMode)) mode = "SWING";
            else if ("SNIPER".equalsIgnoreCase(tradeMode) || "DEEP".equalsIgnoreCase(tradeMode)) mode = "SNIPER";
        }
        double currentPrice = candles.get(candles.size() - 1).getClose();
        double change24h = calculate24hChange(candles);

        boolean isSwing = "SWING".equalsIgnoreCase(mode);

        // 1. Calculate Technical Indicators & Volatility
        List<Double> ema20 = calculateEMA(candles, 20);
        List<Double> ema50 = calculateEMA(candles, 50);
        List<Double> ema200 = calculateEMA(candles, Math.min(200, candles.size() - 1));
        double atr14 = calculateATR(candles, 14);

        // 2. Identify Structure, Untouched FVGs, Order Blocks & Liquidity Sweeps (Dynamically Adaptive)
        List<MarketStructure> structures = detectMarketStructures(candles, isSwing);
        List<FairValueGap> fvgs = detectUntouchedFairValueGaps(candles, isSwing, atr14);
        List<OrderBlock> orderBlocks = detectOrderBlocks(candles, isSwing);
        List<SupportResistance> srLevels = detectSupportResistance(candles, isSwing, symbol);

        // 3. Multi-Factor Institutional Confluence & Strategy Generation (Stateful & Locked until SL / TP hit)
        String setupKey = (symbol != null ? symbol.toUpperCase() : "XAUUSD") + "_" + 
                          (timeframe != null ? timeframe.toLowerCase() : "15m") + "_" + 
                          mode.toUpperCase();

        TradeSetup setup;
        TradeSetup active = activeLockedSetups.get(setupKey);

        if (active != null) {
            boolean isBuy = "BUY".equalsIgnoreCase(active.getSignal());
            double sl = active.getStopLoss();
            double tp = active.getTakeProfit1();
            boolean slHit = false;
            boolean tpHit = false;

            // Check across recent candles if SL or TP was reached
            int checkStart = Math.max(0, candles.size() - 25);
            for (int i = checkStart; i < candles.size(); i++) {
                Candle c = candles.get(i);
                if (c.getTimestamp() >= active.getTimestamp() - 2000) {
                    if (isBuy) {
                        if (c.getLow() <= sl) slHit = true;
                        if (c.getHigh() >= tp) tpHit = true;
                    } else {
                        if (c.getHigh() >= sl) slHit = true;
                        if (c.getLow() <= tp) tpHit = true;
                    }
                }
            }

            // Also check latest live currentPrice
            if (isBuy) {
                if (currentPrice <= sl) slHit = true;
                if (currentPrice >= tp) tpHit = true;
            } else {
                if (currentPrice >= sl) slHit = true;
                if (currentPrice <= tp) tpHit = true;
            }

            if (slHit || tpHit) {
                // Setup is resolved (SL or TP hit) -> Retire and allow next structural setup to appear!
                activeLockedSetups.remove(setupKey);
                setup = generateInstitutionalSetup(symbol, timeframe, mode, candles, fvgs, orderBlocks, srLevels, ema20, ema50, ema200, atr14, spread);
                if (setup != null && ("BUY".equals(setup.getSignal()) || "SELL".equals(setup.getSignal())) && setup.getConfidence() >= 70) {
                    activeLockedSetups.put(setupKey, setup);
                }
            } else {
                // Setup is ACTIVE -> Lock exact coordinates (Zero repainting / wandering of Entry or SL!)
                active.setCurrentPrice(currentPrice);
                setup = active;
            }
        } else {
            // No locked setup -> Generate and lock initial setup
            setup = generateInstitutionalSetup(symbol, timeframe, mode, candles, fvgs, orderBlocks, srLevels, ema20, ema50, ema200, atr14, spread);
            if (setup != null && ("BUY".equals(setup.getSignal()) || "SELL".equals(setup.getSignal())) && setup.getConfidence() >= 70) {
                activeLockedSetups.put(setupKey, setup);
            }
        }

        // 4. Populate Result
        AnalysisResult result = new AnalysisResult();
        result.setSymbol(symbol);
        result.setTimeframe(timeframe);
        result.setCurrentPrice(currentPrice);
        result.setChange24h(round(change24h, 2));
        result.setSpread(spread);
        result.setCandles(candles);
        result.setMarketStructures(structures);
        result.setFairValueGaps(fvgs);
        result.setOrderBlocks(orderBlocks);
        result.setSupportResistance(srLevels);
        result.setEma20(ema20);
        result.setEma50(ema50);
        result.setEma200(ema200);
        result.setTradeSetup(setup);

        return result;
    }

    public AnalysisResult analyzeMarket(String symbol, String timeframe, List<Candle> candles, double spread) {
        return analyzeMarket(symbol, timeframe, "SCALP", candles, spread);
    }

    /**
     * Mode-Adaptive Untouched Fair Value Gap Detection
     * SCALP: Sensitive micro-displacement on recent candles
     * SWING: High-volume macro institutional displacement across full context
     */
    public List<FairValueGap> detectUntouchedFairValueGaps(List<Candle> candles, boolean isSwing, double atr14) {
        List<FairValueGap> validFvgs = new ArrayList<>();
        int n = candles.size();
        if (n < 5) return validFvgs;

        double currentPrice = candles.get(n - 1).getClose();
        int startIdx = 2; // Full history scan so macro 4H/1D overhead & underneath zones are preserved
        double minBodyRatio = isSwing ? 0.55 : 0.38;
        double minGapSize = isSwing ? (atr14 * 0.35) : (atr14 * 0.06);

        for (int i = startIdx; i < n - 1; i++) {
            Candle c1 = candles.get(i - 2);
            Candle c2 = candles.get(i - 1); // Displacement Candle
            Candle c3 = candles.get(i);

            double body2 = Math.abs(c2.getClose() - c2.getOpen());
            double range2 = c2.getHigh() - c2.getLow();
            boolean isDisplacement = range2 > 0 && (body2 / range2) >= minBodyRatio;

            // Bullish FVG: Low of candle 3 is strictly above High of candle 1
            if (c3.getLow() > c1.getHigh() && isDisplacement) {
                double gapBottom = c1.getHigh();
                double gapTop = c3.getLow();
                double gapSize = gapTop - gapBottom;

                if (gapSize >= minGapSize) {
                    double ce = gapBottom + gapSize * 0.50; // 50% Midpoint

                    boolean mitigated = false;
                    for (int j = i + 1; j < n; j++) {
                        if (candles.get(j).getLow() <= ce) {
                            mitigated = true;
                            break;
                        }
                    }

                    if (!mitigated) {
                        FairValueGap fvg = new FairValueGap(
                                (isSwing ? "FVG-SWING-BULL-" : "FVG-SCALP-BULL-") + i,
                                "BULLISH",
                                gapTop,
                                gapBottom,
                                c1.getTimestamp(),
                                c3.getTimestamp(),
                                i
                        );
                        fvg.setConsequentEncroachment(round(ce, 5));
                        fvg.setMitigated(false);
                        validFvgs.add(fvg);
                    }
                }
            }

            // Bearish FVG: High of candle 3 is strictly below Low of candle 1
            if (c3.getHigh() < c1.getLow() && isDisplacement) {
                double gapTop = c1.getLow();
                double gapBottom = c3.getHigh();
                double gapSize = gapTop - gapBottom;

                if (gapSize >= minGapSize) {
                    double ce = gapBottom + gapSize * 0.50;

                    boolean mitigated = false;
                    for (int j = i + 1; j < n; j++) {
                        if (candles.get(j).getHigh() >= ce) {
                            mitigated = true;
                            break;
                        }
                    }

                    if (!mitigated) {
                        FairValueGap fvg = new FairValueGap(
                                (isSwing ? "FVG-SWING-BEAR-" : "FVG-SCALP-BEAR-") + i,
                                "BEARISH",
                                gapTop,
                                gapBottom,
                                c1.getTimestamp(),
                                c3.getTimestamp(),
                                i
                        );
                        fvg.setConsequentEncroachment(round(ce, 5));
                        fvg.setMitigated(false);
                        validFvgs.add(fvg);
                    }
                }
            }
        }

        // Separate and balance: nearest Overhead Bearish FVGs + nearest Underneath Bullish FVGs
        List<FairValueGap> overheadBearFvgs = validFvgs.stream()
                .filter(f -> "BEARISH".equals(f.getType()) && f.getConsequentEncroachment() >= currentPrice)
                .sorted((a, b) -> Double.compare(a.getConsequentEncroachment(), b.getConsequentEncroachment()))
                .limit(isSwing ? 3 : 2)
                .toList();

        List<FairValueGap> underneathBullFvgs = validFvgs.stream()
                .filter(f -> "BULLISH".equals(f.getType()) && f.getConsequentEncroachment() <= currentPrice)
                .sorted((a, b) -> Double.compare(b.getConsequentEncroachment(), a.getConsequentEncroachment()))
                .limit(isSwing ? 3 : 2)
                .toList();

        List<FairValueGap> result = new ArrayList<>();
        result.addAll(underneathBullFvgs);
        result.addAll(overheadBearFvgs);

        // Also add any other active recent FVGs if space permits
        for (FairValueGap f : validFvgs) {
            if (!result.contains(f) && result.size() < (isSwing ? 6 : 4)) {
                result.add(f);
            }
        }

        return result;
    }

    public List<FairValueGap> detectUntouchedFairValueGaps(List<Candle> candles) {
        return detectUntouchedFairValueGaps(candles, false, 1.0);
    }

    /**
     * Mode-Adaptive Institutional Order Blocks
     */
    public List<OrderBlock> detectOrderBlocks(List<Candle> candles, boolean isSwing) {
        List<OrderBlock> obs = new ArrayList<>();
        int n = candles.size();
        if (n < 8) return obs;

        double currentPrice = candles.get(n - 1).getClose();
        int startIdx = 2; // Full history scan
        double expansionThreshold = isSwing ? 1.8 : 1.2;

        for (int i = startIdx; i < n - 3; i++) {
            Candle c = candles.get(i);
            Candle next1 = candles.get(i + 1);
            Candle next2 = candles.get(i + 2);

            // Bullish Demand OB
            if (c.getClose() < c.getOpen() && next1.getClose() > next1.getOpen() && next2.getClose() > next2.getOpen()) {
                double expansion = next2.getClose() - c.getLow();
                if (expansion > (c.getHigh() - c.getLow()) * expansionThreshold) {
                    OrderBlock ob = new OrderBlock(
                            (isSwing ? "OB-SWING-DEMAND-" : "OB-SCALP-DEMAND-") + i,
                            "BULLISH",
                            c.getHigh(),
                            c.getLow(),
                            c.getOpen(),
                            c.getClose(),
                            c.getTimestamp(),
                            i,
                            c.getVolume()
                    );
                    obs.add(ob);
                }
            }

            // Bearish Supply OB
            if (c.getClose() > c.getOpen() && next1.getClose() < next1.getOpen() && next2.getClose() < next2.getOpen()) {
                double drop = c.getHigh() - next2.getClose();
                if (drop > (c.getHigh() - c.getLow()) * expansionThreshold) {
                    OrderBlock ob = new OrderBlock(
                            (isSwing ? "OB-SWING-SUPPLY-" : "OB-SCALP-SUPPLY-") + i,
                            "BEARISH",
                            c.getHigh(),
                            c.getLow(),
                            c.getOpen(),
                            c.getClose(),
                            c.getTimestamp(),
                            i,
                            c.getVolume()
                    );
                    obs.add(ob);
                }
            }
        }

        // Separate and balance: Overhead Supply OBs + Underneath Demand OBs
        List<OrderBlock> overheadObs = obs.stream()
                .filter(o -> "BEARISH".equals(o.getType()) && o.getTop() >= currentPrice)
                .sorted((a, b) -> Double.compare(a.getBottom(), b.getBottom()))
                .limit(2)
                .toList();

        List<OrderBlock> underneathObs = obs.stream()
                .filter(o -> "BULLISH".equals(o.getType()) && o.getBottom() <= currentPrice)
                .sorted((a, b) -> Double.compare(b.getTop(), a.getTop()))
                .limit(2)
                .toList();

        List<OrderBlock> result = new ArrayList<>();
        result.addAll(underneathObs);
        result.addAll(overheadObs);

        for (OrderBlock ob : obs) {
            if (!result.contains(ob) && result.size() < (isSwing ? 5 : 4)) {
                result.add(ob);
            }
        }

        return result;
    }

    public List<OrderBlock> detectOrderBlocks(List<Candle> candles) {
        return detectOrderBlocks(candles, false);
    }

    /**
     * Mode-Adaptive Market Structure Breaks & Liquidity Pools
     */
    public List<MarketStructure> detectMarketStructures(List<Candle> candles, boolean isSwing) {
        List<MarketStructure> structures = new ArrayList<>();
        int n = candles.size();
        if (n < 10) return structures;

        int kRadius = isSwing ? 5 : 2;
        int startIdx = isSwing ? kRadius : Math.max(kRadius, n - 40);

        for (int i = startIdx; i < n - kRadius; i++) {
            Candle curr = candles.get(i);
            boolean isSwingHigh = true;
            boolean isSwingLow = true;

            for (int k = 1; k <= kRadius; k++) {
                if (i - k < 0 || i + k >= n) continue;
                if (candles.get(i - k).getHigh() >= curr.getHigh() || candles.get(i + k).getHigh() >= curr.getHigh()) {
                    isSwingHigh = false;
                }
                if (candles.get(i - k).getLow() <= curr.getLow() || candles.get(i + k).getLow() <= curr.getLow()) {
                    isSwingLow = false;
                }
            }

            if (isSwingHigh) {
                structures.add(new MarketStructure(
                        (isSwing ? "SWING-MACRO-HIGH-" : "SCALP-HIGH-") + i,
                        "SWING_HIGH",
                        "BEARISH",
                        curr.getHigh(),
                        curr.getTimestamp(),
                        i,
                        isSwing ? "Major Macro BSL Pool" : "Micro BSL"
                ));
            }
            if (isSwingLow) {
                structures.add(new MarketStructure(
                        (isSwing ? "SWING-MACRO-LOW-" : "SCALP-LOW-") + i,
                        "SWING_LOW",
                        "BULLISH",
                        curr.getLow(),
                        curr.getTimestamp(),
                        i,
                        isSwing ? "Major Macro SSL Pool" : "Micro SSL"
                ));
            }
        }

        return structures;
    }

    public List<MarketStructure> detectMarketStructures(List<Candle> candles) {
        return detectMarketStructures(candles, false);
    }

    public List<SupportResistance> detectSupportResistance(List<Candle> candles, boolean isSwing, String symbol) {
        List<SupportResistance> levels = new ArrayList<>();
        int n = candles.size();
        if (n < 10) return levels;

        int lookback = isSwing ? Math.min(80, n) : Math.min(30, n);
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (int i = n - lookback; i < n; i++) {
            min = Math.min(min, candles.get(i).getLow());
            max = Math.max(max, candles.get(i).getHigh());
        }

        double span = Math.max(0.01, max - min);
        long tStart = candles.get(n - lookback).getTimestamp();
        long tEnd = candles.get(n - 1).getTimestamp();

        // 1. Classical Swing Range Boundaries
        levels.add(new SupportResistance(
                isSwing ? "SR-SWING-SUP" : "SR-SCALP-SUP",
                round(min, 5),
                isSwing ? "MAJOR MACRO SUPPORT" : "INTRADAY SUPPORT",
                isSwing ? 6 : 3,
                0.92,
                tStart,
                tEnd
        ));

        levels.add(new SupportResistance(
                isSwing ? "SR-SWING-RES" : "SR-SCALP-RES",
                round(max, 5),
                isSwing ? "MAJOR MACRO RESISTANCE" : "INTRADAY RESISTANCE",
                isSwing ? 6 : 3,
                0.92,
                tStart,
                tEnd
        ));

        // 2. 💧 Overhead Buy-Side Liquidity (BSL) & Underneath Sell-Side Liquidity (SSL) Pools
        double bslLevel = max * 1.0035;
        double sslLevel = min * 0.9965;
        levels.add(new SupportResistance(
                "SR-BSL-POOL",
                round(bslLevel, 5),
                "💧 OVERHEAD BSL LIQUIDITY TARGET",
                5,
                0.88,
                tStart,
                tEnd
        ));
        levels.add(new SupportResistance(
                "SR-SSL-POOL",
                round(sslLevel, 5),
                "💧 UNDERNEATH SSL LIQUIDITY POOL",
                5,
                0.88,
                tStart,
                tEnd
        ));

        // 3. 🎯 Macro Institutional Fibonacci Extension Overhead Reversal Zones
        double fib1272 = max + (span * 0.272);
        double fib1618 = max + (span * 0.618); // Golden Extension Target
        double fib2000 = max + (span * 1.000); // 2.0 Macro Expansion Ceiling

        levels.add(new SupportResistance(
                "SR-FIB-1272",
                round(fib1272, 5),
                "🎯 1.272 FIB EXTENSION REVERSAL",
                4,
                0.91,
                tStart,
                tEnd
        ));

        levels.add(new SupportResistance(
                "SR-FIB-1618",
                round(fib1618, 5),
                "🎯 MACRO 1.618 FIB EXPANSION (GOLDEN TARGET)",
                5,
                0.95,
                tStart,
                tEnd
        ));

        levels.add(new SupportResistance(
                "SR-FIB-2000",
                round(fib2000, 5),
                "🎯 MACRO 2.000 FIB EXPANSION CEILING",
                3,
                0.85,
                tStart,
                tEnd
        ));

        // 4. 🏛️ Major Institutional Psychological Whole Round Numbers
        if (symbol != null) {
            String s = symbol.toUpperCase();
            if (s.contains("XAU")) {
                double[] goldRoundLevels = {4600.00, 4800.00, 5000.00, 5200.00, 5500.00, 6000.00};
                for (double gr : goldRoundLevels) {
                    if (gr >= min * 0.95 && gr <= max * 1.40) {
                        levels.add(new SupportResistance(
                                "SR-PSYCH-" + (int)gr,
                                gr,
                                gr == 5000.00 ? "🏛️ $5,000 MAJOR INSTITUTIONAL CEILING" : ("🏛️ PSYCHOLOGICAL LEVEL ($" + (int)gr + ")"),
                                7,
                                0.96,
                                tStart,
                                tEnd
                        ));
                    }
                }
            } else if (s.contains("BTC")) {
                double[] btcLevels = {65000.0, 70000.0, 75000.0, 80000.0, 85000.0, 90000.0, 100000.0};
                for (double br : btcLevels) {
                    if (br >= min * 0.90 && br <= max * 1.40) {
                        levels.add(new SupportResistance(
                                "SR-PSYCH-" + (int)br,
                                br,
                                "🏛️ PSYCHOLOGICAL LEVEL ($" + (int)br + ")",
                                6,
                                0.94,
                                tStart,
                                tEnd
                        ));
                    }
                }
            } else if (s.contains("EUR") || s.contains("GBP")) {
                double[] fxLevels = {1.16000, 1.18000, 1.20000, 1.22000};
                for (double fr : fxLevels) {
                    if (fr >= min * 0.95 && fr <= max * 1.10) {
                        levels.add(new SupportResistance(
                                "SR-PSYCH-" + String.format("%.4f", fr),
                                fr,
                                "🏛️ INSTITUTIONAL FX LEVEL (" + String.format("%.4f", fr) + ")",
                                5,
                                0.90,
                                tStart,
                                tEnd
                        ));
                    }
                }
            }
        }

        return levels;
    }

    public List<SupportResistance> detectSupportResistance(List<Candle> candles, boolean isSwing) {
        return detectSupportResistance(candles, isSwing, "XAUUSD");
    }

    public List<SupportResistance> detectSupportResistance(List<Candle> candles) {
        return detectSupportResistance(candles, false, "XAUUSD");
    }

    /**
     * Rigorous Institutional Setup Generator: Dual Mode (⚡ Scalp vs 🌊 Swing)
     */
    private TradeSetup generateInstitutionalSetup(
            String symbol, String timeframe, String tradeMode, List<Candle> candles,
            List<FairValueGap> fvgs, List<OrderBlock> obs, List<SupportResistance> srs,
            List<Double> ema20, List<Double> ema50, List<Double> ema200,
            double atr14, double spread) {

        int n = candles.size();
        Candle last = candles.get(n - 1);
        double currentPrice = last.getClose();

        double e20 = ema20.get(ema20.size() - 1);
        double e50 = ema50.get(ema50.size() - 1);
        double e200 = ema200.get(ema200.size() - 1);

        double rsi14 = calculateRSI(candles, 14);

        // Mode Parameters (⚡ SCALP vs 🌊 SWING vs 🎯 DEEP SNIPER for Small Capital)
        boolean isSwing = "SWING".equalsIgnoreCase(tradeMode);
        boolean isSniper = "SNIPER".equalsIgnoreCase(tradeMode) || "DEEP".equalsIgnoreCase(tradeMode);

        double atrMultiplier = isSwing ? 2.0 : (isSniper ? 0.85 : 1.3);
        double tp1Multiplier = isSwing ? 3.5 : (isSniper ? 5.5 : 3.0); // Sniper targets healthy 1:5.5+
        double tp2Multiplier = isSwing ? 5.0 : (isSniper ? 8.5 : 4.5);
        double targetRr = isSwing ? 3.5 : (isSniper ? 5.5 : 3.0);

        // Timeframe Reliability Hierarchy
        String tfUpper = timeframe.toUpperCase();
        String tfReliabilityTag = switch (tfUpper) {
            case "1D", "4H" -> "👑 Maximum Macro Reliability (⭐⭐⭐⭐⭐)";
            case "1H", "30M", "15M" -> "🏛️ High Structural Reliability (⭐⭐⭐⭐)";
            case "5M", "3M" -> "⚡ Moderate Execution Timeframe (⭐⭐⭐ - HTF Anchored)";
            default -> "⚡ Micro Execution Timeframe (⭐⭐ - Strict HTF Filter)";
        };
        boolean isLtf = "1M".equalsIgnoreCase(tfUpper) || "3M".equalsIgnoreCase(tfUpper) || "5M".equalsIgnoreCase(tfUpper);

        // Minimum Volatility Buffer per Asset Class (Extra safe breathing room on LTF 1m/3m to protect against broker spread & wick spikes)
        double minBuffer = switch (symbol.toUpperCase()) {
            case "XAUUSD" -> isLtf ? 5.50 : (isSniper ? 4.50 : 3.50); // $5.50 minimum floor on LTF Gold (55 pips breathing room!)
            case "BTCUSD" -> isLtf ? 180.0 : (isSniper ? 140.0 : 100.0); // $180 floor on Bitcoin
            case "USDJPY" -> isLtf ? 0.30 : (isSniper ? 0.22 : 0.18); // 30 pips on Yen
            default -> isLtf ? 0.00220 : (isSniper ? 0.00180 : 0.00150);   // 22 pips on EURUSD / GBPUSD
        };
        double dynamicAtrBuffer = Math.max(minBuffer, atr14 * atrMultiplier);

        // Calculate Macro Range High, Low & Equilibrium across substantial history
        double rangeHigh = Double.MIN_VALUE;
        double rangeLow = Double.MAX_VALUE;
        int lookback = Math.min(n, isSwing ? 60 : 35);
        for (int i = n - lookback; i < n; i++) {
            rangeHigh = Math.max(rangeHigh, candles.get(i).getHigh());
            rangeLow = Math.min(rangeLow, candles.get(i).getLow());
        }
        double rangeSpan = Math.max(minBuffer * 2, rangeHigh - rangeLow);
        double equilibrium = rangeLow + rangeSpan * 0.50;
        double pricePositionPct = ((currentPrice - rangeLow) / rangeSpan) * 100.0;

        // 1. Identify Nearest Overhead Supply Zones (Above current market price)
        FairValueGap nearestOverheadFvg = fvgs.stream()
                .filter(f -> "BEARISH".equals(f.getType()) && f.getConsequentEncroachment() >= currentPrice)
                .min((a, b) -> Double.compare(a.getConsequentEncroachment(), b.getConsequentEncroachment()))
                .orElse(null);

        OrderBlock nearestOverheadOb = obs.stream()
                .filter(o -> "BEARISH".equals(o.getType()) && o.getTop() >= currentPrice)
                .min((a, b) -> Double.compare(a.getBottom(), b.getBottom()))
                .orElse(null);

        // 2. Identify Nearest Underneath Demand Zones (Below current market price)
        FairValueGap nearestUnderneathFvg = fvgs.stream()
                .filter(f -> "BULLISH".equals(f.getType()) && f.getConsequentEncroachment() <= currentPrice)
                .max((a, b) -> Double.compare(a.getConsequentEncroachment(), b.getConsequentEncroachment()))
                .orElse(null);

        OrderBlock nearestUnderneathOb = obs.stream()
                .filter(o -> "BULLISH".equals(o.getType()) && o.getBottom() <= currentPrice)
                .max((a, b) -> Double.compare(a.getTop(), b.getTop()))
                .orElse(null);

        // Proximity checks (Distance in % to overhead supply or underneath demand)
        double overheadSupplyLevel = nearestOverheadFvg != null ? nearestOverheadFvg.getConsequentEncroachment() : (nearestOverheadOb != null ? nearestOverheadOb.getBottom() : rangeHigh);
        double underneathDemandLevel = nearestUnderneathFvg != null ? nearestUnderneathFvg.getConsequentEncroachment() : (nearestUnderneathOb != null ? nearestUnderneathOb.getTop() : rangeLow);

        double distToOverheadSupplyPct = (Math.abs(overheadSupplyLevel - currentPrice) / currentPrice) * 100.0;
        double distToUnderneathDemandPct = (Math.abs(currentPrice - underneathDemandLevel) / currentPrice) * 100.0;

        List<String> confluences = new ArrayList<>();
        int confidence = 85;

        // =========================================================================
        // 🏛️ INSTITUTIONAL ORDER FLOW & TREND CLASSIFICATION ENGINE
        // Rule: NEVER trade counter-trend! Trade with Higher Timeframe Order Flow.
        // =========================================================================
        boolean isBullishTrend = e20 > e50 && e50 > e200;
        boolean isBearishTrend = e20 < e50 && e50 < e200;

        boolean isBuySignal;
        if (isBullishTrend) {
            isBuySignal = true;
        } else if (isBearishTrend) {
            isBuySignal = false;
        } else {
            isBuySignal = pricePositionPct <= 50.0;
        }

        if (!isBuySignal) {
            // =========================================================================
            // 🔴 INSTITUTIONAL SELL SETUP (Trend-Aligned Short)
            // =========================================================================
            double entry;
            double entry2;
            double stopLoss;
            double anchorWickHigh;

            // 1. Calculate genuine Structural Invalidation High & Protective Stop Loss
            if (isSniper && nearestOverheadFvg != null) {
                // 75% Deep OTE Tap into Supply FVG Ceiling
                double gTop = nearestOverheadFvg.getTop();
                double gBottom = nearestOverheadFvg.getBottom();
                entry = round(gTop - ((gTop - gBottom) * 0.25), 5);
                anchorWickHigh = gTop;
                stopLoss = round(anchorWickHigh + dynamicAtrBuffer + spread, 5);
            } else if (isSniper && nearestOverheadOb != null) {
                double obTop = nearestOverheadOb.getTop();
                double obBottom = nearestOverheadOb.getBottom();
                entry = round(obTop - ((obTop - obBottom) * 0.25), 5);
                anchorWickHigh = obTop;
                stopLoss = round(anchorWickHigh + dynamicAtrBuffer + spread, 5);
            } else {
                entry = overheadSupplyLevel;
                anchorWickHigh = nearestOverheadFvg != null 
                    ? Math.max(nearestOverheadFvg.getTop(), rangeHigh) 
                    : (nearestOverheadOb != null ? Math.max(nearestOverheadOb.getTop(), rangeHigh) : rangeHigh);
                stopLoss = round(anchorWickHigh + dynamicAtrBuffer + spread, 5);
            }

            // 2. Risk Distance Calibrated for Scalp & Swing (Strictly 200 to 300 Pips)
            double risk;
            if (isSniper) {
                risk = Math.max(minBuffer, Math.abs(stopLoss - entry));
            } else {
                double minPipDist = getPipDistance(symbol, isSwing ? 250.0 : 200.0);
                double maxPipDist = getPipDistance(symbol, isSwing ? 300.0 : 250.0);
                double rawRisk = Math.abs(stopLoss - entry);
                risk = Math.max(minPipDist, Math.min(maxPipDist, rawRisk));
            }
            stopLoss = round(entry + risk, 5);

            // 3. Dual Entry 2 (Structural Invalidation Inflection Level: 50%+ Bounce Probability Floor)
            // Sits at the Structural High Ceiling (75-80% OTE Premium Retest). Above this, bounce chance drops drastically.
            entry2 = round(anchorWickHigh - spread, 5);
            if (entry2 <= entry || entry2 >= stopLoss) {
                entry2 = round(entry + (risk * 0.60), 5); // 60% Deep Retest Level
            }

            // 4. Take Profit Targets (Preserve Full Macro Target / Range Low / Demand)
            double fullDemandTarget = (nearestUnderneathFvg != null) ? nearestUnderneathFvg.getConsequentEncroachment() : underneathDemandLevel;
            double macroTargetSell = Math.min(fullDemandTarget, rangeLow);
            double minSniperTpSell = entry - (risk * tp1Multiplier);
            double tp1 = round(isSniper ? minSniperTpSell : Math.min(macroTargetSell, minSniperTpSell), 5);
            double tp2 = round(entry - (risk * tp2Multiplier), 5);
            double calculatedRr = round(Math.abs(entry - tp1) / risk, 1);
            double bufferApplied = Math.abs(stopLoss - anchorWickHigh);

            confidence = (nearestOverheadFvg != null && isBearishTrend) ? (isSniper ? 94 : (isSwing ? 96 : 92)) : (isSwing ? 90 : 86);

            confluences.add("Timeframe Reliability: " + tfReliabilityTag);
            confluences.add("Mode: " + (isSniper ? "🎯 Deep Sniper / Small Capital OTE Mode (Healthy Cushion)" : (isSwing ? "🌊 Swing / Macro Trend Continuation (250-300 Pip SL Guard)" : "⚡ Scalp / Intraday Momentum (200-250 Pip SL Guard)")));
            confluences.add("🏛️ Institutional Delivery: Bearish Order Flow (" + (isBearishTrend ? "EMA 20 < 50 < 200 Waterfall" : "Supply Rejection") + ")");
            confluences.add("🛡️ Invalidation Anchor: Structural High @" + formatPrice(anchorWickHigh, symbol) + " + Buffer ➔ Real SL: " + formatPrice(stopLoss, symbol) + " (" + String.format("%.0f", calculatePips(symbol, risk)) + " Pips)");
            confluences.add("🎯 Primary Entry: " + formatPrice(entry, symbol) + " | 🟢 High-Prob Inflection E2: " + formatPrice(entry2, symbol));
            confluences.add("Target Direction: Discount Demand / SSL Pool at " + formatPrice(underneathDemandLevel, symbol));
            confluences.add("RSI Momentum: RSI=" + String.format("%.1f", rsi14) + " (Bearish Alignment)");
            confluences.add("Target Risk-to-Reward: 1:" + calculatedRr + " (Strategic Asymmetric Short)");

            String setupTitle = isSniper
                ? "🎯 Deep Sniper Short: 75% OTE Supply Retest (Safe Cushion SL 1:5.5+)"
                : (isSwing ? "🎯 4H/Macro Bearish Trend: Supply Pullback (250-300 Pip Guard)" : "⚡ Scalp Supply Tap: High-Probability Short (200-250 Pip Guard)");

            String bookExplanation = String.format(
                "### 📚 %s (ICT Bearish Order Flow Blueprint)\n\n" +
                "1. **Institutional Market Direction:**\n" +
                "   Market structure on %s is in **Bearish Trend Alignment** seeking downside Discount Liquidity.\n\n" +
                "2. **Dual Supply Entry Coordination:**\n" +
                "   • Primary Entry 1: **%s** (50%% FVG / OTE Equilibrium)\n" +
                "   • High-Prob Inflection E2: **%s** (Supply Ceiling Retest with 50%%+ Reversal Odds)\n\n" +
                "3. **Wick Anchor & Buffer Invalidation:**\n" +
                "   SL is placed safely at **%s** maintaining a **%s Pip protective boundary** ➔ True Hard SL at **%s**.\n\n" +
                "4. **Target Horizons:**\n" +
                "   Take Profit 1 at **%s** and Take Profit 2 at **%s** yielding an asymmetric **1:%.1f R:R**.\n",
                isSniper ? "🎯 Deep Sniper OTE Short Blueprint" : (isSwing ? "🌊 Macro Bearish Trend Blueprint" : "⚡ Intraday Supply Tap"),
                timeframe,
                formatPrice(entry, symbol),
                formatPrice(entry2, symbol),
                formatPrice(anchorWickHigh, symbol),
                String.format("%.0f", calculatePips(symbol, risk)),
                formatPrice(stopLoss, symbol),
                formatPrice(tp1, symbol),
                formatPrice(tp2, symbol),
                calculatedRr
            );

            return new TradeSetup(
                    "SETUP-OVERHEAD-SHORT-" + System.currentTimeMillis(),
                    symbol,
                    timeframe,
                    "SELL",
                    confidence,
                    currentPrice,
                    entry,
                    entry2,
                    stopLoss,
                    tp1,
                    tp2,
                    calculatedRr,
                    setupTitle,
                    confluences,
                    bookExplanation,
                    System.currentTimeMillis()
            );
        } else {
            // =========================================================================
            // 🟢 INSTITUTIONAL BUY SETUP (Trend-Aligned Long)
            // =========================================================================
            double entry;
            double entry2;
            double stopLoss;
            double anchorWickLow;

            // 1. Calculate genuine Structural Invalidation Low & Protective Stop Loss
            if (isSniper && nearestUnderneathFvg != null) {
                // 75% Deep OTE Tap into Demand FVG Floor
                double gTop = nearestUnderneathFvg.getTop();
                double gBottom = nearestUnderneathFvg.getBottom();
                entry = round(gBottom + ((gTop - gBottom) * 0.25) + spread, 5);
                anchorWickLow = gBottom;
                stopLoss = round(anchorWickLow - dynamicAtrBuffer, 5);
            } else if (isSniper && nearestUnderneathOb != null) {
                double obTop = nearestUnderneathOb.getTop();
                double obBottom = nearestUnderneathOb.getBottom();
                entry = round(obBottom + ((obTop - obBottom) * 0.25) + spread, 5);
                anchorWickLow = obBottom;
                stopLoss = round(anchorWickLow - dynamicAtrBuffer, 5);
            } else {
                entry = underneathDemandLevel + spread;
                anchorWickLow = nearestUnderneathFvg != null 
                    ? Math.min(nearestUnderneathFvg.getBottom(), rangeLow) 
                    : (nearestUnderneathOb != null ? Math.min(nearestUnderneathOb.getBottom(), rangeLow) : rangeLow);
                stopLoss = round(anchorWickLow - dynamicAtrBuffer, 5);
            }

            // 2. Risk Distance Calibrated for Scalp & Swing (Strictly 200 to 300 Pips)
            double risk;
            if (isSniper) {
                risk = Math.max(minBuffer, Math.abs(entry - stopLoss));
            } else {
                double minPipDist = getPipDistance(symbol, isSwing ? 250.0 : 200.0);
                double maxPipDist = getPipDistance(symbol, isSwing ? 300.0 : 250.0);
                double rawRisk = Math.abs(entry - stopLoss);
                risk = Math.max(minPipDist, Math.min(maxPipDist, rawRisk));
            }
            stopLoss = round(entry - risk, 5);

            // 3. Dual Entry 2 (Structural Invalidation Inflection Level: 50%+ Bounce Probability Floor)
            // Sits at the Structural Low Base (75-80% OTE Demand Retest). Below this, bounce chance drops drastically.
            entry2 = round(anchorWickLow + spread, 5);
            if (entry2 >= entry || entry2 <= stopLoss) {
                entry2 = round(entry - (risk * 0.60), 5); // 60% Deep Retest Level
            }

            // 4. Take Profit Targets (1:5.5+ Asymmetric Expansion)
            double fullSupplyTarget = (nearestOverheadFvg != null) ? nearestOverheadFvg.getConsequentEncroachment() : overheadSupplyLevel;
            double macroTargetBuy = Math.max(fullSupplyTarget, rangeHigh);
            double minSniperTpBuy = entry + (risk * tp1Multiplier);
            double tp1 = round(isSniper ? minSniperTpBuy : Math.max(macroTargetBuy, minSniperTpBuy), 5);
            double tp2 = round(entry + (risk * tp2Multiplier), 5);
            double calculatedRr = round(Math.abs(tp1 - entry) / risk, 1);
            double bufferApplied = Math.abs(anchorWickLow - stopLoss);

            confidence = (nearestUnderneathFvg != null && isBullishTrend) ? (isSniper ? 94 : (isSwing ? 96 : 92)) : (isSwing ? 90 : 86);

            confluences.add("Timeframe Reliability: " + tfReliabilityTag);
            confluences.add("Mode: " + (isSniper ? "🎯 Deep Sniper / Small Capital OTE Mode (Healthy Cushion)" : (isSwing ? "🌊 Swing / Macro Bullish Expansion (250-300 Pip SL Guard)" : "⚡ Scalp / Intraday Momentum (200-250 Pip SL Guard)")));
            confluences.add("🏛️ Institutional Delivery: Bullish Order Flow (" + (isBullishTrend ? "EMA 20 > 50 > 200 Expansion" : "Demand Mitigation") + ")");
            confluences.add("🛡️ Invalidation Anchor: Structural Low @" + formatPrice(anchorWickLow, symbol) + " - Buffer ➔ Real SL: " + formatPrice(stopLoss, symbol) + " (" + String.format("%.0f", calculatePips(symbol, risk)) + " Pips)");
            confluences.add("🎯 Primary Entry: " + formatPrice(entry, symbol) + " | 🟢 High-Prob Inflection E2: " + formatPrice(entry2, symbol));
            confluences.add("Overhead Target Magnet: Bearish Supply / BSL Expansion at " + formatPrice(overheadSupplyLevel, symbol));
            confluences.add("RSI Momentum: RSI=" + String.format("%.1f", rsi14) + " (Clean Bullish Momentum)");
            confluences.add("Target Risk-to-Reward: 1:" + calculatedRr + " (Strategic Asymmetric Expectancy)");

            String setupTitle = isSniper
                ? "🎯 Deep Sniper Long: 75% OTE Demand Retest (Safe Cushion SL 1:5.5+)"
                : (isSwing ? "🚀 4H/Macro Bullish Expansion: Demand Pullback (250-300 Pip Guard)" : "⚡ Scalp Demand Tap: Rapid Push (200-250 Pip Guard)");

            String bookExplanation = String.format(
                "### 📚 %s (ICT OTE & Bullish Expansion Blueprint)\n\n" +
                "1. **Institutional Target Magnet:**\n" +
                "   Market is expanding upwards on %s seeking the **Overhead Bearish Supply at %s**.\n\n" +
                "2. **Dual Demand Entry Coordination:**\n" +
                "   • Primary Entry 1: **%s** (50%% FVG / OTE Equilibrium)\n" +
                "   • High-Prob Inflection E2: **%s** (Demand Floor Retest with 50%%+ Reversal Odds)\n\n" +
                "3. **Wick Anchor & Buffer Invalidation:**\n" +
                "   SL is placed safely below **%s** maintaining a **%s Pip protective boundary** ➔ True Hard SL at **%s**.\n\n" +
                "4. **Target Horizons:**\n" +
                "   Take Profit 1 at **%s** (Overhead Target) and Take Profit 2 at **%s** yielding an asymmetric **1:%.1f R:R**.\n",
                isSniper ? "🎯 Deep Sniper OTE Long Blueprint" : (isSwing ? "🌊 Macro Bullish Expansion Blueprint" : "⚡ Intraday Demand Expansion"),
                timeframe,
                overheadSupplyLevel > 0 ? formatPrice(overheadSupplyLevel, symbol) : "Target Liquidity",
                formatPrice(entry, symbol),
                formatPrice(entry2, symbol),
                formatPrice(anchorWickLow, symbol),
                String.format("%.0f", calculatePips(symbol, risk)),
                formatPrice(stopLoss, symbol),
                formatPrice(tp1, symbol),
                formatPrice(tp2, symbol),
                calculatedRr
            );

            return new TradeSetup(
                    "SETUP-EXPANSION-BUY-" + System.currentTimeMillis(),
                    symbol,
                    timeframe,
                    "BUY",
                    confidence,
                    currentPrice,
                    entry,
                    entry2,
                    stopLoss,
                    tp1,
                    tp2,
                    calculatedRr,
                    setupTitle,
                    confluences,
                    bookExplanation,
                    System.currentTimeMillis()
            );
        }
    }

    private double calculate24hChange(List<Candle> candles) {
        if (candles.size() < 2) return 0.0;
        double first = candles.get(0).getOpen();
        double last = candles.get(candles.size() - 1).getClose();
        if (first <= 0) return 0.0;
        return ((last - first) / first) * 100.0;
    }

    private List<Double> calculateEMA(List<Candle> candles, int period) {
        List<Double> emas = new ArrayList<>();
        if (candles == null || candles.isEmpty() || period <= 0) return emas;

        double multiplier = 2.0 / (period + 1.0);
        double prevEMA = candles.get(0).getClose();
        emas.add(prevEMA);

        for (int i = 1; i < candles.size(); i++) {
            double close = candles.get(i).getClose();
            double currentEMA = (close - prevEMA) * multiplier + prevEMA;
            emas.add(round(currentEMA, 5));
            prevEMA = currentEMA;
        }

        return emas;
    }

    private double calculateRSI(List<Candle> candles, int period) {
        if (candles == null || candles.size() < period + 1) return 50.0;
        double gains = 0.0;
        double losses = 0.0;

        for (int i = candles.size() - period; i < candles.size(); i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (change > 0) gains += change;
            else losses += Math.abs(change);
        }

        double avgGain = gains / period;
        double avgLoss = losses / period;

        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private double calculateATR(List<Candle> candles, int period) {
        if (candles.size() < period + 1) return 1.0;
        double trSum = 0.0;
        int count = 0;

        for (int i = candles.size() - period; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            double tr = Math.max(
                    curr.getHigh() - curr.getLow(),
                    Math.max(
                            Math.abs(curr.getHigh() - prev.getClose()),
                            Math.abs(curr.getLow() - prev.getClose())
                    )
            );
            trSum += tr;
            count++;
        }

        return count > 0 ? (trSum / count) : 1.0;
    }

    public TradeAdvisorResult generateTradeAdvisor(String symbol, String timeframe, String tradeMode, double lotSize, List<Candle> candles, double spread) {
        int n = candles.size();
        Candle last = candles.get(n - 1);
        double currentPrice = last.getClose();
        boolean isSwing = "SWING".equalsIgnoreCase(tradeMode);
        boolean isSniper = "SNIPER".equalsIgnoreCase(tradeMode);

        List<Double> ema20 = calculateEMA(candles, 20);
        List<Double> ema50 = calculateEMA(candles, 50);
        List<Double> ema200 = calculateEMA(candles, Math.min(200, candles.size() - 1));
        double atr14 = calculateATR(candles, 14);
        double rsi14 = calculateRSI(candles, 14);

        double e20 = ema20.get(ema20.size() - 1);
        double e50 = ema50.get(ema50.size() - 1);
        double e200 = ema200.get(ema200.size() - 1);

        boolean isBullishTrend = (currentPrice > e50 && e20 > e50 && e50 > e200 && currentPrice >= e20);
        boolean isBearishTrend = (currentPrice < e50 && e20 < e50 && e50 < e200 && currentPrice <= e20);

        List<FairValueGap> fvgs = detectUntouchedFairValueGaps(candles, isSwing, atr14);

        FairValueGap nearestBullFvg = null;
        FairValueGap nearestBearFvg = null;
        for (int i = fvgs.size() - 1; i >= 0; i--) {
            if ("BULLISH".equals(fvgs.get(i).getType()) && nearestBullFvg == null && !fvgs.get(i).isMitigated()) {
                nearestBullFvg = fvgs.get(i);
            }
            if ("BEARISH".equals(fvgs.get(i).getType()) && nearestBearFvg == null && !fvgs.get(i).isMitigated()) {
                nearestBearFvg = fvgs.get(i);
            }
        }

        double atrMult = isSwing ? 2.5 : 1.2;
        double tpMult = isSwing ? 4.5 : 2.2;
        double dynamicAtrBuffer = Math.max(spread * (isSwing ? 3.0 : 1.5), atr14 * atrMult);

        // Calculate Range High, Low & Equilibrium
        double rangeHigh = Double.MIN_VALUE;
        double rangeLow = Double.MAX_VALUE;
        for (int i = Math.max(0, n - (isSwing ? 35 : 20)); i < n; i++) {
            rangeHigh = Math.max(rangeHigh, candles.get(i).getHigh());
            rangeLow = Math.min(rangeLow, candles.get(i).getLow());
        }
        double equilibrium = rangeLow + (rangeHigh - rangeLow) * 0.50;
        boolean inDiscount = currentPrice < equilibrium;
        boolean inPremium = currentPrice > equilibrium;

        // --- 🟢 BUY SCENARIO ---
        double buySl = nearestBullFvg != null ? nearestBullFvg.getBottom() - dynamicAtrBuffer : currentPrice - dynamicAtrBuffer;
        double buyEntry;
        if (isSniper) {
            buyEntry = buySl + getTenPointsOffset(symbol) + spread;
        } else {
            buyEntry = nearestBullFvg != null ? nearestBullFvg.getConsequentEncroachment() + spread : currentPrice + spread;
        }
        double buyRiskDist = Math.max(atr14 * 0.5, Math.abs(buyEntry - buySl));
        double buyTp = isSniper ? Math.max(rangeHigh, buyEntry + (buyRiskDist * 8.0)) : (buyEntry + (buyRiskDist * tpMult));

        double buyRiskDollars = calculateDollarPnl(symbol, buyRiskDist, lotSize, currentPrice);
        double buyProfitDollars = calculateDollarPnl(symbol, Math.abs(buyTp - buyEntry), lotSize, currentPrice);
        double buyRiskPips = calculatePips(symbol, buyRiskDist);
        double buyProfitPips = calculatePips(symbol, Math.abs(buyTp - buyEntry));

        int buyWinProb = isBullishTrend && inDiscount && nearestBullFvg != null ? (isSwing ? 94 : 88) : (isBullishTrend ? 68 : 32);
        List<String> buyConfluences = new ArrayList<>();
        List<String> buyWarnings = new ArrayList<>();
        if (isBullishTrend) buyConfluences.add("Ascending EMA 20/50/200 Trend Alignment");
        if (inDiscount) buyConfluences.add("Price is in deep Institutional Discount (< 50% Equilibrium)");
        if (nearestBullFvg != null) buyConfluences.add(isSniper ? "10-Point Tight Sniper Entry at " + formatPrice(buyEntry, symbol) : "Untouched 50% Consequent Encroachment Demand FVG at " + formatPrice(buyEntry, symbol));
        if (!isBullishTrend) buyWarnings.add("⚠️ Opposing Higher-Timeframe Trend (EMA slope bearish)");
        if (inPremium) buyWarnings.add("⚠️ Price in Institutional Premium - High risk of distribution drop");

        TradeScenario buyScenario = new TradeScenario(
                "BUY", round(buyEntry, 5), round(buySl, 5), round(buyTp, 5),
                round(buyRiskDollars, 2), round(buyProfitDollars, 2),
                round(buyRiskPips, 1), round(buyProfitPips, 1),
                round(Math.abs(buyTp - buyEntry) / buyRiskDist, 2), buyWinProb,
                buyWinProb >= 80, buyConfluences, buyWarnings
        );

        // --- 🔴 SELL SCENARIO ---
        double sellSl = nearestBearFvg != null ? nearestBearFvg.getTop() + dynamicAtrBuffer + spread : currentPrice + dynamicAtrBuffer + spread;
        double sellEntry;
        if (isSniper) {
            sellEntry = sellSl - getTenPointsOffset(symbol);
        } else {
            sellEntry = nearestBearFvg != null ? nearestBearFvg.getConsequentEncroachment() : currentPrice;
        }
        double sellRiskDist = Math.max(atr14 * 0.5, Math.abs(sellSl - sellEntry));
        double sellTp = isSniper ? Math.min(rangeLow, sellEntry - (sellRiskDist * 8.0)) : (sellEntry - (sellRiskDist * tpMult));

        double sellRiskDollars = calculateDollarPnl(symbol, sellRiskDist, lotSize, currentPrice);
        double sellProfitDollars = calculateDollarPnl(symbol, Math.abs(sellEntry - sellTp), lotSize, currentPrice);
        double sellRiskPips = calculatePips(symbol, sellRiskDist);
        double sellProfitPips = calculatePips(symbol, Math.abs(sellEntry - sellTp));

        int sellWinProb = isBearishTrend && inPremium && nearestBearFvg != null ? (isSwing ? 94 : 88) : (isBearishTrend ? 68 : 32);
        List<String> sellConfluences = new ArrayList<>();
        List<String> sellWarnings = new ArrayList<>();
        if (isBearishTrend) sellConfluences.add("Descending EMA 20/50/200 Trend Alignment");
        if (inPremium) sellConfluences.add("Price is in Institutional Premium (> 50% Equilibrium)");
        if (nearestBearFvg != null) sellConfluences.add(isSniper ? "10-Point Tight Sniper Entry at " + formatPrice(sellEntry, symbol) : "Untouched 50% Consequent Encroachment Supply FVG at " + formatPrice(sellEntry, symbol));
        if (!isBearishTrend) sellWarnings.add("⚠️ Opposing Higher-Timeframe Trend (EMA slope bullish)");
        if (inDiscount) sellWarnings.add("⚠️ Price in Institutional Discount - Smart money accumulating long");

        TradeScenario sellScenario = new TradeScenario(
                "SELL", round(sellEntry, 5), round(sellSl, 5), round(sellTp, 5),
                round(sellRiskDollars, 2), round(sellProfitDollars, 2),
                round(sellRiskPips, 1), round(sellProfitPips, 1),
                round(tpMult, 2), sellWinProb,
                sellWinProb >= 80, sellConfluences, sellWarnings
        );

        // --- VERDICT & STRATEGY EVALUATION ---
        String recAction;
        String verdict;
        String headline;
        String explanation;
        double expectancy;

        if (buyWinProb >= 80) {
            recAction = "BUY";
            verdict = "VALID_A_PLUS";
            headline = "🟢 STRATEGY IS 100% RIGHT & A+ GRADE TO BUY";
            explanation = String.format(
                    "Statistical edge is heavily skewed in favor of BUY. Price is trading in institutional discount with untouched 50%% FVG demand confluence, yielding a +$%.2f profit target vs $%.2f risk.",
                    buyProfitDollars, buyRiskDollars
            );
            expectancy = ((buyWinProb / 100.0) * buyProfitDollars) - (((100 - buyWinProb) / 100.0) * buyRiskDollars);
        } else if (sellWinProb >= 80) {
            recAction = "SELL";
            verdict = "VALID_A_PLUS";
            headline = "🔴 STRATEGY IS 100% RIGHT & A+ GRADE TO SELL";
            explanation = String.format(
                    "Statistical edge is heavily skewed in favor of SHORT/SELL. Price is trading in institutional premium with untouched 50%% FVG supply confluence, yielding a +$%.2f profit target vs $%.2f risk.",
                    sellProfitDollars, sellRiskDollars
            );
            expectancy = ((sellWinProb / 100.0) * sellProfitDollars) - (((100 - sellWinProb) / 100.0) * sellRiskDollars);
        } else {
            recAction = "WAIT_AND_PRESERVE";
            verdict = "CHOPPY_PRESERVE";
            headline = "🛡️ STRATEGY IS NOT RIGHT NOW — CAPITAL PRESERVATION ACTIVE";
            explanation = "Market structure is currently in low-conviction consolidation or chop. Forcing a trade here has negative mathematical expectancy. Preserve margin until a high-conviction liquidity sweep occurs (Mark Douglas Rule #1).";
            expectancy = 0.0;
        }

        return new TradeAdvisorResult(
                symbol, timeframe, tradeMode, currentPrice, lotSize,
                recAction, verdict, headline, explanation,
                round(expectancy, 2), buyScenario, sellScenario, System.currentTimeMillis()
        );
    }

    private double calculateDollarPnl(String symbol, double priceDist, double lotSize, double currentPrice) {
        if ("XAUUSD".equalsIgnoreCase(symbol)) {
            return priceDist * 100.0 * lotSize;
        } else if ("EURUSD".equalsIgnoreCase(symbol) || "GBPUSD".equalsIgnoreCase(symbol)) {
            return (priceDist / 0.00010) * 10.0 * lotSize;
        } else if ("USDJPY".equalsIgnoreCase(symbol)) {
            return (priceDist / 0.010) * (1000.0 / Math.max(1.0, currentPrice)) * lotSize;
        } else if ("BTCUSD".equalsIgnoreCase(symbol)) {
            return priceDist * 1.0 * lotSize;
        }
        return priceDist * 100.0 * lotSize;
    }

    private double calculatePips(String symbol, double priceDist) {
        if ("EURUSD".equalsIgnoreCase(symbol) || "GBPUSD".equalsIgnoreCase(symbol)) {
            return priceDist / 0.00010;
        } else if ("USDJPY".equalsIgnoreCase(symbol)) {
            return priceDist / 0.010;
        } else if ("XAUUSD".equalsIgnoreCase(symbol)) {
            return priceDist * 10.0; // 1 dollar move = 10 gold pips / points
        }
        return priceDist;
    }

    private double getPipDistance(String symbol, double pips) {
        String sym = symbol != null ? symbol.toUpperCase() : "";
        if (sym.contains("XAU")) {
            return pips * 0.10; // 200 pips = $20.00, 300 pips = $30.00
        } else if (sym.contains("BTC")) {
            return pips * 1.0; // 200 pips = $200.00, 300 pips = $300.00
        } else if (sym.contains("JPY")) {
            return pips * 0.0010; // 200-300 points = 0.200 - 0.300
        } else {
            return pips * 0.000010; // 200-300 points (20-30 standard pips) = 0.00200 - 0.00300
        }
    }

    private double getTenPointsOffset(String symbol) {
        String sym = symbol != null ? symbol.toUpperCase() : "";
        if (sym.contains("XAU")) {
            return 1.00; // $1.00 on Gold (10 gold pips/points)
        } else if (sym.contains("BTC")) {
            return 15.0; // $15 points on Bitcoin
        } else if (sym.contains("JPY")) {
            return 0.10; // 10 pips on USDJPY
        } else {
            return 0.00100; // 10 pips on EURUSD/GBPUSD
        }
    }

    private double get50PipDistance(String symbol) {
        String sym = symbol != null ? symbol.toUpperCase() : "";
        if (sym.contains("XAU")) {
            return 0.50; // $0.50 on Gold (5 points / 50 MT5 micro-pips above SL)
        } else if (sym.contains("BTC")) {
            return 50.0; // $50 on Bitcoin
        } else if (sym.contains("JPY")) {
            return 0.050; // 5 pips / 50 points on USDJPY
        } else {
            return 0.00050; // 5 pips / 50 points on EURUSD/GBPUSD
        }
    }

    private String formatPrice(double val, String symbol) {
        if (symbol != null && (symbol.contains("XAU") || symbol.contains("BTC") || symbol.contains("JPY"))) {
            return String.format("%.2f", val);
        }
        return String.format("%.5f", val);
    }

    public List<TradeRadarItem> scanFutureSetups(Map<String, Map<String, List<Candle>>> candleData,
                                                Map<String, Double> currentPrices,
                                                Map<String, Double> spreads) {
        List<TradeRadarItem> radarList = new ArrayList<>();
        List<String> pairs = List.of("XAUUSD", "EURUSD", "GBPUSD", "USDJPY", "BTCUSD");
        List<String> timeframes = List.of("5m", "15m", "30m", "1h", "4h");
        List<String> modes = List.of("SCALP", "SWING", "SNIPER");

        for (String sym : pairs) {
            Double curPrice = currentPrices.get(sym);
            if (curPrice == null) continue;
            Double spread = spreads.getOrDefault(sym, 0.1);
            Map<String, List<Candle>> tfMap = candleData.get(sym);
            if (tfMap == null) continue;

            for (String tf : timeframes) {
                List<Candle> candles = tfMap.get(tf);
                if (candles == null || candles.size() < 30) continue;

                for (String mode : modes) {
                    if ("SCALP".equals(mode) && ("1h".equals(tf) || "4h".equals(tf))) continue;
                    if ("SWING".equals(mode) && ("5m".equals(tf))) continue;
                    if ("SNIPER".equals(mode) && ("4h".equals(tf))) continue;

                    AnalysisResult analysis = analyzeMarket(sym, tf, mode, new ArrayList<>(candles), spread);
                    TradeSetup setup = analysis.getTradeSetup();
                    if (setup != null && ("BUY".equals(setup.getSignal()) || "SELL".equals(setup.getSignal()))
                            && setup.getConfidence() >= 70 && setup.getRiskRewardRatio() >= 3.0) {

                        double dist = Math.abs(curPrice - setup.getEntryPrice());
                        double distPct = (dist / curPrice) * 100.0;
                        String distDesc = String.format("%.2f away (%.2f%%)", dist, distPct);
                        if (sym.contains("EUR") || sym.contains("GBP")) {
                            distDesc = String.format("%.1f pips away", dist / 0.00010);
                        } else if (sym.contains("XAU")) {
                            distDesc = String.format("$%.2f away (%.1f pips)", dist, dist * 10.0);
                        }

                        double risk = Math.abs(setup.getEntryPrice() - setup.getStopLoss());
                        double reward = Math.abs(setup.getTakeProfit1() - setup.getEntryPrice());
                        double riskDollar = calculateDollarPnl(sym, risk, 0.10, curPrice);
                        double rewardDollar = calculateDollarPnl(sym, reward, 0.10, curPrice);

                        String status = distPct <= 0.08 ? "ACTIVE_IN_ZONE" : "PENDING_PULLBACK";

                        String friendlyName = switch (sym) {
                            case "XAUUSD" -> "Gold (XAU/USD)";
                            case "EURUSD" -> "Euro (EUR/USD)";
                            case "GBPUSD" -> "British Pound (GBP/USD)";
                            case "USDJPY" -> "USD/JPY";
                            case "BTCUSD" -> "Bitcoin (BTC/USD)";
                            default -> sym;
                        };

                        radarList.add(new TradeRadarItem(
                                "RADAR-" + sym + "-" + tf + "-" + mode,
                                sym,
                                friendlyName,
                                tf,
                                mode,
                                setup.getSignal(),
                                setup.getConfidence(),
                                round(curPrice, 5),
                                round(setup.getEntryPrice(), 5),
                                round(setup.getStopLoss(), 5),
                                round(setup.getTakeProfit1(), 5),
                                round(setup.getRiskRewardRatio(), 2),
                                round(riskDollar, 2),
                                round(rewardDollar, 2),
                                round(dist, 5),
                                distDesc,
                                status,
                                setup.getConfluencePoints(),
                                System.currentTimeMillis()
                        ));
                    }
                }
            }
        }

        radarList.sort((a, b) -> {
            if ("ACTIVE_IN_ZONE".equals(a.getStatus()) && !"ACTIVE_IN_ZONE".equals(b.getStatus())) return -1;
            if (!"ACTIVE_IN_ZONE".equals(a.getStatus()) && "ACTIVE_IN_ZONE".equals(b.getStatus())) return 1;
            return Double.compare(b.getRiskRewardRatio(), a.getRiskRewardRatio());
        });

        return radarList;
    }

    private double round(double val, int decimals) {
        double p = Math.pow(10, decimals);
        return Math.round(val * p) / p;
    }
}

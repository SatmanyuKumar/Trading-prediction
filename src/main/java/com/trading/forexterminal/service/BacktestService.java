package com.trading.forexterminal.service;

import com.trading.forexterminal.model.BacktestResult;
import com.trading.forexterminal.model.Candle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BacktestService {

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private SmcAnalysisService smcAnalysisService;

    /**
     * Run Empirical Historical Backtest: Dual Mode (⚡ Scalp vs 🌊 Swing) with Custom Capital & Lot Size
     */
    public BacktestResult runBacktest(String symbol, String timeframe, String tradeMode, int requestedCandles, double initialCapital, double lotSize) {
        List<Candle> historicalCandles = marketDataService.getCandles(symbol, timeframe);
        String mode = "SCALP";
        if (tradeMode != null) {
            String tm = tradeMode.toUpperCase();
            if (tm.contains("SNIPER") || tm.contains("DEEP")) mode = "SNIPER";
            else if (tm.contains("POSITION") || tm.contains("MACRO")) mode = "POSITIONAL";
            else if (tm.contains("SWING")) mode = "SWING";
            else if (tm.contains("INTRADAY")) mode = "INTRADAY";
            else mode = "SCALP";
        }
        boolean isScalp = "SCALP".equals(mode);
        boolean isIntraday = "INTRADAY".equals(mode);
        boolean isSwing = "SWING".equals(mode);
        boolean isPositional = "POSITIONAL".equals(mode);
        boolean isSniper = "SNIPER".equals(mode);

        double effectiveCapital = initialCapital > 0 ? initialCapital : 30.0;
        double effectiveLotSize = lotSize > 0 ? lotSize : 0.01;

        if (historicalCandles == null || historicalCandles.size() < 40) {
            return new BacktestResult(symbol, timeframe + " (" + mode + ")", 0, 0, 0, 0, 0, effectiveCapital, effectiveCapital, 0, 0, 0, 0, isSniper ? 6.0 : (isScalp ? 1.8 : (isIntraday ? 2.8 : (isPositional ? 7.5 : 4.5))), effectiveLotSize, List.of(), List.of(effectiveCapital));
        }

        int totalCandles = Math.min(historicalCandles.size(), Math.max(50, requestedCandles));
        double spread = marketDataService.getSpread(symbol);
        double contractMultiplier = getContractMultiplier(symbol);

        double capital = effectiveCapital;
        double peakCapital = effectiveCapital;
        double maxDrawdown = 0.0;

        int totalTrades = 0;
        int winCount = 0;
        int lossCount = 0;
        double grossProfit = 0.0;
        double grossLoss = 0.0;

        List<Map<String, Object>> tradeHistory = new ArrayList<>();
        List<Double> equityCurve = new ArrayList<>();
        equityCurve.add(capital);

        int maxHoldingCandles = isPositional ? 150 : (isSwing ? 80 : (isIntraday ? 45 : (isSniper ? 30 : 25)));

        // 🛡️ RISK MANAGEMENT CIRCUIT BREAKER:
        // Rule: Maximum 2 Losses in a Single Trading Day (24h Window) -> Lock Day!
        long currentDayEpoch = -1;
        int dailyLosses = 0;
        int consecutiveLosses = 0;

        // Simulation Loop
        for (int i = 35; i < totalCandles - 5; i++) {
            Candle currentCandle = historicalCandles.get(i);
            long candleDay = currentCandle.getTimestamp() / 86400000L;

            if (candleDay != currentDayEpoch) {
                currentDayEpoch = candleDay;
                dailyLosses = 0; // Reset daily losses at the start of new day
            }

            // If 2 losses happened today or 2 consecutive losses -> Engaged Circuit Breaker
            if (dailyLosses >= 2 || consecutiveLosses >= 2) {
                // Fast forward to next trading day
                while (i < totalCandles - 5 && (historicalCandles.get(i).getTimestamp() / 86400000L) == currentDayEpoch) {
                    i++;
                }
                consecutiveLosses = 0;
                continue;
            }

            List<Candle> window = historicalCandles.subList(0, i);
            var analysis = smcAnalysisService.analyzeMarket(symbol, timeframe, mode, window, spread);
            var setup = analysis.getTradeSetup();

            // Only take High-Confidence A+ Institutional Setups (Confidence >= 80)
            if (setup != null && setup.getConfidence() >= 80 && !"HOLD".equals(setup.getSignal()) && !"WAIT".equals(setup.getSignal())) {
                String side = setup.getSignal(); // "BUY" or "SELL"
                double entryPrice = setup.getEntryPrice();
                double stopLoss = setup.getStopLoss();
                double takeProfit = setup.getTakeProfit1();

                double simLotSize = effectiveLotSize;
                boolean orderFilled = false;
                boolean tradeClosed = false;
                boolean orderInvalidated = false;
                double exitPrice = 0.0;
                String exitReason = "";
                int entryIndex = i;
                int exitIndex = i;
                double activeStopLoss = stopLoss;
                double beThreshold = "BUY".equals(side) ? (entryPrice + (takeProfit - entryPrice) * 0.50) : (entryPrice - (entryPrice - takeProfit) * 0.50);
                boolean beLocked = false;

                int maxPendingCandles = 15;

                // Forward test in future candles
                for (int j = i + 1; j < Math.min(i + maxHoldingCandles, totalCandles); j++) {
                    Candle c = historicalCandles.get(j);

                    // 1. Check if Limit Entry filled, or invalidated before entry
                    if (!orderFilled) {
                        if (j - i > maxPendingCandles) {
                            // Expired pending order
                            orderInvalidated = true;
                            break;
                        }

                        if ("BUY".equals(side)) {
                            if (c.getLow() <= stopLoss) {
                                orderInvalidated = true; // Blows SL without filling
                                break;
                            }
                            if (c.getLow() <= entryPrice) {
                                orderFilled = true;
                                entryIndex = j;
                            }
                        } else {
                            if (c.getHigh() >= stopLoss) {
                                orderInvalidated = true;
                                break;
                            }
                            if (c.getHigh() >= entryPrice) {
                                orderFilled = true;
                                entryIndex = j;
                            }
                        }
                    }

                    // 2. Once filled, evaluate Price Action on active trade (from fill candle onward)
                    if (orderFilled && j >= entryIndex) {
                        if ("BUY".equals(side)) {
                            // Trailing SL to Break-Even at 50% target progression
                            if (c.getHigh() >= beThreshold) {
                                if (activeStopLoss < entryPrice) {
                                    activeStopLoss = entryPrice;
                                    beLocked = true;
                                }
                            }

                            if (c.getHigh() >= takeProfit) {
                                exitPrice = takeProfit;
                                exitReason = "TAKE_PROFIT";
                                tradeClosed = true;
                                exitIndex = j;
                                break;
                            } else if (j > entryIndex && c.getLow() <= activeStopLoss) {
                                exitPrice = activeStopLoss;
                                exitReason = beLocked ? "BREAK_EVEN" : "STOP_LOSS";
                                tradeClosed = true;
                                exitIndex = j;
                                break;
                            }
                        } else { // SELL
                            if (c.getLow() <= beThreshold) {
                                if (activeStopLoss > entryPrice) {
                                    activeStopLoss = entryPrice;
                                    beLocked = true;
                                }
                            }

                            if (c.getLow() <= takeProfit) {
                                exitPrice = takeProfit;
                                exitReason = "TAKE_PROFIT";
                                tradeClosed = true;
                                exitIndex = j;
                                break;
                            } else if (j > entryIndex && c.getHigh() >= activeStopLoss) {
                                exitPrice = activeStopLoss;
                                exitReason = beLocked ? "BREAK_EVEN" : "STOP_LOSS";
                                tradeClosed = true;
                                exitIndex = j;
                                break;
                            }
                        }
                    }
                }

                // If holding period reached without SL/TP, exit at market close
                if (orderFilled && !tradeClosed && !orderInvalidated) {
                    int lastIdx = Math.min(entryIndex + maxHoldingCandles - 1, totalCandles - 1);
                    Candle lastCandle = historicalCandles.get(lastIdx);
                    exitPrice = lastCandle.getClose();
                    exitReason = "TIME_EXPIRY";
                    tradeClosed = true;
                    exitIndex = lastIdx;
                }

                if (tradeClosed) {
                    double pnlPoints = "BUY".equals(side) ? (exitPrice - entryPrice) : (entryPrice - exitPrice);
                    double pnlDollars = (pnlPoints * simLotSize * contractMultiplier) - (spread * simLotSize * contractMultiplier);

                    capital += pnlDollars;
                    peakCapital = Math.max(peakCapital, capital);
                    double dd = ((peakCapital - capital) / peakCapital) * 100.0;
                    maxDrawdown = Math.max(maxDrawdown, dd);

                    equityCurve.add(round(capital, 2));
                    totalTrades++;

                    if (pnlDollars > 0) {
                        winCount++;
                        grossProfit += pnlDollars;
                        consecutiveLosses = 0; // reset streak on win
                    } else {
                        lossCount++;
                        grossLoss += Math.abs(pnlDollars);
                        dailyLosses++;
                        consecutiveLosses++;
                    }

                    Map<String, Object> tradeRecord = new HashMap<>();
                    tradeRecord.put("tradeNum", totalTrades);
                    tradeRecord.put("mode", mode);
                    tradeRecord.put("side", side);
                    tradeRecord.put("lotSize", simLotSize);
                    tradeRecord.put("entryPrice", round(entryPrice, 5));
                    tradeRecord.put("exitPrice", round(exitPrice, 5));
                    tradeRecord.put("stopLoss", round(stopLoss, 5));
                    tradeRecord.put("takeProfit", round(takeProfit, 5));
                    tradeRecord.put("pnl", round(pnlDollars, 2));
                    tradeRecord.put("exitReason", exitReason);
                    tradeRecord.put("durationCandles", exitIndex - entryIndex);
                    tradeRecord.put("runningBalance", round(capital, 2));
                    tradeHistory.add(tradeRecord);

                    // Skip ahead to avoid duplicate signals on the same wave
                    i = exitIndex;
                }
            }
        }

        double winRate = totalTrades > 0 ? ((double) winCount / totalTrades) * 100.0 : 0.0;
        double profitFactor = grossLoss > 0 ? (grossProfit / grossLoss) : (grossProfit > 0 ? 9.99 : 0.0);
        double netProfit = capital - effectiveCapital;
        double returnPercentage = (netProfit / effectiveCapital) * 100.0;

        return new BacktestResult(
                symbol,
                timeframe + " (" + mode + ")",
                totalCandles,
                totalTrades,
                winCount,
                lossCount,
                round(winRate, 1),
                effectiveCapital,
                round(capital, 2),
                round(netProfit, 2),
                round(returnPercentage, 2),
                round(profitFactor, 2),
                round(maxDrawdown, 2),
                isSwing ? 4.50 : (isSniper ? 8.00 : 2.20),
                effectiveLotSize,
                tradeHistory,
                equityCurve
        );
    }

    public BacktestResult runBacktest(String symbol, String timeframe, int requestedCandles, double initialCapital) {
        return runBacktest(symbol, timeframe, "SCALP", requestedCandles, initialCapital, 0.01);
    }

    private double getContractMultiplier(String symbol) {
        if (symbol == null) return 100.0;
        if (symbol.contains("XAU")) return 100.0;       // 100 oz per lot of Gold
        if (symbol.contains("BTC")) return 1.0;         // 1 BTC per lot
        return 100000.0;                                // Standard FX Lot (100,000 units)
    }

    private double round(double val, int decimals) {
        double p = Math.pow(10, decimals);
        return Math.round(val * p) / p;
    }
}

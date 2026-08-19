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
     * Run Empirical Historical Backtest: Dual Mode (⚡ Scalp vs 🌊 Swing)
     */
    public BacktestResult runBacktest(String symbol, String timeframe, String tradeMode, int requestedCandles, double initialCapital) {
        List<Candle> historicalCandles = marketDataService.getCandles(symbol, timeframe);
        String mode = (tradeMode != null && "SWING".equalsIgnoreCase(tradeMode)) ? "SWING" : "SCALP";
        boolean isSwing = "SWING".equals(mode);

        if (historicalCandles == null || historicalCandles.size() < 40) {
            return new BacktestResult(symbol, timeframe + " (" + mode + ")", 0, 0, 0, 0, 0, initialCapital, initialCapital, 0, 0, 0, 0, isSwing ? 4.5 : 2.2, List.of(), List.of(initialCapital));
        }

        int totalCandles = Math.min(historicalCandles.size(), Math.max(50, requestedCandles));
        double spread = marketDataService.getSpread(symbol);
        double contractMultiplier = getContractMultiplier(symbol);

        double capital = initialCapital;
        double peakCapital = initialCapital;
        double maxDrawdown = 0.0;

        int totalTrades = 0;
        int winCount = 0;
        int lossCount = 0;
        double grossProfit = 0.0;
        double grossLoss = 0.0;

        List<Map<String, Object>> tradeHistory = new ArrayList<>();
        List<Double> equityCurve = new ArrayList<>();
        equityCurve.add(capital);

        int maxHoldingCandles = isSwing ? 120 : 35;

        // Simulation Loop
        for (int i = 35; i < totalCandles - 5; i++) {
            List<Candle> window = historicalCandles.subList(0, i);
            var analysis = smcAnalysisService.analyzeMarket(symbol, timeframe, mode, window, spread);
            var setup = analysis.getTradeSetup();

            // Only take High-Confidence A+ Institutional Setups (Confidence >= 80)
            if (setup != null && setup.getConfidence() >= 80) {
                String side = setup.getSignal(); // "BUY" or "SELL"
                double entryPrice = setup.getEntryPrice();
                double stopLoss = setup.getStopLoss();
                double takeProfit = setup.getTakeProfit1();

                // Safe 0.10 Mini lot in simulation
                double lotSize = 0.10;
                boolean orderFilled = false;
                boolean tradeClosed = false;
                double exitPrice = 0.0;
                String exitReason = "";
                int entryIndex = i;
                int exitIndex = i;
                double activeStopLoss = stopLoss;
                double beThreshold = "BUY".equals(side) ? (entryPrice + (takeProfit - entryPrice) * 0.50) : (entryPrice - (entryPrice - takeProfit) * 0.50);
                boolean beLocked = false;

                // Forward test in future candles
                for (int j = i + 1; j < Math.min(i + maxHoldingCandles, totalCandles); j++) {
                    Candle c = historicalCandles.get(j);

                    // 1. Check if Limit Entry filled
                    if (!orderFilled) {
                        if ("BUY".equals(side) && c.getLow() <= entryPrice) {
                            orderFilled = true;
                            entryIndex = j;
                        } else if ("SELL".equals(side) && c.getHigh() >= entryPrice) {
                            orderFilled = true;
                            entryIndex = j;
                        }
                    }

                    // 2. Once filled, evaluate Price Action on subsequent/active candles
                    if (orderFilled) {
                        if ("BUY".equals(side)) {
                            // Probability-Aware Trailing SL
                            if (c.getHigh() >= beThreshold) {
                                if (setup.getConfidence() >= 80) {
                                    // High TP Probability (>= 80%): Move SL to 0 Risk (Break-Even = entryPrice)
                                    if (activeStopLoss < entryPrice) {
                                        activeStopLoss = entryPrice;
                                        beLocked = true;
                                    }
                                } else {
                                    // Moderate/Low TP Probability (< 80%): Move SL to PLUS SL (Lock in +50% Profit)
                                    double currentGain = c.getHigh() - entryPrice;
                                    double plusSl = entryPrice + (currentGain * 0.50);
                                    if (plusSl > activeStopLoss) {
                                        activeStopLoss = plusSl;
                                        beLocked = true;
                                    }
                                }
                            }

                            boolean isBullishCandle = c.getClose() >= c.getOpen();

                            // If candle is bullish, check TP first; if bearish, check SL first
                            if (isBullishCandle) {
                                if (c.getHigh() >= takeProfit) {
                                    exitPrice = takeProfit;
                                    exitReason = "TAKE_PROFIT";
                                    tradeClosed = true;
                                    exitIndex = j;
                                    break;
                                } else if (c.getLow() <= activeStopLoss) {
                                    exitPrice = activeStopLoss;
                                    exitReason = beLocked ? (activeStopLoss > entryPrice ? "PLUS_SL" : "BREAK_EVEN") : "STOP_LOSS";
                                    tradeClosed = true;
                                    exitIndex = j;
                                    break;
                                }
                            } else {
                                if (c.getLow() <= activeStopLoss) {
                                    exitPrice = activeStopLoss;
                                    exitReason = beLocked ? (activeStopLoss > entryPrice ? "PLUS_SL" : "BREAK_EVEN") : "STOP_LOSS";
                                    tradeClosed = true;
                                    exitIndex = j;
                                    break;
                                } else if (c.getHigh() >= takeProfit) {
                                    exitPrice = takeProfit;
                                    exitReason = "TAKE_PROFIT";
                                    tradeClosed = true;
                                    exitIndex = j;
                                    break;
                                }
                            }
                        } else { // SELL
                            // Probability-Aware Trailing SL
                            if (c.getLow() <= beThreshold) {
                                if (setup.getConfidence() >= 80) {
                                    // High TP Probability (>= 80%): Move SL to 0 Risk (Break-Even = entryPrice)
                                    if (activeStopLoss > entryPrice) {
                                        activeStopLoss = entryPrice;
                                        beLocked = true;
                                    }
                                } else {
                                    // Moderate/Low TP Probability (< 80%): Move SL to PLUS SL (Lock in +50% Profit)
                                    double currentGain = entryPrice - c.getLow();
                                    double plusSl = entryPrice - (currentGain * 0.50);
                                    if (plusSl < activeStopLoss) {
                                        activeStopLoss = plusSl;
                                        beLocked = true;
                                    }
                                }
                            }

                            boolean isBearishCandle = c.getClose() <= c.getOpen();

                            if (isBearishCandle) {
                                if (c.getLow() <= takeProfit) {
                                    exitPrice = takeProfit;
                                    exitReason = "TAKE_PROFIT";
                                    tradeClosed = true;
                                    exitIndex = j;
                                    break;
                                } else if (c.getHigh() >= activeStopLoss) {
                                    exitPrice = activeStopLoss;
                                    exitReason = beLocked ? (activeStopLoss < entryPrice ? "PLUS_SL" : "BREAK_EVEN") : "STOP_LOSS";
                                    tradeClosed = true;
                                    exitIndex = j;
                                    break;
                                }
                            } else {
                                if (c.getHigh() >= activeStopLoss) {
                                    exitPrice = activeStopLoss;
                                    exitReason = beLocked ? (activeStopLoss < entryPrice ? "PLUS_SL" : "BREAK_EVEN") : "STOP_LOSS";
                                    tradeClosed = true;
                                    exitIndex = j;
                                    break;
                                } else if (c.getLow() <= takeProfit) {
                                    exitPrice = takeProfit;
                                    exitReason = "TAKE_PROFIT";
                                    tradeClosed = true;
                                    exitIndex = j;
                                    break;
                                }
                            }
                        }
                    }
                }

                // If holding period reached without SL/TP, exit at market close
                if (orderFilled && !tradeClosed) {
                    int lastIdx = Math.min(entryIndex + maxHoldingCandles - 1, totalCandles - 1);
                    Candle lastCandle = historicalCandles.get(lastIdx);
                    exitPrice = lastCandle.getClose();
                    exitReason = "TIME_EXPIRY";
                    tradeClosed = true;
                    exitIndex = lastIdx;
                }

                if (tradeClosed) {
                    double pnlPoints = "BUY".equals(side) ? (exitPrice - entryPrice) : (entryPrice - exitPrice);
                    double pnlDollars = (pnlPoints * lotSize * contractMultiplier) - (spread * lotSize * contractMultiplier);

                    capital += pnlDollars;
                    peakCapital = Math.max(peakCapital, capital);
                    double dd = ((peakCapital - capital) / peakCapital) * 100.0;
                    maxDrawdown = Math.max(maxDrawdown, dd);

                    equityCurve.add(round(capital, 2));
                    totalTrades++;

                    if (pnlDollars > 0) {
                        winCount++;
                        grossProfit += pnlDollars;
                    } else {
                        lossCount++;
                        grossLoss += Math.abs(pnlDollars);
                    }

                    Map<String, Object> tradeRecord = new HashMap<>();
                    tradeRecord.put("tradeNum", totalTrades);
                    tradeRecord.put("mode", mode);
                    tradeRecord.put("side", side);
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
        double netProfit = capital - initialCapital;
        double returnPercentage = (netProfit / initialCapital) * 100.0;

        return new BacktestResult(
                symbol,
                timeframe + " (" + mode + ")",
                totalCandles,
                totalTrades,
                winCount,
                lossCount,
                round(winRate, 1),
                initialCapital,
                round(capital, 2),
                round(netProfit, 2),
                round(returnPercentage, 2),
                round(profitFactor, 2),
                round(maxDrawdown, 2),
                isSwing ? 4.50 : 2.20,
                tradeHistory,
                equityCurve
        );
    }

    public BacktestResult runBacktest(String symbol, String timeframe, int requestedCandles, double initialCapital) {
        return runBacktest(symbol, timeframe, "SCALP", requestedCandles, initialCapital);
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

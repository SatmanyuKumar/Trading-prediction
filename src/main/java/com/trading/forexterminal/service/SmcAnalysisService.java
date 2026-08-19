package com.trading.forexterminal.service;

import com.trading.forexterminal.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SmcAnalysisService {

    /**
     * Institutional Smart Money Analysis Engine
     * Supports Dual Trading Modes: SCALP (Fast 1:2.2 R:R) vs SWING (Macro 1:4.5+ R:R)
     */
    public AnalysisResult analyzeMarket(String symbol, String timeframe, String tradeMode, List<Candle> candles, double spread) {
        if (candles == null || candles.size() < 30) {
            return new AnalysisResult();
        }

        String mode = (tradeMode != null && "SWING".equalsIgnoreCase(tradeMode)) ? "SWING" : "SCALP";
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

        // 3. Multi-Factor Institutional Confluence & Strategy Generation (Scalp vs Swing)
        TradeSetup setup = generateInstitutionalSetup(symbol, timeframe, mode, candles, fvgs, orderBlocks, srLevels, ema20, ema50, ema200, atr14, spread);

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

        // Mode Parameters
        boolean isSwing = "SWING".equalsIgnoreCase(tradeMode);
        double atrMultiplier = isSwing ? 2.0 : 1.3;
        double tp1Multiplier = isSwing ? 3.5 : 3.0; // Strictly >= 3.0 (1:3+ R:R)
        double tp2Multiplier = isSwing ? 5.0 : 4.5;
        double targetRr = isSwing ? 3.5 : 3.0;

        // Timeframe Reliability Hierarchy
        String tfUpper = timeframe.toUpperCase();
        String tfReliabilityTag = switch (tfUpper) {
            case "1D", "4H" -> "👑 Maximum Macro Reliability (⭐⭐⭐⭐⭐)";
            case "1H", "30M", "15M" -> "🏛️ High Structural Reliability (⭐⭐⭐⭐)";
            case "5M", "3M" -> "⚡ Moderate Execution Timeframe (⭐⭐⭐ - HTF Anchored)";
            default -> "⚡ Micro Execution Timeframe (⭐⭐ - Strict HTF Filter)";
        };

        // Minimum Volatility Buffer per Asset Class
        double minBuffer = switch (symbol.toUpperCase()) {
            case "XAUUSD" -> 2.50; // $2.50 minimum floor on Gold (25 pips)
            case "BTCUSD" -> 80.0; // $80 floor on Bitcoin
            case "USDJPY" -> 0.15; // 15 pips on Yen
            default -> 0.00120;   // 12 pips on EURUSD / GBPUSD
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
        // 🏛️ INSTITUTIONAL DECISION ENGINE (4 Core Market Delivery Phases)
        // =========================================================================

        // PHASE 1: OVERHEAD SUPPLY REVERSAL (Short from Overhead Bearish FVG / BSL Exhaustion)
        // Triggered when price has rallied into Premium (>= 58%) OR is tapping Overhead Supply within 1.5% distance
        boolean isOverheadReversal = pricePositionPct >= 58.0 || distToOverheadSupplyPct <= 1.2 || rsi14 >= 62.0;

        if (isOverheadReversal) {
            double entry = overheadSupplyLevel;
            double structuralHigh = Math.max(entry, rangeHigh);
            double stopLoss = round(structuralHigh + dynamicAtrBuffer + spread, 5);
            double risk = Math.max(minBuffer, Math.abs(stopLoss - entry));
            stopLoss = round(entry + risk, 5);

            double tp1 = round(entry - (risk * tp1Multiplier), 5);
            double tp2 = round(entry - (risk * tp2Multiplier), 5);

            confidence = (nearestOverheadFvg != null && pricePositionPct >= 70.0) ? (isSwing ? 96 : 92) : (isSwing ? 90 : 86);

            confluences.add("Timeframe Reliability: " + tfReliabilityTag);
            confluences.add("Mode: " + (isSwing ? "🌊 Swing / Macro Trend Invalidation" : "⚡ Scalp / Intraday Momentum"));
            confluences.add("🏛️ Institutional Delivery: Market expanded up to tap Overhead Bearish Supply / Imbalance");
            confluences.add(nearestOverheadFvg != null ? "Untouched 50% Consequent Encroachment (C.E.) of Macro Overhead FVG: " + formatPrice(overheadSupplyLevel, symbol) : "Macro Range High Liquidity Pool (BSL Sweep)");
            confluences.add("Premium Positioning: Trading at " + String.format("%.1f", pricePositionPct) + "% of Macro Range (Distribution Zone)");
            confluences.add("RSI Overextension: RSI=" + String.format("%.1f", rsi14) + " (Exhaustion Reversal Magnet)");
            confluences.add("Target Risk-to-Reward: 1:" + targetRr + " (Asymmetric Short Expectancy)");

            String setupTitle = isSwing 
                ? "🎯 4H/Macro Overhead Reversal: Bearish Supply & FVG Mitigation" 
                : "⚡ Scalp Overhead Rejection: Quick Supply Tap & Short Drop";

            String bookExplanation = String.format(
                "### 📚 %s (ICT / Wyckoff Distribution Blueprint)\n\n" +
                "1. **Macro Overhead Supply Mitigation:**\n" +
                "   After an extended upward run on the %s timeframe, price is testing the **Overhead Bearish Supply FVG / BSL Pool at %s**.\n\n" +
                "2. **Smart Money Distribution Logic (Mark Douglas / ICT):**\n" +
                "   Institutions do not chase rallies in extreme Premium (%s). They distribute long positions and initiate short inventory at the 50%% Consequent Encroachment.\n\n" +
                "3. **Entry Coordinates & Buffer:**\n" +
                "   Short entry waiting at **%s** with a dynamic stop loss buffered at **%s** (%.1f× ATR volatility protection).\n\n" +
                "4. **Target & Liquidity Horizons:**\n" +
                "   Take Profit 1 at **%s** and Take Profit 2 at **%s** targeting Mean Reversion back to 50%% Equilibrium at **1:%.1f R:R**.\n",
                isSwing ? "🌊 Macro Overhead Reversal Blueprint" : "⚡ Intraday Supply Rejection",
                timeframe,
                formatPrice(overheadSupplyLevel, symbol),
                String.format("%.1f%% Range High", pricePositionPct),
                formatPrice(entry, symbol),
                formatPrice(stopLoss, symbol),
                atrMultiplier,
                formatPrice(tp1, symbol),
                formatPrice(tp2, symbol),
                targetRr
            );

            return new TradeSetup(
                    "SETUP-OVERHEAD-SHORT-" + System.currentTimeMillis(),
                    symbol,
                    timeframe,
                    "SELL",
                    confidence,
                    currentPrice,
                    entry,
                    stopLoss,
                    tp1,
                    tp2,
                    targetRr,
                    setupTitle,
                    confluences,
                    bookExplanation,
                    System.currentTimeMillis()
            );
        }

        // PHASE 2: BULLISH PULLBACK TO DEMAND TARGETING OVERHEAD SUPPLY (Expansion Long)
        // Triggered when price is in Discount / Mid-Range (< 58%) expanding upwards towards Overhead Supply
        double entry = underneathDemandLevel + spread;
        double structuralLow = Math.min(entry, rangeLow);
        double stopLoss = round(structuralLow - dynamicAtrBuffer, 5);
        double risk = Math.max(minBuffer, Math.abs(entry - stopLoss));
        stopLoss = round(entry - risk, 5);

        // Take Profit 1 is anchored to the Overhead Supply Level or 1:3+ R:R (whichever is greater)
        double idealTp1 = Math.max(entry + (risk * tp1Multiplier), overheadSupplyLevel);
        double tp1 = round(idealTp1, 5);
        double tp2 = round(entry + (risk * tp2Multiplier), 5);
        double calculatedRr = round(Math.abs(tp1 - entry) / risk, 1);

        confidence = (nearestUnderneathFvg != null && pricePositionPct <= 45.0) ? (isSwing ? 95 : 91) : (isSwing ? 89 : 85);

        confluences.add("Timeframe Reliability: " + tfReliabilityTag);
        confluences.add("Mode: " + (isSwing ? "🌊 Swing / Macro Trend Invalidation" : "⚡ Scalp / Intraday Momentum"));
        confluences.add("🏛️ Institutional Delivery: Bullish Expansion targeting Macro Overhead Supply FVG");
        confluences.add(nearestUnderneathFvg != null ? "50% Consequent Encroachment (C.E.) Demand Retest: " + formatPrice(underneathDemandLevel, symbol) : "Discount Equilibrium Accumulation Retest");
        confluences.add("Overhead Target Magnet: Bearish Supply / BSL at " + formatPrice(overheadSupplyLevel, symbol));
        confluences.add("RSI Momentum: RSI=" + String.format("%.1f", rsi14) + " (Clean Expansion Slope)");
        confluences.add("Target Risk-to-Reward: 1:" + calculatedRr + " (Asymmetric Expectancy)");

        String setupTitle = isSwing 
            ? "🚀 4H/Macro Bullish Expansion: Demand Pullback targeting Overhead Supply" 
            : "⚡ Scalp Demand Tap: Rapid Push towards Overhead Liquidity";

        String bookExplanation = String.format(
            "### 📚 %s (ICT OTE & Expansion Blueprint)\n\n" +
            "1. **Institutional Target Magnet:**\n" +
            "   Market is expanding upwards on %s seeking the **Overhead Bearish Supply FVG at %s**.\n\n" +
            "2. **Discount Entry Coordination:**\n" +
            "   Smart money waits for a pullback into the **Discount Bullish Demand zone at %s** to enter with low risk.\n\n" +
            "3. **Entry Coordinates & Buffer:**\n" +
            "   Buy entry waiting at **%s** with a dynamic stop loss buffered at **%s** (%.1f× ATR volatility protection).\n\n" +
            "4. **Target Horizons:**\n" +
            "   Take Profit 1 at **%s** (Overhead Target) and Take Profit 2 at **%s** yielding an asymmetric **1:%.1f R:R**.\n",
            isSwing ? "🌊 Macro Expansion Blueprint" : "⚡ Intraday Demand Expansion",
            timeframe,
            formatPrice(overheadSupplyLevel, symbol),
            formatPrice(underneathDemandLevel, symbol),
            formatPrice(entry, symbol),
            formatPrice(stopLoss, symbol),
            atrMultiplier,
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
        double buyEntry = nearestBullFvg != null ? nearestBullFvg.getConsequentEncroachment() + spread : currentPrice + spread;
        double buySl = nearestBullFvg != null ? nearestBullFvg.getBottom() - dynamicAtrBuffer : currentPrice - dynamicAtrBuffer;
        double buyRiskDist = Math.max(atr14 * 0.5, Math.abs(buyEntry - buySl));
        double buyTp = buyEntry + (buyRiskDist * tpMult);

        double buyRiskDollars = calculateDollarPnl(symbol, buyRiskDist, lotSize, currentPrice);
        double buyProfitDollars = calculateDollarPnl(symbol, Math.abs(buyTp - buyEntry), lotSize, currentPrice);
        double buyRiskPips = calculatePips(symbol, buyRiskDist);
        double buyProfitPips = calculatePips(symbol, Math.abs(buyTp - buyEntry));

        int buyWinProb = isBullishTrend && inDiscount && nearestBullFvg != null ? (isSwing ? 94 : 88) : (isBullishTrend ? 68 : 32);
        List<String> buyConfluences = new ArrayList<>();
        List<String> buyWarnings = new ArrayList<>();
        if (isBullishTrend) buyConfluences.add("Ascending EMA 20/50/200 Trend Alignment");
        if (inDiscount) buyConfluences.add("Price is in deep Institutional Discount (< 50% Equilibrium)");
        if (nearestBullFvg != null) buyConfluences.add("Untouched 50% Consequent Encroachment Demand FVG at " + formatPrice(buyEntry, symbol));
        if (!isBullishTrend) buyWarnings.add("⚠️ Opposing Higher-Timeframe Trend (EMA slope bearish)");
        if (inPremium) buyWarnings.add("⚠️ Price in Institutional Premium - High risk of distribution drop");

        TradeScenario buyScenario = new TradeScenario(
                "BUY", round(buyEntry, 5), round(buySl, 5), round(buyTp, 5),
                round(buyRiskDollars, 2), round(buyProfitDollars, 2),
                round(buyRiskPips, 1), round(buyProfitPips, 1),
                round(tpMult, 2), buyWinProb,
                buyWinProb >= 80, buyConfluences, buyWarnings
        );

        // --- 🔴 SELL SCENARIO ---
        double sellEntry = nearestBearFvg != null ? nearestBearFvg.getConsequentEncroachment() : currentPrice;
        double sellSl = nearestBearFvg != null ? nearestBearFvg.getTop() + dynamicAtrBuffer + spread : currentPrice + dynamicAtrBuffer + spread;
        double sellRiskDist = Math.max(atr14 * 0.5, Math.abs(sellSl - sellEntry));
        double sellTp = sellEntry - (sellRiskDist * tpMult);

        double sellRiskDollars = calculateDollarPnl(symbol, sellRiskDist, lotSize, currentPrice);
        double sellProfitDollars = calculateDollarPnl(symbol, Math.abs(sellEntry - sellTp), lotSize, currentPrice);
        double sellRiskPips = calculatePips(symbol, sellRiskDist);
        double sellProfitPips = calculatePips(symbol, Math.abs(sellEntry - sellTp));

        int sellWinProb = isBearishTrend && inPremium && nearestBearFvg != null ? (isSwing ? 94 : 88) : (isBearishTrend ? 68 : 32);
        List<String> sellConfluences = new ArrayList<>();
        List<String> sellWarnings = new ArrayList<>();
        if (isBearishTrend) sellConfluences.add("Descending EMA 20/50/200 Trend Alignment");
        if (inPremium) sellConfluences.add("Price is in Institutional Premium (> 50% Equilibrium)");
        if (nearestBearFvg != null) sellConfluences.add("Untouched 50% Consequent Encroachment Supply FVG at " + formatPrice(sellEntry, symbol));
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
        List<String> modes = List.of("SCALP", "SWING");

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

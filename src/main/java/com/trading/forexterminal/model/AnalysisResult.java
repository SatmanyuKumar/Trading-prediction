package com.trading.forexterminal.model;

import java.util.List;

public class AnalysisResult {
    private String symbol;
    private String timeframe;
    private double currentPrice;
    private double change24h;
    private double high24h;
    private double low24h;
    private double spread;
    private List<Candle> candles;
    private List<FairValueGap> fairValueGaps;
    private List<OrderBlock> orderBlocks;
    private List<SupportResistance> supportResistanceList;
    private List<MarketStructure> marketStructures;
    private List<Double> ema20;
    private List<Double> ema50;
    private List<Double> ema200;
    private TradeSetup tradeSetup;
    private List<TradeSetup> historicalSetups;

    public AnalysisResult() {}

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getChange24h() { return change24h; }
    public void setChange24h(double change24h) { this.change24h = change24h; }

    public double getHigh24h() { return high24h; }
    public void setHigh24h(double high24h) { this.high24h = high24h; }

    public double getLow24h() { return low24h; }
    public void setLow24h(double low24h) { this.low24h = low24h; }

    public double getSpread() { return spread; }
    public void setSpread(double spread) { this.spread = spread; }

    public List<Candle> getCandles() { return candles; }
    public void setCandles(List<Candle> candles) { this.candles = candles; }

    public List<FairValueGap> getFairValueGaps() { return fairValueGaps; }
    public void setFairValueGaps(List<FairValueGap> fairValueGaps) { this.fairValueGaps = fairValueGaps; }

    public List<OrderBlock> getOrderBlocks() { return orderBlocks; }
    public void setOrderBlocks(List<OrderBlock> orderBlocks) { this.orderBlocks = orderBlocks; }

    public List<SupportResistance> getSupportResistanceList() { return supportResistanceList; }
    public void setSupportResistanceList(List<SupportResistance> supportResistanceList) { this.supportResistanceList = supportResistanceList; }
    public void setSupportResistance(List<SupportResistance> supportResistanceList) { this.supportResistanceList = supportResistanceList; }

    public List<MarketStructure> getMarketStructures() { return marketStructures; }
    public void setMarketStructures(List<MarketStructure> marketStructures) { this.marketStructures = marketStructures; }

    public List<Double> getEma20() { return ema20; }
    public void setEma20(List<Double> ema20) { this.ema20 = ema20; }

    public List<Double> getEma50() { return ema50; }
    public void setEma50(List<Double> ema50) { this.ema50 = ema50; }

    public List<Double> getEma200() { return ema200; }
    public void setEma200(List<Double> ema200) { this.ema200 = ema200; }

    public TradeSetup getTradeSetup() { return tradeSetup; }
    public void setTradeSetup(TradeSetup tradeSetup) { this.tradeSetup = tradeSetup; }

    public List<TradeSetup> getHistoricalSetups() { return historicalSetups; }
    public void setHistoricalSetups(List<TradeSetup> historicalSetups) { this.historicalSetups = historicalSetups; }
}

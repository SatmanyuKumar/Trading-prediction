package com.trading.forexterminal.model;

import java.util.List;

public class TradeSetup {
    private String id;
    private String symbol;
    private String timeframe;
    private String signal; // "BUY", "SELL", "WAIT"
    private int confidence; // 0 - 100%
    private double currentPrice;
    private double entryPrice;
    private double entryPrice2; // Deep Limit Entry near SL (Minimal Risk)
    private double stopLoss;
    private double takeProfit1;
    private double takeProfit2;
    private double riskRewardRatio;
    private String setupType;
    private List<String> confluencePoints;
    private String bookRulesExplanation;
    private long timestamp;
    private String status = "ACTIVE"; // "ACTIVE", "FAILED_SL", "FAILED_TREND_SHIFT", "TP_HIT"
    private long startTimestamp;
    private long endTimestamp;
    private boolean triggered;
    private long triggeredTimestamp;

    public TradeSetup() {}

    public TradeSetup(String id, String symbol, String timeframe, String signal, int confidence, double currentPrice,
                      double entryPrice, double entryPrice2, double stopLoss, double takeProfit1, double takeProfit2,
                      double riskRewardRatio, String setupType, List<String> confluencePoints,
                      String bookRulesExplanation, long timestamp) {
        this.id = id;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.signal = signal;
        this.confidence = confidence;
        this.currentPrice = currentPrice;
        this.entryPrice = entryPrice;
        this.entryPrice2 = entryPrice2;
        this.stopLoss = stopLoss;
        this.takeProfit1 = takeProfit1;
        this.takeProfit2 = takeProfit2;
        this.riskRewardRatio = riskRewardRatio;
        this.setupType = setupType;
        this.confluencePoints = confluencePoints;
        this.bookRulesExplanation = bookRulesExplanation;
        this.timestamp = timestamp;
    }

    public TradeSetup(String id, String symbol, String timeframe, String signal, int confidence, double currentPrice,
                      double entryPrice, double stopLoss, double takeProfit1, double takeProfit2,
                      double riskRewardRatio, String setupType, List<String> confluencePoints,
                      String bookRulesExplanation, long timestamp) {
        this(id, symbol, timeframe, signal, confidence, currentPrice, entryPrice, entryPrice, stopLoss, takeProfit1, takeProfit2, riskRewardRatio, setupType, confluencePoints, bookRulesExplanation, timestamp);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public String getSignal() { return signal; }
    public void setSignal(String signal) { this.signal = signal; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public double getEntryPrice2() { return entryPrice2; }
    public void setEntryPrice2(double entryPrice2) { this.entryPrice2 = entryPrice2; }

    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }

    public double getTakeProfit1() { return takeProfit1; }
    public void setTakeProfit1(double takeProfit1) { this.takeProfit1 = takeProfit1; }

    public double getTakeProfit2() { return takeProfit2; }
    public void setTakeProfit2(double takeProfit2) { this.takeProfit2 = takeProfit2; }

    public double getRiskRewardRatio() { return riskRewardRatio; }
    public void setRiskRewardRatio(double riskRewardRatio) { this.riskRewardRatio = riskRewardRatio; }

    public String getSetupType() { return setupType; }
    public void setSetupType(String setupType) { this.setupType = setupType; }

    public List<String> getConfluencePoints() { return confluencePoints; }
    public void setConfluencePoints(List<String> confluencePoints) { this.confluencePoints = confluencePoints; }

    public String getBookRulesExplanation() { return bookRulesExplanation; }
    public void setBookRulesExplanation(String bookRulesExplanation) { this.bookRulesExplanation = bookRulesExplanation; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getStartTimestamp() { return startTimestamp; }
    public void setStartTimestamp(long startTimestamp) { this.startTimestamp = startTimestamp; }

    public long getEndTimestamp() { return endTimestamp; }
    public void setEndTimestamp(long endTimestamp) { this.endTimestamp = endTimestamp; }

    public boolean isTriggered() { return triggered; }
    public void setTriggered(boolean triggered) { this.triggered = triggered; }

    public long getTriggeredTimestamp() { return triggeredTimestamp; }
    public void setTriggeredTimestamp(long triggeredTimestamp) { this.triggeredTimestamp = triggeredTimestamp; }
}

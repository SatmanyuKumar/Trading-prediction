package com.trading.forexterminal.model;

import java.util.List;

public class TradeRadarItem {
    private String id;
    private String symbol;
    private String symbolName;
    private String timeframe;
    private String mode; // "SCALP" or "SWING"
    private String signal; // "BUY" or "SELL"
    private int confidence;
    private double currentPrice;
    private double entryPrice;
    private double stopLoss;
    private double takeProfit;
    private double riskRewardRatio;
    private double riskAmount;
    private double rewardAmount;
    private double distanceToEntry;
    private String distanceDescription;
    private String status; // "ARMED", "PENDING_PULLBACK", "ACTIVE_TRIGGER"
    private List<String> confluences;
    private long timestamp;

    public TradeRadarItem() {}

    public TradeRadarItem(String id, String symbol, String symbolName, String timeframe, String mode,
                          String signal, int confidence, double currentPrice, double entryPrice,
                          double stopLoss, double takeProfit, double riskRewardRatio, double riskAmount,
                          double rewardAmount, double distanceToEntry, String distanceDescription,
                          String status, List<String> confluences, long timestamp) {
        this.id = id;
        this.symbol = symbol;
        this.symbolName = symbolName;
        this.timeframe = timeframe;
        this.mode = mode;
        this.signal = signal;
        this.confidence = confidence;
        this.currentPrice = currentPrice;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.riskRewardRatio = riskRewardRatio;
        this.riskAmount = riskAmount;
        this.rewardAmount = rewardAmount;
        this.distanceToEntry = distanceToEntry;
        this.distanceDescription = distanceDescription;
        this.status = status;
        this.confluences = confluences;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getSymbolName() { return symbolName; }
    public void setSymbolName(String symbolName) { this.symbolName = symbolName; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getSignal() { return signal; }
    public void setSignal(String signal) { this.signal = signal; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }

    public double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(double takeProfit) { this.takeProfit = takeProfit; }

    public double getRiskRewardRatio() { return riskRewardRatio; }
    public void setRiskRewardRatio(double riskRewardRatio) { this.riskRewardRatio = riskRewardRatio; }

    public double getRiskAmount() { return riskAmount; }
    public void setRiskAmount(double riskAmount) { this.riskAmount = riskAmount; }

    public double getRewardAmount() { return rewardAmount; }
    public void setRewardAmount(double rewardAmount) { this.rewardAmount = rewardAmount; }

    public double getDistanceToEntry() { return distanceToEntry; }
    public void setDistanceToEntry(double distanceToEntry) { this.distanceToEntry = distanceToEntry; }

    public String getDistanceDescription() { return distanceDescription; }
    public void setDistanceDescription(String distanceDescription) { this.distanceDescription = distanceDescription; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<String> getConfluences() { return confluences; }
    public void setConfluences(List<String> confluences) { this.confluences = confluences; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

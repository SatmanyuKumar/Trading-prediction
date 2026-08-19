package com.trading.forexterminal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "suggestion_history")
public class SuggestionHistoryEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String timeframe;

    private String mode; // SCALP or SWING
    private String signal; // BUY or SELL
    private double entryPrice;
    private double stopLoss;
    private double takeProfit;
    private double riskRewardRatio;
    private int confidence;
    private String triggerState; // PENDING_WAIT, READY_EXECUTED, TP_HIT, SL_HIT, CANCELLED
    private double exitPrice;
    private double pnl;
    private long suggestedTime;
    private long executedTime;
    private long closedTime;

    public SuggestionHistoryEntity() {}

    public SuggestionHistoryEntity(String id, String symbol, String timeframe, String mode, String signal,
                                   double entryPrice, double stopLoss, double takeProfit, double riskRewardRatio,
                                   int confidence, String triggerState, double exitPrice, double pnl,
                                   long suggestedTime, long executedTime, long closedTime) {
        this.id = id;
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.mode = mode;
        this.signal = signal;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.riskRewardRatio = riskRewardRatio;
        this.confidence = confidence;
        this.triggerState = triggerState;
        this.exitPrice = exitPrice;
        this.pnl = pnl;
        this.suggestedTime = suggestedTime;
        this.executedTime = executedTime;
        this.closedTime = closedTime;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getSignal() { return signal; }
    public void setSignal(String signal) { this.signal = signal; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }

    public double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(double takeProfit) { this.takeProfit = takeProfit; }

    public double getRiskRewardRatio() { return riskRewardRatio; }
    public void setRiskRewardRatio(double riskRewardRatio) { this.riskRewardRatio = riskRewardRatio; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }

    public String getTriggerState() { return triggerState; }
    public void setTriggerState(String triggerState) { this.triggerState = triggerState; }

    public double getExitPrice() { return exitPrice; }
    public void setExitPrice(double exitPrice) { this.exitPrice = exitPrice; }

    public double getPnl() { return pnl; }
    public void setPnl(double pnl) { this.pnl = pnl; }

    public long getSuggestedTime() { return suggestedTime; }
    public void setSuggestedTime(long suggestedTime) { this.suggestedTime = suggestedTime; }

    public long getExecutedTime() { return executedTime; }
    public void setExecutedTime(long executedTime) { this.executedTime = executedTime; }

    public long getClosedTime() { return closedTime; }
    public void setClosedTime(long closedTime) { this.closedTime = closedTime; }
}

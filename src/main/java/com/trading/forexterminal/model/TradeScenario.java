package com.trading.forexterminal.model;

import java.util.List;

public class TradeScenario {
    private String action; // "BUY" or "SELL"
    private double entryPrice;
    private double stopLoss;
    private double takeProfit;
    private double riskDollars;
    private double profitDollars;
    private double riskPips;
    private double profitPips;
    private double riskRewardRatio;
    private int winProbability; // 0 - 100%
    private boolean isRecommended;
    private List<String> confluences;
    private List<String> warnings;

    public TradeScenario() {}

    public TradeScenario(String action, double entryPrice, double stopLoss, double takeProfit,
                         double riskDollars, double profitDollars, double riskPips, double profitPips,
                         double riskRewardRatio, int winProbability, boolean isRecommended,
                         List<String> confluences, List<String> warnings) {
        this.action = action;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.riskDollars = riskDollars;
        this.profitDollars = profitDollars;
        this.riskPips = riskPips;
        this.profitPips = profitPips;
        this.riskRewardRatio = riskRewardRatio;
        this.winProbability = winProbability;
        this.isRecommended = isRecommended;
        this.confluences = confluences;
        this.warnings = warnings;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }

    public double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(double takeProfit) { this.takeProfit = takeProfit; }

    public double getRiskDollars() { return riskDollars; }
    public void setRiskDollars(double riskDollars) { this.riskDollars = riskDollars; }

    public double getProfitDollars() { return profitDollars; }
    public void setProfitDollars(double profitDollars) { this.profitDollars = profitDollars; }

    public double getRiskPips() { return riskPips; }
    public void setRiskPips(double riskPips) { this.riskPips = riskPips; }

    public double getProfitPips() { return profitPips; }
    public void setProfitPips(double profitPips) { this.profitPips = profitPips; }

    public double getRiskRewardRatio() { return riskRewardRatio; }
    public void setRiskRewardRatio(double riskRewardRatio) { this.riskRewardRatio = riskRewardRatio; }

    public int getWinProbability() { return winProbability; }
    public void setWinProbability(int winProbability) { this.winProbability = winProbability; }

    public boolean isRecommended() { return isRecommended; }
    public void setRecommended(boolean recommended) { isRecommended = recommended; }

    public List<String> getConfluences() { return confluences; }
    public void setConfluences(List<String> confluences) { this.confluences = confluences; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}

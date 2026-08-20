package com.trading.forexterminal.model;

import java.util.List;
import java.util.Map;

public class BacktestResult {
    private String symbol;
    private String timeframe;
    private int totalCandles;
    private int totalTrades;
    private int winningTrades;
    private int losingTrades;
    private double winRate;
    private double initialCapital;
    private double finalCapital;
    private double netProfit;
    private double returnPercentage;
    private double profitFactor;
    private double maxDrawdown;
    private double averageRiskReward;
    private double lotSize = 0.01;
    private List<Map<String, Object>> tradeHistory;
    private List<Double> equityCurve;

    public BacktestResult() {}

    public BacktestResult(String symbol, String timeframe, int totalCandles, int totalTrades,
                          int winningTrades, int losingTrades, double winRate, double initialCapital,
                          double finalCapital, double netProfit, double returnPercentage,
                          double profitFactor, double maxDrawdown, double averageRiskReward,
                          double lotSize,
                          List<Map<String, Object>> tradeHistory, List<Double> equityCurve) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.totalCandles = totalCandles;
        this.totalTrades = totalTrades;
        this.winningTrades = winningTrades;
        this.losingTrades = losingTrades;
        this.winRate = winRate;
        this.initialCapital = initialCapital;
        this.finalCapital = finalCapital;
        this.netProfit = netProfit;
        this.returnPercentage = returnPercentage;
        this.profitFactor = profitFactor;
        this.maxDrawdown = maxDrawdown;
        this.averageRiskReward = averageRiskReward;
        this.lotSize = lotSize;
        this.tradeHistory = tradeHistory;
        this.equityCurve = equityCurve;
    }

    public double getLotSize() { return lotSize; }
    public void setLotSize(double lotSize) { this.lotSize = lotSize; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public int getTotalCandles() { return totalCandles; }
    public void setTotalCandles(int totalCandles) { this.totalCandles = totalCandles; }

    public int getTotalTrades() { return totalTrades; }
    public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }

    public int getWinningTrades() { return winningTrades; }
    public void setWinningTrades(int winningTrades) { this.winningTrades = winningTrades; }

    public int getLosingTrades() { return losingTrades; }
    public void setLosingTrades(int losingTrades) { this.losingTrades = losingTrades; }

    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }

    public double getInitialCapital() { return initialCapital; }
    public void setInitialCapital(double initialCapital) { this.initialCapital = initialCapital; }

    public double getFinalCapital() { return finalCapital; }
    public void setFinalCapital(double finalCapital) { this.finalCapital = finalCapital; }

    public double getNetProfit() { return netProfit; }
    public void setNetProfit(double netProfit) { this.netProfit = netProfit; }

    public double getReturnPercentage() { return returnPercentage; }
    public void setReturnPercentage(double returnPercentage) { this.returnPercentage = returnPercentage; }

    public double getProfitFactor() { return profitFactor; }
    public void setProfitFactor(double profitFactor) { this.profitFactor = profitFactor; }

    public double getMaxDrawdown() { return maxDrawdown; }
    public void setMaxDrawdown(double maxDrawdown) { this.maxDrawdown = maxDrawdown; }

    public double getAverageRiskReward() { return averageRiskReward; }
    public void setAverageRiskReward(double averageRiskReward) { this.averageRiskReward = averageRiskReward; }

    public List<Map<String, Object>> getTradeHistory() { return tradeHistory; }
    public void setTradeHistory(List<Map<String, Object>> tradeHistory) { this.tradeHistory = tradeHistory; }

    public List<Double> getEquityCurve() { return equityCurve; }
    public void setEquityCurve(List<Double> equityCurve) { this.equityCurve = equityCurve; }
}

package com.trading.forexterminal.model;

public class TradeAdvisorResult {
    private String symbol;
    private String timeframe;
    private String tradeMode;
    private double currentPrice;
    private double lotSize;
    private String recommendedAction; // "BUY", "SELL", "WAIT_AND_PRESERVE"
    private String strategyVerdict;    // "VALID_A_PLUS", "CHOPPY_PRESERVE", "COUNTER_HTF_RISKY"
    private String verdictHeadline;
    private String verdictExplanation;
    private double mathematicalExpectancy; // expected $ per trade
    private TradeScenario buyScenario;
    private TradeScenario sellScenario;
    private long timestamp;

    public TradeAdvisorResult() {}

    public TradeAdvisorResult(String symbol, String timeframe, String tradeMode, double currentPrice,
                              double lotSize, String recommendedAction, String strategyVerdict,
                              String verdictHeadline, String verdictExplanation, double mathematicalExpectancy,
                              TradeScenario buyScenario, TradeScenario sellScenario, long timestamp) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.tradeMode = tradeMode;
        this.currentPrice = currentPrice;
        this.lotSize = lotSize;
        this.recommendedAction = recommendedAction;
        this.strategyVerdict = strategyVerdict;
        this.verdictHeadline = verdictHeadline;
        this.verdictExplanation = verdictExplanation;
        this.mathematicalExpectancy = mathematicalExpectancy;
        this.buyScenario = buyScenario;
        this.sellScenario = sellScenario;
        this.timestamp = timestamp;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }

    public String getTradeMode() { return tradeMode; }
    public void setTradeMode(String tradeMode) { this.tradeMode = tradeMode; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getLotSize() { return lotSize; }
    public void setLotSize(double lotSize) { this.lotSize = lotSize; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }

    public String getStrategyVerdict() { return strategyVerdict; }
    public void setStrategyVerdict(String strategyVerdict) { this.strategyVerdict = strategyVerdict; }

    public String getVerdictHeadline() { return verdictHeadline; }
    public void setVerdictHeadline(String verdictHeadline) { this.verdictHeadline = verdictHeadline; }

    public String getVerdictExplanation() { return verdictExplanation; }
    public void setVerdictExplanation(String verdictExplanation) { this.verdictExplanation = verdictExplanation; }

    public double getMathematicalExpectancy() { return mathematicalExpectancy; }
    public void setMathematicalExpectancy(double mathematicalExpectancy) { this.mathematicalExpectancy = mathematicalExpectancy; }

    public TradeScenario getBuyScenario() { return buyScenario; }
    public void setBuyScenario(TradeScenario buyScenario) { this.buyScenario = buyScenario; }

    public TradeScenario getSellScenario() { return sellScenario; }
    public void setSellScenario(TradeScenario sellScenario) { this.sellScenario = sellScenario; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

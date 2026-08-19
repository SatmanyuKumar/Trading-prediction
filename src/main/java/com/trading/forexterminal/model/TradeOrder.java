package com.trading.forexterminal.model;

public class TradeOrder {
    private String id;
    private String symbol;
    private String type; // BUY or SELL
    private double entryPrice; // Ask for BUY, Bid for SELL
    private double stopLoss;
    private double takeProfit;
    private double lotSize;
    private double spreadAtEntry;
    private long openTime;
    private long closeTime;
    private double closePrice;
    private double pnl;
    private String status; // OPEN, CLOSED_TP, CLOSED_SL, CLOSED_MANUAL

    private String trailingStatus = "STANDARD"; // STANDARD, BREAK_EVEN, PLUS_SL
    private String timeframe = "15m";

    public TradeOrder() {
    }

    public TradeOrder(String id, String symbol, String type, double entryPrice, double stopLoss, double takeProfit, double lotSize, double spreadAtEntry, long openTime) {
        this(id, symbol, type, "15m", entryPrice, stopLoss, takeProfit, lotSize, spreadAtEntry, openTime);
    }

    public TradeOrder(String id, String symbol, String type, String timeframe, double entryPrice, double stopLoss, double takeProfit, double lotSize, double spreadAtEntry, long openTime) {
        this.id = id;
        this.symbol = symbol;
        this.type = type;
        this.timeframe = (timeframe != null && !timeframe.isEmpty()) ? timeframe : "15m";
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.lotSize = lotSize;
        this.spreadAtEntry = spreadAtEntry;
        this.openTime = openTime;
        this.status = "OPEN";
        this.pnl = 0.0;
    }

    public void updatePnL(double currentPrice, double spread) {
        if (!"OPEN".equals(this.status)) return;

        double multiplier = getContractMultiplier(this.symbol);
        
        if ("BUY".equals(this.type)) {
            // For BUY: you bought at Ask (entryPrice), you close at current Bid (currentPrice)
            double currentBid = currentPrice;
            this.pnl = (currentBid - this.entryPrice) * this.lotSize * multiplier;
        } else if ("SELL".equals(this.type)) {
            // For SELL: you sold at Bid (entryPrice), you close at current Ask (currentPrice + spread)
            double currentAsk = currentPrice + spread;
            this.pnl = (this.entryPrice - currentAsk) * this.lotSize * multiplier;
        }
    }

    private double getContractMultiplier(String sym) {
        if (sym == null) return 100.0;
        if (sym.contains("XAU")) return 100.0; // 1 standard lot = 100 oz of Gold
        if (sym.contains("BTC")) return 1.0;   // 1 lot = 1 BTC
        if (sym.contains("JPY")) return 1000.0;
        return 100000.0; // Standard Forex lot = 100,000 units
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }

    public double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(double takeProfit) { this.takeProfit = takeProfit; }

    public double getLotSize() { return lotSize; }
    public void setLotSize(double lotSize) { this.lotSize = lotSize; }

    public double getSpreadAtEntry() { return spreadAtEntry; }
    public void setSpreadAtEntry(double spreadAtEntry) { this.spreadAtEntry = spreadAtEntry; }

    public long getOpenTime() { return openTime; }
    public void setOpenTime(long openTime) { this.openTime = openTime; }

    public long getCloseTime() { return closeTime; }
    public void setCloseTime(long closeTime) { this.closeTime = closeTime; }

    public double getClosePrice() { return closePrice; }
    public void setClosePrice(double closePrice) { this.closePrice = closePrice; }

    public double getPnl() { return pnl; }
    public void setPnl(double pnl) { this.pnl = pnl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTrailingStatus() { return trailingStatus; }
    public void setTrailingStatus(String trailingStatus) { this.trailingStatus = trailingStatus; }

    public String getTimeframe() { return timeframe; }
    public void setTimeframe(String timeframe) { this.timeframe = timeframe; }
}

package com.trading.forexterminal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trade_orders")
public class TradeOrderEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String type; // BUY or SELL

    private String timeframe = "15m";

    private double entryPrice;
    private double stopLoss;
    private double takeProfit;
    private double lotSize;
    private double spreadAtEntry;
    private long openTime;
    private long closeTime;
    private double closePrice;
    private double pnl;

    @Column(nullable = false)
    private String status; // OPEN, PENDING_LIMIT, CLOSED_TP, CLOSED_SL, CLOSED_MANUAL, CANCELLED

    private String trailingStatus = "STANDARD";

    public TradeOrderEntity() {}

    public TradeOrderEntity(String id, String symbol, String type, String timeframe, double entryPrice, double stopLoss,
                            double takeProfit, double lotSize, double spreadAtEntry, long openTime,
                            long closeTime, double closePrice, double pnl, String status, String trailingStatus) {
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
        this.closeTime = closeTime;
        this.closePrice = closePrice;
        this.pnl = pnl;
        this.status = status;
        this.trailingStatus = trailingStatus;
    }

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

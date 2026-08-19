package com.trading.forexterminal.model;

public class OrderBlock {
    private String id;
    private String type; // BULLISH or BEARISH
    private double top;
    private double bottom;
    private double openPrice;
    private double closePrice;
    private long timestamp;
    private int candleIndex;
    private boolean mitigated;
    private double volume;

    public OrderBlock() {}

    public OrderBlock(String id, String type, double top, double bottom, double openPrice, double closePrice, long timestamp, int candleIndex, double volume) {
        this.id = id;
        this.type = type;
        this.top = top;
        this.bottom = bottom;
        this.openPrice = openPrice;
        this.closePrice = closePrice;
        this.timestamp = timestamp;
        this.candleIndex = candleIndex;
        this.mitigated = false;
        this.volume = volume;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getTop() { return top; }
    public void setTop(double top) { this.top = top; }

    public double getBottom() { return bottom; }
    public void setBottom(double bottom) { this.bottom = bottom; }

    public double getOpenPrice() { return openPrice; }
    public void setOpenPrice(double openPrice) { this.openPrice = openPrice; }

    public double getClosePrice() { return closePrice; }
    public void setClosePrice(double closePrice) { this.closePrice = closePrice; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getCandleIndex() { return candleIndex; }
    public void setCandleIndex(int candleIndex) { this.candleIndex = candleIndex; }

    public boolean isMitigated() { return mitigated; }
    public void setMitigated(boolean mitigated) { this.mitigated = mitigated; }

    public double getVolume() { return volume; }
    public void setVolume(double volume) { this.volume = volume; }
}

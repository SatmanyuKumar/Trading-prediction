package com.trading.forexterminal.model;

public class MarketStructure {
    private String id;
    private String type; // "BOS" (Break of Structure), "CHOCH" (Change of Character), "SWING_HIGH", "SWING_LOW"
    private String direction; // "BULLISH" or "BEARISH"
    private double price;
    private long timestamp;
    private int candleIndex;
    private String description;

    public MarketStructure() {}

    public MarketStructure(String id, String type, String direction, double price, long timestamp, int candleIndex, String description) {
        this.id = id;
        this.type = type;
        this.direction = direction;
        this.price = price;
        this.timestamp = timestamp;
        this.candleIndex = candleIndex;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public int getCandleIndex() { return candleIndex; }
    public void setCandleIndex(int candleIndex) { this.candleIndex = candleIndex; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

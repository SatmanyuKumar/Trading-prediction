package com.trading.forexterminal.model;

public class SupportResistance {
    private String id;
    private double price;
    private String type; // SUPPORT or RESISTANCE
    private int touches;
    private double strength; // 0.0 to 1.0
    private long firstTouchTime;
    private long lastTouchTime;

    public SupportResistance() {}

    public SupportResistance(String id, double price, String type, int touches, double strength, long firstTouchTime, long lastTouchTime) {
        this.id = id;
        this.price = price;
        this.type = type;
        this.touches = touches;
        this.strength = strength;
        this.firstTouchTime = firstTouchTime;
        this.lastTouchTime = lastTouchTime;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getTouches() { return touches; }
    public void setTouches(int touches) { this.touches = touches; }

    public double getStrength() { return strength; }
    public void setStrength(double strength) { this.strength = strength; }

    public long getFirstTouchTime() { return firstTouchTime; }
    public void setFirstTouchTime(long firstTouchTime) { this.firstTouchTime = firstTouchTime; }

    public long getLastTouchTime() { return lastTouchTime; }
    public void setLastTouchTime(long lastTouchTime) { this.lastTouchTime = lastTouchTime; }
}

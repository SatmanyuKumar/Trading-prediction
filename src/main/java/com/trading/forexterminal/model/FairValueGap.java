package com.trading.forexterminal.model;

public class FairValueGap {
    private String id;
    private String type; // BULLISH or BEARISH
    private double top;
    private double bottom;
    private double consequentEncroachment; // 50% midpoint
    private long startTime;
    private long endTime;
    private int candleIndex;
    private boolean mitigated;

    public FairValueGap() {}

    public FairValueGap(String id, String type, double top, double bottom, long startTime, long endTime, int candleIndex) {
        this.id = id;
        this.type = type;
        this.top = top;
        this.bottom = bottom;
        this.consequentEncroachment = (top + bottom) / 2.0;
        this.startTime = startTime;
        this.endTime = endTime;
        this.candleIndex = candleIndex;
        this.mitigated = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getTop() { return top; }
    public void setTop(double top) { this.top = top; }

    public double getBottom() { return bottom; }
    public void setBottom(double bottom) { this.bottom = bottom; }

    public double getConsequentEncroachment() { return consequentEncroachment; }
    public void setConsequentEncroachment(double consequentEncroachment) { this.consequentEncroachment = consequentEncroachment; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }

    public int getCandleIndex() { return candleIndex; }
    public void setCandleIndex(int candleIndex) { this.candleIndex = candleIndex; }

    public boolean isMitigated() { return mitigated; }
    public void setMitigated(boolean mitigated) { this.mitigated = mitigated; }
}

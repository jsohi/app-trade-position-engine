package com.bofa.equity.position;

public class DefaultPositionData implements PositionData {


    private final int accountId;
    private final int securityId;

    private long buyQuantity;
    private long sellQuantity;
    private double buyPrice;
    private double sellPrice;

    public DefaultPositionData(final int accountId, final int securityId) {
        this.accountId = accountId;
        this.securityId = securityId;
    }

    public void update(final long quantity, final double price, final boolean buy) {
        if (buy) {
            buyQuantity += quantity;
            buyPrice += price;
        } else {
            sellQuantity += quantity;
            sellPrice += price;
        }
    }

    @Override
    public int accountId() {
        return accountId;
    }

    @Override
    public int securityId() {
        return securityId;
    }

    @Override
    public long buyQuantity() {
        return buyQuantity;
    }

    @Override
    public long sellQuantity() {
        return sellQuantity;
    }

    @Override
    public double buyPrice() {
        return buyPrice; // keeping original precision and only changing it for view/log purpose
    }

    @Override
    public double sellPrice() {
        return sellPrice; // keeping original precision and only changing it for view/log purpose
    }

    @Override
    public StringBuilder appendTo(final StringBuilder builder) {
        builder.append(DefaultPositionData.class.getSimpleName()).append('[')
                .append("accountId=").append(accountId())
                .append(", securityId=").append(securityId())
                .append(", buyQuantity=").append(buyQuantity())
                .append(", sellQuantity=").append(sellQuantity())
                .append(", netQuantity=").append(netQuantity())
                .append(", avgBuyPrice=");
        appendDouble2dp(builder, avgBuyPrice());
        builder.append(", avgSellPrice=");
        appendDouble2dp(builder, avgSellPrice());
        builder.append(']');
        return builder;
    }

    @Override
    public String toString() {
        return appendTo(new StringBuilder()).toString();
    }

    // GC-free 2-decimal-place double formatter: avoids StringBuilder.append(double) which
    // calls Double.toString() and allocates. Scales to integer arithmetic instead.
    private static void appendDouble2dp(final StringBuilder sb, final double value) {
        if (Double.isNaN(value)) {
            sb.append("NaN");
        } else if (Double.isInfinite(value)) {
            sb.append(value > 0 ? "Infinity" : "-Infinity");
        } else {
            final long scaled = Math.round(value * 100.0);
            final long intPart = scaled / 100;
            final long fracPart = Math.abs(scaled % 100);
            sb.append(intPart).append('.');
            if (fracPart < 10) sb.append('0'); // zero-pad single digit fraction
            sb.append(fracPart);
        }
    }
}

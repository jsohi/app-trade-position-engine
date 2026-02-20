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
                .append(", avgBuyPrice=").append(avgBuyPrice())
                .append(", avgSellPrice=").append(avgSellPrice())
                .append(']');
        return builder;
    }

    @Override
    public String toString() {
        return appendTo(new StringBuilder()).toString();
    }
}

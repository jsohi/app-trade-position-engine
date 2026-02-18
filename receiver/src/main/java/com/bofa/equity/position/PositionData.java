package com.bofa.equity.position;

import org.decimal4j.util.DoubleRounder;

public interface PositionData {

    int DEFAULT_DECIMAL_PRECISION = 2;

    int accountId();

    int securityId();

    long buyQuantity();

    long sellQuantity();

    // other option is to use big decimal but using SBE composite we can represent using mantissa and exponential values
    double buyPrice();

    // other option is to use big decimal but using SBE composite we can represent using mantissa and exponential values
    double sellPrice();

    default long netQuantity() {
        return buyQuantity() - sellQuantity();
    }

    default double avgBuyPrice() {
        // BigDecimal formatting can also be used, but will create garbage
        return DoubleRounder.round(buyQuantity() / buyPrice(), DEFAULT_DECIMAL_PRECISION);
    }

    default double avgSellPrice() {
        // BigDecimal formatting can also be used, but will create garbage
        return DoubleRounder.round(sellQuantity() / sellPrice(), DEFAULT_DECIMAL_PRECISION);
    }

    void update(long quantity, double price, boolean buy);

}

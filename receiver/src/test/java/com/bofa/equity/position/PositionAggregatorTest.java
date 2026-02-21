package com.bofa.equity.position;

import com.bofa.equity.cache.Cache;
import com.bofa.equity.sbe.SideType;
import com.bofa.equity.sbe.TradeDecoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.bofa.equity.util.TradeTestCodecs;
import static org.junit.jupiter.api.Assertions.*;

public class PositionAggregatorTest {
    private static final Logger logger = LogManager.getLogger(PositionAggregatorTest.class);

    private static boolean doubleEqual(final double d1, final double d2) {
        final double d = d1 / d2;
        return (Math.abs(d - 1.0) < 0.01);
    }

    private final Cache cache = Cache.defaultCache();
    private final PositionAggregator positionAggregator = new PositionAggregator(cache);
    private final TradeTestCodecs tradeTestCodecs = new TradeTestCodecs();

    @DisplayName("Single buy trade aggregation")
    @Test
    void singleTradeAggregation_Buy() {
        final String accountStrId = "Acc1";
        final String securityStrId = "0001.AX";

        final int accountId = cache.accountStrIds().get(accountStrId);
        final int securityId = cache.securityStrIds().get(securityStrId);

        final SideType sideType = SideType.B;
        final long quantity = 3;
        final double price = 4;

        // when
        final TradeDecoder tradeDecoder = tradeTestCodecs.encodeTrade(accountId, securityId, sideType, quantity, price);
        positionAggregator.aggregate(tradeDecoder, System.nanoTime());

        // then
        final PositionData actualPositionData = positionAggregator.positionData(accountId, securityId);

        assertAll("Aggregation position fields",
                () -> assertEquals(quantity, actualPositionData.buyQuantity(), "buyQuantity"),
                () -> assertEquals(0, actualPositionData.sellQuantity(), "sellQuantity"),
                () -> assertEquals(quantity, actualPositionData.netQuantity(), "netQuantity"),
                () -> assertEquals(quantity / price, actualPositionData.avgBuyPrice(), "avgBuyPrice"),
                () -> assertEquals(Double.NaN, actualPositionData.avgSellPrice(), "avgSellPrice")
        );
    }

    @DisplayName("Single sell trade aggregation")
    @Test
    void singleTradeAggregation_Sell() {
        final int accountId = 125;
        final int securityId = 2;
        final SideType sideType = SideType.S;
        final long quantity = 300000;
        final double price = 446465;

        // when
        final TradeDecoder tradeDecoder = tradeTestCodecs.encodeTrade(accountId, securityId, sideType, quantity, price);
        positionAggregator.aggregate(tradeDecoder, System.nanoTime());

        // then
        final PositionData actualPositionData = positionAggregator.positionData(accountId, securityId);

        assertAll("Aggregation position fields",
                () -> assertEquals(0, actualPositionData.buyQuantity(), "buyQuantity"),
                () -> assertEquals(quantity, actualPositionData.sellQuantity(), "sellQuantity"),
                () -> assertEquals(-quantity, actualPositionData.netQuantity(), "netQuantity"),
                () -> assertEquals(Double.NaN, actualPositionData.avgBuyPrice(), "avgBuyPrice"),
                () -> assertTrue(doubleEqual(quantity / price, actualPositionData.avgSellPrice()), "avgSellPrice")
        );
    }

    @DisplayName("Multi buy trade aggregation")
    @Test
    void multiTradeAggregation_Buy() {
        final String accountStrId = "Acc1";
        final String securityStrId = "0001.AX";

        final int accountId = cache.accountStrIds().get(accountStrId);
        final int securityId = cache.securityStrIds().get(securityStrId);

        final SideType sideType = SideType.B;
        final long quantity = 3;
        final double price = 4;

        // when
        // trade 1
        final TradeDecoder tradeDecoder1 = tradeTestCodecs.encodeTrade(accountId, securityId, sideType, quantity, price);
        positionAggregator.aggregate(tradeDecoder1, System.nanoTime());

        // trade 2
        final TradeDecoder tradeDecoder2 = tradeTestCodecs.encodeTrade(accountId, securityId, sideType, quantity, price);
        positionAggregator.aggregate(tradeDecoder2, System.nanoTime());

        // then
        final PositionData actualPositionData = positionAggregator.positionData(accountId, securityId);

        assertAll("Aggregation position fields",
                () -> assertEquals(quantity * 2, actualPositionData.buyQuantity(), "buyQuantity"),
                () -> assertEquals(0, actualPositionData.sellQuantity(), "sellQuantity"),
                () -> assertEquals(quantity * 2, actualPositionData.netQuantity(), "netQuantity"),
                () -> assertEquals(quantity / price, actualPositionData.avgBuyPrice(), "avgBuyPrice"),
                () -> assertEquals(Double.NaN, actualPositionData.avgSellPrice(), "avgSellPrice")
        );
    }

    @DisplayName("Multi sell trade aggregation")
    @Test
    void multiTradeAggregation_Sell() {
        final int accountId = 125;
        final int securityId = 2;
        final SideType sideType = SideType.S;
        final long quantity = 300000;
        final double price = 446465;

        // when
        // trade 1
        final TradeDecoder tradeDecoder = tradeTestCodecs.encodeTrade(accountId, securityId, sideType, quantity, price);
        positionAggregator.aggregate(tradeDecoder, System.nanoTime());

        // trade 2
        final TradeDecoder tradeDecoder2 = tradeTestCodecs.encodeTrade(accountId, securityId, sideType, quantity, price);
        positionAggregator.aggregate(tradeDecoder2, System.nanoTime());

        // then
        final PositionData actualPositionData = positionAggregator.positionData(accountId, securityId);

        assertAll("Aggregation position fields",
                () -> assertEquals(0, actualPositionData.buyQuantity(), "buyQuantity"),
                () -> assertEquals(quantity * 2, actualPositionData.sellQuantity(), "sellQuantity"),
                () -> assertEquals(-quantity * 2, actualPositionData.netQuantity(), "netQuantity"),
                () -> assertEquals(Double.NaN, actualPositionData.avgBuyPrice(), "avgBuyPrice"),
                () -> assertTrue(doubleEqual(quantity / price, actualPositionData.avgSellPrice()), "avgSellPrice")
        );
    }

    @DisplayName("Multi buy and sell trade aggregation")
    @Test
    void multiTradeAggregation_Buy_Sell() {
        final int accountId = 125;
        final int securityId = 2;

        // trade 1 details
        final SideType sideType1 = SideType.S;
        final long quantity1 = 300000;
        final double price1 = 446465;

        // trade 2 details
        final SideType sideType2 = SideType.B;
        final long quantity2 = 243230000;
        final double price2 = 2342365;

        // when
        // trade 1
        final TradeDecoder tradeDecoder = tradeTestCodecs.encodeTrade(accountId, securityId, sideType1, quantity1, price1);
        positionAggregator.aggregate(tradeDecoder, System.nanoTime());

        // trade 2
        final TradeDecoder tradeDecoder2 = tradeTestCodecs.encodeTrade(accountId, securityId, sideType2, quantity2, price2);
        positionAggregator.aggregate(tradeDecoder2, System.nanoTime());

        // then
        final PositionData actualPositionData = positionAggregator.positionData(accountId, securityId);

        assertAll("Aggregation position fields",
                () -> assertEquals(quantity2, actualPositionData.buyQuantity(), "buyQuantity"),
                () -> assertEquals(quantity1, actualPositionData.sellQuantity(), "sellQuantity"),
                () -> assertEquals(quantity2 - quantity1, actualPositionData.netQuantity(), "netQuantity"),
                () -> assertTrue(doubleEqual(quantity2 / price2, actualPositionData.avgBuyPrice()), "avgBuyPrice"),
                () -> assertTrue(doubleEqual(quantity1 / price1, actualPositionData.avgSellPrice()), "avgSellPrice")
        );
    }

}

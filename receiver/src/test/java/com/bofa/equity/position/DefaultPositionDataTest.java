package com.bofa.equity.position;

import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPositionDataTest {

    @Test
    @DisplayName("DefaultPositionData implements StringBuilderFormattable")
    void implementsStringBuilderFormattable() {
        assertInstanceOf(StringBuilderFormattable.class, new DefaultPositionData(1, 2));
    }

    @Test
    @DisplayName("formatTo delegates to appendTo, producing identical output")
    void formatTo_delegatesToAppendTo() {
        final DefaultPositionData data = new DefaultPositionData(3, 7);
        data.update(100, 1000.0, true);
        data.update(50, 500.0, false);

        final StringBuilder fromAppendTo = new StringBuilder();
        data.appendTo(fromAppendTo);

        final StringBuilder fromFormatTo = new StringBuilder();
        data.formatTo(fromFormatTo);

        assertEquals(fromAppendTo.toString(), fromFormatTo.toString());
    }

    @Test
    @DisplayName("appendTo formats all fields correctly for a buy position")
    void appendTo_buyPosition_formatsCorrectly() {
        final DefaultPositionData data = new DefaultPositionData(1, 2);
        data.update(200, 100.0, true); // avgBuyPrice = 200/100.0 = 2.0 → "2.00"

        final String result = data.appendTo(new StringBuilder()).toString();

        assertAll("appendTo buy position fields",
                () -> assertTrue(result.contains("accountId=1"), "accountId: " + result),
                () -> assertTrue(result.contains("securityId=2"), "securityId: " + result),
                () -> assertTrue(result.contains("buyQuantity=200"), "buyQuantity: " + result),
                () -> assertTrue(result.contains("sellQuantity=0"), "sellQuantity: " + result),
                () -> assertTrue(result.contains("netQuantity=200"), "netQuantity: " + result),
                () -> assertTrue(result.contains("avgBuyPrice=2.00"), "avgBuyPrice: " + result),
                () -> assertTrue(result.contains("avgSellPrice=NaN"), "avgSellPrice: " + result)
        );
    }

    @Test
    @DisplayName("appendTo formats all fields correctly for a sell position")
    void appendTo_sellPosition_formatsCorrectly() {
        final DefaultPositionData data = new DefaultPositionData(4, 8);
        data.update(50, 250.0, false); // avgSellPrice = 50/250.0 = 0.2 → "0.20"

        final String result = data.appendTo(new StringBuilder()).toString();

        assertAll("appendTo sell position fields",
                () -> assertTrue(result.contains("buyQuantity=0"), "buyQuantity: " + result),
                () -> assertTrue(result.contains("sellQuantity=50"), "sellQuantity: " + result),
                () -> assertTrue(result.contains("netQuantity=-50"), "netQuantity: " + result),
                () -> assertTrue(result.contains("avgBuyPrice=NaN"), "avgBuyPrice: " + result),
                () -> assertTrue(result.contains("avgSellPrice=0.20"), "avgSellPrice: " + result)
        );
    }

    @Test
    @DisplayName("appendDouble2dp zero-pads single-digit fractional part")
    void appendTo_zeroPadsSingleDigitFraction() {
        final DefaultPositionData data = new DefaultPositionData(1, 2);
        data.update(100, 1000.0, true); // avgBuyPrice = 100/1000.0 = 0.1 → "0.10"

        final String result = data.appendTo(new StringBuilder()).toString();

        assertTrue(result.contains("avgBuyPrice=0.10"), "should zero-pad fraction: " + result);
    }

    @Test
    @DisplayName("appendTo formats NaN for avgBuyPrice and avgSellPrice when no trades recorded")
    void appendTo_nanWhenNoTrades() {
        final DefaultPositionData data = new DefaultPositionData(5, 10);

        final String result = data.appendTo(new StringBuilder()).toString();

        assertAll("NaN prices when no trades",
                () -> assertTrue(result.contains("avgBuyPrice=NaN"), "avgBuyPrice NaN: " + result),
                () -> assertTrue(result.contains("avgSellPrice=NaN"), "avgSellPrice NaN: " + result)
        );
    }

    @Test
    @DisplayName("toString delegates to appendTo")
    void toString_delegatesToAppendTo() {
        final DefaultPositionData data = new DefaultPositionData(4, 8);
        data.update(50, 250.0, false);

        assertEquals(data.appendTo(new StringBuilder()).toString(), data.toString());
    }
}

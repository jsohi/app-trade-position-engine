package com.bofa.equity.trade;

import org.agrona.ExpandableArrayBuffer;
import org.apache.logging.log4j.util.StringBuilderFormattable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class TradeCodecFormattableTest {

    @Test
    @DisplayName("TradeCodec.encodedTradeLog is StringBuilderFormattable and produces output after encoding")
    void tradeCodec_encodedTradeLog_isStringBuilderFormattable() throws Exception {
        final TradeCodec codec = new TradeCodec();
        final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(512);
        codec.encodeTrade(buffer);

        final Field field = TradeCodec.class.getDeclaredField("encodedTradeLog");
        field.setAccessible(true);
        final Object log = field.get(codec);

        assertInstanceOf(StringBuilderFormattable.class, log);

        final StringBuilder sb = new StringBuilder();
        ((StringBuilderFormattable) log).formatTo(sb);
        assertFalse(sb.isEmpty(), "formatTo should produce non-empty output after encoding a trade");
    }

    @Test
    @DisplayName("AuditTradeCodec.encodedAuditLog is StringBuilderFormattable and produces output after encoding")
    void auditTradeCodec_encodedAuditLog_isStringBuilderFormattable() throws Exception {
        final AuditTradeCodec codec = new AuditTradeCodec();
        final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(512);
        codec.encodeAuditTrade(buffer);

        final Field field = AuditTradeCodec.class.getDeclaredField("encodedAuditLog");
        field.setAccessible(true);
        final Object log = field.get(codec);

        assertInstanceOf(StringBuilderFormattable.class, log);

        final StringBuilder sb = new StringBuilder();
        ((StringBuilderFormattable) log).formatTo(sb);
        assertFalse(sb.isEmpty(), "formatTo should produce non-empty output after encoding an audit trade");
    }
}

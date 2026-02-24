package com.bofa.equity.trade;

import com.bofa.equity.sbe.AuditTradeEncoder;
import com.bofa.equity.sbe.TradeEncoder;
import org.agrona.ExpandableArrayBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class TradeCodecFormattableTest {

    @Test
    @DisplayName("TradeCodec logging uses immutable toString snapshot after encoding")
    void tradeCodec_encoderToString_producesOutputAfterEncoding() throws Exception {
        final TradeCodec codec = new TradeCodec();
        final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(512);
        codec.encodeTrade(buffer);

        final Field field = TradeCodec.class.getDeclaredField("tradeEncoder");
        field.setAccessible(true);
        final TradeEncoder encoder = (TradeEncoder) field.get(codec);

        final String output = encoder.toString();
        assertNotNull(output);
        assertFalse(output.isEmpty(), "toString should produce non-empty output after encoding a trade");
    }

    @Test
    @DisplayName("AuditTradeCodec logging uses immutable toString snapshot after encoding")
    void auditTradeCodec_encoderToString_producesOutputAfterEncoding() throws Exception {
        final AuditTradeCodec codec = new AuditTradeCodec();
        final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer(512);
        codec.encodeAuditTrade(buffer);

        final Field field = AuditTradeCodec.class.getDeclaredField("auditEncoder");
        field.setAccessible(true);
        final AuditTradeEncoder encoder = (AuditTradeEncoder) field.get(codec);

        final String output = encoder.toString();
        assertNotNull(output);
        assertFalse(output.isEmpty(), "toString should produce non-empty output after encoding an audit trade");
    }
}

package com.bofa.equity.util;

import com.bofa.equity.sbe.MessageHeaderEncoder;
import com.bofa.equity.sbe.SideType;
import com.bofa.equity.sbe.TradeEncoder;
import io.aeron.Publication;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

public final class IntegrationTradePublisher {

    private static final MessageHeaderEncoder MESSAGE_HEADER_ENCODER = new MessageHeaderEncoder();
    private static final TradeEncoder TRADE_ENCODER = new TradeEncoder();
    private static final MutableDirectBuffer BUFFER = new ExpandableArrayBuffer();

    private IntegrationTradePublisher() {}

    public static void publish(final Publication publication,
                               final int accountId,
                               final int securityId,
                               final SideType side,
                               final long quantity,
                               final double price) {
        TRADE_ENCODER.wrapAndApplyHeader(BUFFER, 0, MESSAGE_HEADER_ENCODER)
                .referenceId("IT-" + accountId + "-" + securityId)
                .accountId((short) accountId)
                .securityId((short) securityId)
                .side(side)
                .quantity(quantity)
                .price(price)
                .timestampMillis(System.nanoTime())
                .description("integration-test");

        final int length = MessageHeaderEncoder.ENCODED_LENGTH + TRADE_ENCODER.encodedLength();

        while (publication.offer(BUFFER, 0, length) < 0) {
            Thread.onSpinWait();
        }
    }
}

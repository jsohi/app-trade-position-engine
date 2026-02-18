package com.bofa.equity.util;

import com.bofa.equity.sbe.*;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

public enum TradeTestCodecs {
    ;

    private static final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private static MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private static TradeDecoder tradeDecoder = new TradeDecoder();
    private static TradeEncoder tradeEncoder = new TradeEncoder();
    private static final MutableDirectBuffer directBuffer = new ExpandableArrayBuffer();


    public static TradeDecoder encodeTrade(final int accountId,
                                           final int securityId,
                                           final SideType sideType,
                                           final long quantity,
                                           final double price) {
        tradeEncoder.wrapAndApplyHeader(directBuffer, 0, messageHeaderEncoder)
                .referenceId("T-1")
                .accountId((short) accountId)
                .securityId((short) securityId)
                .side(sideType)
                .quantity(quantity)
                .price(price)
                .timestampMillis(System.nanoTime())
                .description("testing");

        messageHeaderDecoder.wrap(directBuffer, 0);
        tradeDecoder.wrapAndApplyHeader(directBuffer, 0, messageHeaderDecoder);

        return tradeDecoder;
    }

}

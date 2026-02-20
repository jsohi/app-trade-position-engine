package com.bofa.equity.util;

import com.bofa.equity.sbe.*;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

public class TradeTestCodecs {

    private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final TradeDecoder tradeDecoder = new TradeDecoder();
    private final TradeEncoder tradeEncoder = new TradeEncoder();
    private final MutableDirectBuffer directBuffer = new ExpandableArrayBuffer();

    public TradeDecoder encodeTrade(final int accountId,
                                    final int securityId,
                                    final SideType sideType,
                                    final long quantity,
                                    final double price) {
        tradeEncoder.wrapAndApplyHeader(directBuffer, 0, messageHeaderEncoder)
                .referenceId("T-1")
                .accountId((short) accountId)
                .securityId(securityId) // int — SecurityIdType is uint16
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

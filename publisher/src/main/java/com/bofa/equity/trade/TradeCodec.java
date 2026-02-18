package com.bofa.equity.trade;

import com.bofa.equity.sbe.MessageHeaderEncoder;
import com.bofa.equity.sbe.TradeEncoder;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.bofa.equity.trade.TradeEncoderHelper.*;

public enum TradeCodec {
    ;

    private static final Logger logger = LoggerFactory.getLogger(TradeCodec.class);

    private static final MessageHeaderEncoder MESSAGE_HEADER_ENCODER = new MessageHeaderEncoder();
    private static final TradeEncoder TRADE_ENCODER = new TradeEncoder();

    private static final StringBuilder referenceIdTemp = new StringBuilder(TradeEncoder.referenceIdLength());
    private static final byte[] descriptionBytes = new byte[2048]; // pre-allocated, zero garbage on hot path

    public static int encodeTrade(final MutableDirectBuffer directBuffer) {
        // validations of fields can be done here, before encoding fields
        TRADE_ENCODER.wrapAndApplyHeader(directBuffer, 0, MESSAGE_HEADER_ENCODER)
                .referenceId(nextReferenceId(referenceIdTemp))
                .accountId((short) randomInt(10)) // can be passed as variable
                .securityId((short) randomInt(2000)) // can be passed as variable
                .side(randomSide())
                .quantity(randomQuantity()) // can be short to meet current exercise requirement, but keeping long for scaling
                .price(randomPrice())
                .timestampMillis(System.nanoTime()) // nanos stored in uint64 field; field name is legacy
                .putDescription(descriptionBytes, 0, randomDescriptionBytes(descriptionBytes)); // zero allocation, variable length set last for SBE encoder

        logger.debug("Encoded {}", TRADE_ENCODER);
        return MessageHeaderEncoder.ENCODED_LENGTH + TRADE_ENCODER.encodedLength();
    }

}

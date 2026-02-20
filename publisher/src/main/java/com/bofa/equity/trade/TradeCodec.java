package com.bofa.equity.trade;

import com.bofa.equity.sbe.MessageHeaderEncoder;
import com.bofa.equity.sbe.TradeEncoder;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.bofa.equity.trade.TradeEncoderHelper.*;

public class TradeCodec {
    private static final Logger logger = LogManager.getLogger(TradeCodec.class);

    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final TradeEncoder tradeEncoder = new TradeEncoder();
    private final StringBuilder referenceIdTemp = new StringBuilder(TradeEncoder.referenceIdLength());
    private final byte[] descriptionBytes = new byte[2048]; // pre-allocated, zero garbage on hot path
    private final StringBuilder logBuffer = new StringBuilder(256); // pre-allocated, avoids String allocation at log call site

    public int encodeTrade(final MutableDirectBuffer directBuffer) {
        // validations of fields can be done here, before encoding fields
        tradeEncoder.wrapAndApplyHeader(directBuffer, 0, messageHeaderEncoder)
                .referenceId(nextReferenceId(referenceIdTemp))
                .accountId((short) (randomInt(10) + 1)) // can be passed as variable; +1 ensures range [1,10] per AccountIdType minValue
                .securityId(randomInt(2000) + 1) // can be passed as variable; int (not short) since SecurityIdType is uint16; +1 ensures range [1,2000]
                .side(randomSide())
                .quantity(randomQuantity()) // can be short to meet current exercise requirement, but keeping long for scaling
                .price(randomPrice())
                .timestampMillis(System.nanoTime()) // nanos stored in uint64 field; field name is legacy
                .putDescription(descriptionBytes, 0, randomDescriptionBytes(descriptionBytes)); // zero allocation, variable length set last for SBE encoder

        logBuffer.setLength(0);
        tradeEncoder.appendTo(logBuffer);
        logger.debug("Encoded {}", logBuffer);
        return MessageHeaderEncoder.ENCODED_LENGTH + tradeEncoder.encodedLength();
    }
}

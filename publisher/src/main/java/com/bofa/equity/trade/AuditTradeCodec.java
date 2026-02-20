package com.bofa.equity.trade;

import com.bofa.equity.sbe.AuditTradeEncoder;
import com.bofa.equity.sbe.MessageHeaderEncoder;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.bofa.equity.trade.TradeEncoderHelper.*;

public class AuditTradeCodec {
    private static final Logger logger = LoggerFactory.getLogger(AuditTradeCodec.class);

    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final AuditTradeEncoder auditEncoder = new AuditTradeEncoder();
    private final StringBuilder referenceIdTemp = new StringBuilder(AuditTradeEncoder.referenceIdLength());
    private final byte[] descriptionBytes = new byte[2048]; // pre-allocated, zero garbage on hot path

    public int encodeAuditTrade(final MutableDirectBuffer buffer) {
        auditEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder)
                .referenceId(nextReferenceId(referenceIdTemp))
                .accountId((short) (randomInt(10) + 1)) // +1 ensures range [1,10] per AccountIdType minValue
                .securityId(randomInt(2000) + 1) // int (not short) since SecurityIdType is uint16; +1 ensures range [1,2000]
                .putDescription(descriptionBytes, 0, randomDescriptionBytes(descriptionBytes));

        logger.debug("Encoded AuditTrade {}", auditEncoder);
        return MessageHeaderEncoder.ENCODED_LENGTH + auditEncoder.encodedLength();
    }
}

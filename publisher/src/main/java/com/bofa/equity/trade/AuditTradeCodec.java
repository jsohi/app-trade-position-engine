package com.bofa.equity.trade;

import com.bofa.equity.sbe.AuditTradeEncoder;
import com.bofa.equity.sbe.MessageHeaderEncoder;
import org.agrona.MutableDirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.bofa.equity.trade.TradeEncoderHelper.*;

public enum AuditTradeCodec {
    ;

    private static final Logger logger = LoggerFactory.getLogger(AuditTradeCodec.class);

    private static final MessageHeaderEncoder MESSAGE_HEADER_ENCODER = new MessageHeaderEncoder();
    private static final AuditTradeEncoder AUDIT_ENCODER = new AuditTradeEncoder();

    private static final StringBuilder referenceIdTemp = new StringBuilder(AuditTradeEncoder.referenceIdLength());
    private static final byte[] descriptionBytes = new byte[2048]; // pre-allocated, zero garbage on hot path

    public static int encodeAuditTrade(final MutableDirectBuffer buffer) {
        AUDIT_ENCODER.wrapAndApplyHeader(buffer, 0, MESSAGE_HEADER_ENCODER)
                .referenceId(nextReferenceId(referenceIdTemp))
                .accountId((short) randomInt(10))
                .securityId((short) randomInt(2000))
                .putDescription(descriptionBytes, 0, randomDescriptionBytes(descriptionBytes));

        logger.debug("Encoded AuditTrade {}", AUDIT_ENCODER);
        return MessageHeaderEncoder.ENCODED_LENGTH + AUDIT_ENCODER.encodedLength();
    }

}

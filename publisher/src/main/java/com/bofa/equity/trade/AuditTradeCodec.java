package com.bofa.equity.trade;

import com.bofa.equity.sbe.AuditTradeEncoder;
import com.bofa.equity.sbe.MessageHeaderEncoder;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.StringBuilderFormattable;

import static com.bofa.equity.trade.TradeEncoderHelper.*;

public class AuditTradeCodec {
    private static final Logger logger = LogManager.getLogger(AuditTradeCodec.class);

    private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    private final AuditTradeEncoder auditEncoder = new AuditTradeEncoder();
    private final StringBuilder referenceIdTemp = new StringBuilder(AuditTradeEncoder.referenceIdLength());
    private final byte[] descriptionBytes = new byte[2048]; // pre-allocated, zero garbage on hot path
    // Pre-allocated formattable: Log4j2 calls formatTo() directly on its own internal reused buffer —
    // no StringBuilder or String allocation at the log call site.
    private final StringBuilderFormattable encodedAuditLog = sb -> auditEncoder.appendTo(sb);

    public int encodeAuditTrade(final MutableDirectBuffer buffer) {
        auditEncoder.wrapAndApplyHeader(buffer, 0, messageHeaderEncoder)
                .referenceId(nextReferenceId(referenceIdTemp))
                .accountId((short) (randomInt(10) + 1)) // +1 ensures range [1,10] per AccountIdType minValue
                .securityId(randomInt(2000) + 1) // int (not short) since SecurityIdType is uint16; +1 ensures range [1,2000]
                .putDescription(descriptionBytes, 0, randomDescriptionBytes(descriptionBytes));

        logger.debug("Encoded AuditTrade {}", encodedAuditLog);
        return MessageHeaderEncoder.ENCODED_LENGTH + auditEncoder.encodedLength();
    }
}

package com.bofa.equity.rfq;

import com.bofa.equity.sbe.*;
import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Decodes inbound SBE event messages by templateId and dispatches to
 * the {@link RfqProjection} for read-side cache updates.
 */
public class RfqEventHandler {
    private static final Logger logger = LogManager.getLogger(RfqEventHandler.class);

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final QuoteRequestedEvtDecoder requestedEvtDecoder = new QuoteRequestedEvtDecoder();
    private final QuoteProposedEvtDecoder proposedEvtDecoder = new QuoteProposedEvtDecoder();
    private final QuoteAcceptedEvtDecoder acceptedEvtDecoder = new QuoteAcceptedEvtDecoder();
    private final QuoteRejectedEvtDecoder rejectedEvtDecoder = new QuoteRejectedEvtDecoder();
    private final QuoteCancelledEvtDecoder cancelledEvtDecoder = new QuoteCancelledEvtDecoder();

    private final RfqProjection projection;

    public RfqEventHandler(final RfqProjection projection) {
        this.projection = projection;
    }

    public boolean handle(final DirectBuffer buffer, final int offset, final int length) {
        if (length < MessageHeaderDecoder.ENCODED_LENGTH) {
            logger.warn("Fragment too short for header: {} bytes", length);
            return false;
        }

        headerDecoder.wrap(buffer, offset);

        if (headerDecoder.schemaId() != RfqConstants.SCHEMA_ID) {
            return false;
        }

        final int requiredLength = MessageHeaderDecoder.ENCODED_LENGTH + headerDecoder.blockLength();
        if (length < requiredLength) {
            logger.warn("Fragment too short for message block: {} bytes, expected at least {}", length, requiredLength);
            return false;
        }

        final int templateId = headerDecoder.templateId();

        switch (templateId) {
            case QuoteRequestedEvtEncoder.TEMPLATE_ID -> {
                requestedEvtDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
                projection.onQuoteRequested(requestedEvtDecoder);
            }
            case QuoteProposedEvtEncoder.TEMPLATE_ID -> {
                proposedEvtDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
                projection.onQuoteProposed(proposedEvtDecoder);
            }
            case QuoteAcceptedEvtEncoder.TEMPLATE_ID -> {
                acceptedEvtDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
                projection.onQuoteAccepted(acceptedEvtDecoder);
            }
            case QuoteRejectedEvtEncoder.TEMPLATE_ID -> {
                rejectedEvtDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
                projection.onQuoteRejected(rejectedEvtDecoder);
            }
            case QuoteCancelledEvtEncoder.TEMPLATE_ID -> {
                cancelledEvtDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
                projection.onQuoteCancelled(cancelledEvtDecoder);
            }
            default -> {
                logger.warn("Unknown RFQ event templateId: {}", templateId);
                return false;
            }
        }
        return true;
    }
}

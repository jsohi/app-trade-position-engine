package com.bofa.equity.command;

import com.bofa.equity.event.RfqEventPublisher;
import com.bofa.equity.rfq.RfqAggregate;
import com.bofa.equity.rfq.RfqConstants;
import com.bofa.equity.sbe.*;
import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Decodes inbound SBE command messages by templateId and dispatches to
 * the {@link RfqAggregate} for validation, then publishes events via
 * {@link RfqEventPublisher}.
 * <p>
 * Follows the same header-decode + templateId dispatch pattern as {@code TradeHandler}.
 */
public class RfqCommandHandler {
    private static final Logger logger = LogManager.getLogger(RfqCommandHandler.class);

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final RequestQuoteCmdDecoder requestQuoteDecoder = new RequestQuoteCmdDecoder();
    private final ProposeQuoteCmdDecoder proposeQuoteDecoder = new ProposeQuoteCmdDecoder();
    private final AcceptQuoteCmdDecoder acceptQuoteDecoder = new AcceptQuoteCmdDecoder();
    private final RejectQuoteCmdDecoder rejectQuoteDecoder = new RejectQuoteCmdDecoder();
    private final CancelQuoteCmdDecoder cancelQuoteDecoder = new CancelQuoteCmdDecoder();

    private final RfqAggregate aggregate;
    private final RfqEventPublisher eventPublisher;
    private long sequenceNumber = 0;

    public RfqCommandHandler(final RfqAggregate aggregate,
                             final RfqEventPublisher eventPublisher) {
        this.aggregate = aggregate;
        this.eventPublisher = eventPublisher;
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
            case RequestQuoteCmdEncoder.TEMPLATE_ID -> handleRequestQuote(buffer, offset);
            case ProposeQuoteCmdEncoder.TEMPLATE_ID -> handleProposeQuote(buffer, offset);
            case AcceptQuoteCmdEncoder.TEMPLATE_ID -> handleAcceptQuote(buffer, offset);
            case RejectQuoteCmdEncoder.TEMPLATE_ID -> handleRejectQuote(buffer, offset);
            case CancelQuoteCmdEncoder.TEMPLATE_ID -> handleCancelQuote(buffer, offset);
            default -> {
                logger.warn("Unknown RFQ command templateId: {}", templateId);
                return false;
            }
        }
        return true;
    }

    private void handleRequestQuote(final DirectBuffer buffer, final int offset) {
        requestQuoteDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        if (aggregate.validateRequestQuote(requestQuoteDecoder)) {
            final long seqNum = ++sequenceNumber;
            aggregate.applyQuoteRequested(requestQuoteDecoder);
            eventPublisher.publishQuoteRequested(seqNum, requestQuoteDecoder);
            logger.debug("QuoteRequested seq={} quoteReqId={}", seqNum, requestQuoteDecoder.quoteReqId());
        }
    }

    private void handleProposeQuote(final DirectBuffer buffer, final int offset) {
        proposeQuoteDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        if (aggregate.validateProposeQuote(proposeQuoteDecoder)) {
            final long seqNum = ++sequenceNumber;
            aggregate.applyQuoteProposed(proposeQuoteDecoder);
            eventPublisher.publishQuoteProposed(seqNum, proposeQuoteDecoder);
            logger.debug("QuoteProposed seq={} quoteReqId={}", seqNum, proposeQuoteDecoder.quoteReqId());
        }
    }

    private void handleAcceptQuote(final DirectBuffer buffer, final int offset) {
        acceptQuoteDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        if (aggregate.validateAcceptQuote(acceptQuoteDecoder)) {
            final long seqNum = ++sequenceNumber;
            aggregate.applyQuoteAccepted(acceptQuoteDecoder);
            eventPublisher.publishQuoteAccepted(seqNum, acceptQuoteDecoder);
            logger.debug("QuoteAccepted seq={} quoteReqId={}", seqNum, acceptQuoteDecoder.quoteReqId());
        }
    }

    private void handleRejectQuote(final DirectBuffer buffer, final int offset) {
        rejectQuoteDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        if (aggregate.validateRejectQuote(rejectQuoteDecoder)) {
            final long seqNum = ++sequenceNumber;
            aggregate.applyQuoteRejected(rejectQuoteDecoder);
            eventPublisher.publishQuoteRejected(seqNum, rejectQuoteDecoder);
            logger.debug("QuoteRejected seq={} quoteReqId={}", seqNum, rejectQuoteDecoder.quoteReqId());
        }
    }

    private void handleCancelQuote(final DirectBuffer buffer, final int offset) {
        cancelQuoteDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);
        if (aggregate.validateCancelQuote(cancelQuoteDecoder)) {
            final long seqNum = ++sequenceNumber;
            aggregate.applyQuoteCancelled(cancelQuoteDecoder);
            eventPublisher.publishQuoteCancelled(seqNum, cancelQuoteDecoder);
            logger.debug("QuoteCancelled seq={} quoteReqId={}", seqNum, cancelQuoteDecoder.quoteReqId());
        }
    }

    public long sequenceNumber() {
        return sequenceNumber;
    }
}

package com.bofa.equity.event;

import com.bofa.equity.rfq.RfqConstants;
import com.bofa.equity.sbe.*;
import io.aeron.Publication;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Encodes RFQ events via SBE and publishes them on the event Aeron publication (stream 21).
 * Flyweight encoders are reused — no allocation on the hot path.
 * <p>
 * Busy-spins on back-pressure to guarantee event delivery (events are facts).
 */
public class RfqEventPublisher {
    private static final Logger logger = LogManager.getLogger(RfqEventPublisher.class);

    private final Publication publication;
    private final MutableDirectBuffer buffer = new ExpandableArrayBuffer();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    private final QuoteRequestedEvtEncoder requestedEvtEncoder = new QuoteRequestedEvtEncoder();
    private final QuoteProposedEvtEncoder proposedEvtEncoder = new QuoteProposedEvtEncoder();
    private final QuoteAcceptedEvtEncoder acceptedEvtEncoder = new QuoteAcceptedEvtEncoder();
    private final QuoteRejectedEvtEncoder rejectedEvtEncoder = new QuoteRejectedEvtEncoder();
    private final QuoteCancelledEvtEncoder cancelledEvtEncoder = new QuoteCancelledEvtEncoder();

    public RfqEventPublisher(final Publication publication) {
        this.publication = publication;
    }

    public void publishQuoteRequested(final long seqNum,
                                      final RequestQuoteCmdDecoder cmd) {
        requestedEvtEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .sequenceNumber(seqNum)
                .quoteReqId(cmd.quoteReqId())
                .symbol(cmd.symbol())
                .side(cmd.side())
                .quantity(cmd.quantity())
                .partyId(cmd.partyId())
                .state(RfqStateType.REQUESTED)
                .timestampNanos(cmd.timestampNanos());

        offer(MessageHeaderEncoder.ENCODED_LENGTH + requestedEvtEncoder.encodedLength());
    }

    public void publishQuoteProposed(final long seqNum,
                                     final ProposeQuoteCmdDecoder cmd) {
        proposedEvtEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .sequenceNumber(seqNum)
                .quoteReqId(cmd.quoteReqId())
                .quoteId(cmd.quoteId())
                .symbol(cmd.symbol())
                .bidPx(cmd.bidPx())
                .offerPx(cmd.offerPx())
                .bidSize(cmd.bidSize())
                .offerSize(cmd.offerSize())
                .validUntilTime(cmd.validUntilTime())
                .partyId(cmd.partyId())
                .state(RfqStateType.QUOTED)
                .timestampNanos(cmd.timestampNanos());

        offer(MessageHeaderEncoder.ENCODED_LENGTH + proposedEvtEncoder.encodedLength());
    }

    public void publishQuoteAccepted(final long seqNum,
                                     final AcceptQuoteCmdDecoder cmd) {
        acceptedEvtEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .sequenceNumber(seqNum)
                .quoteReqId(cmd.quoteReqId())
                .quoteId(cmd.quoteId())
                .partyId(cmd.partyId())
                .state(RfqStateType.ACCEPTED)
                .timestampNanos(cmd.timestampNanos());

        offer(MessageHeaderEncoder.ENCODED_LENGTH + acceptedEvtEncoder.encodedLength());
    }

    public void publishQuoteRejected(final long seqNum,
                                     final RejectQuoteCmdDecoder cmd) {
        rejectedEvtEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .sequenceNumber(seqNum)
                .quoteReqId(cmd.quoteReqId())
                .quoteId(cmd.quoteId())
                .partyId(cmd.partyId())
                .state(RfqStateType.REJECTED)
                .timestampNanos(cmd.timestampNanos());

        String reason = cmd.reason();
        if (reason == null) {
            reason = "";
        } else if (reason.length() > RfqConstants.MAX_REASON_LENGTH) {
            reason = reason.substring(0, RfqConstants.MAX_REASON_LENGTH);
        }
        rejectedEvtEncoder.reason(reason);

        offer(MessageHeaderEncoder.ENCODED_LENGTH + rejectedEvtEncoder.encodedLength());
    }

    public void publishQuoteCancelled(final long seqNum,
                                      final CancelQuoteCmdDecoder cmd) {
        cancelledEvtEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .sequenceNumber(seqNum)
                .quoteReqId(cmd.quoteReqId())
                .partyId(cmd.partyId())
                .state(RfqStateType.CANCELLED)
                .timestampNanos(cmd.timestampNanos());

        offer(MessageHeaderEncoder.ENCODED_LENGTH + cancelledEvtEncoder.encodedLength());
    }

    private void offer(final int length) {
        while (true) {
            final long result = publication.offer(buffer, 0, length);
            if (result > 0) {
                return;
            }
            if (result == Publication.CLOSED || result == Publication.MAX_POSITION_EXCEEDED) {
                logger.error("Event publication failed permanently: result={}", result);
                throw new IllegalStateException("Event publication closed or max position exceeded: " + result);
            }
            // BACK_PRESSURED or NOT_CONNECTED — busy-spin retry (events are facts, never drop)
        }
    }
}

package com.bofa.equity.rfq;

import com.bofa.equity.sbe.*;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RfqProjectionTest {

    private RfqProjection projection;
    private final MutableDirectBuffer buffer = new ExpandableArrayBuffer();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    @BeforeEach
    void setUp() {
        projection = new RfqProjection();
    }

    @Test
    void quoteRequested_createsEntry() {
        final var encoder = new QuoteRequestedEvtEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .sequenceNumber(1L)
                .quoteReqId("RFQ-001")
                .symbol("AAPL")
                .side(RfqSideType.BUY)
                .quantity(1000)
                .partyId("CLIENT-A")
                .state(RfqStateType.REQUESTED)
                .timestampNanos(System.nanoTime());

        headerDecoder.wrap(buffer, 0);
        final var decoder = new QuoteRequestedEvtDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, headerDecoder);

        projection.onQuoteRequested(decoder);

        final RfqCacheEntry entry = projection.getByQuoteReqId("RFQ-001");
        assertNotNull(entry);
        assertEquals(1L, entry.sequenceNumber());
        assertEquals(RfqStateType.REQUESTED, entry.state());
        assertEquals("AAPL", entry.symbol());
        assertEquals(RfqSideType.BUY, entry.side());
        assertEquals(1000, entry.quantity());
    }

    @Test
    void quoteProposed_updatesEntryAndIndexesByQuoteId() {
        // First create via requested
        encodeAndProjectRequested("RFQ-002", "AAPL", 1L);

        // Now propose
        final var encoder = new QuoteProposedEvtEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .sequenceNumber(2L)
                .quoteReqId("RFQ-002")
                .quoteId("Q-002")
                .symbol("AAPL")
                .bidPx(150.0)
                .offerPx(151.0)
                .bidSize(500)
                .offerSize(500)
                .validUntilTime(999L)
                .partyId("DEALER-X")
                .state(RfqStateType.QUOTED)
                .timestampNanos(System.nanoTime());

        headerDecoder.wrap(buffer, 0);
        final var decoder = new QuoteProposedEvtDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, headerDecoder);

        projection.onQuoteProposed(decoder);

        final RfqCacheEntry entry = projection.getByQuoteReqId("RFQ-002");
        assertEquals(RfqStateType.QUOTED, entry.state());
        assertEquals(150.0, entry.bidPx());
        assertEquals(151.0, entry.offerPx());

        // Also indexed by quoteId
        assertSame(entry, projection.getByQuoteId("Q-002"));
    }

    @Test
    void quoteAccepted_updatesState() {
        encodeAndProjectRequested("RFQ-003", "GOOG", 1L);
        encodeAndProjectProposed("RFQ-003", "Q-003", 2L);

        final var encoder = new QuoteAcceptedEvtEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .sequenceNumber(3L)
                .quoteReqId("RFQ-003")
                .quoteId("Q-003")
                .partyId("CLIENT-A")
                .state(RfqStateType.ACCEPTED)
                .timestampNanos(System.nanoTime());

        headerDecoder.wrap(buffer, 0);
        final var decoder = new QuoteAcceptedEvtDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, headerDecoder);

        projection.onQuoteAccepted(decoder);

        assertEquals(RfqStateType.ACCEPTED, projection.getByQuoteReqId("RFQ-003").state());
        assertEquals(3L, projection.getByQuoteReqId("RFQ-003").sequenceNumber());
    }

    @Test
    void quoteCancelled_updatesState() {
        encodeAndProjectRequested("RFQ-004", "MSFT", 1L);

        final var encoder = new QuoteCancelledEvtEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .sequenceNumber(2L)
                .quoteReqId("RFQ-004")
                .partyId("CLIENT-A")
                .state(RfqStateType.CANCELLED)
                .timestampNanos(System.nanoTime());

        headerDecoder.wrap(buffer, 0);
        final var decoder = new QuoteCancelledEvtDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, headerDecoder);

        projection.onQuoteCancelled(decoder);

        assertEquals(RfqStateType.CANCELLED, projection.getByQuoteReqId("RFQ-004").state());
    }

    private void encodeAndProjectRequested(final String quoteReqId, final String symbol, final long seqNum) {
        final var encoder = new QuoteRequestedEvtEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .sequenceNumber(seqNum)
                .quoteReqId(quoteReqId)
                .symbol(symbol)
                .side(RfqSideType.BUY)
                .quantity(1000)
                .partyId("CLIENT-A")
                .state(RfqStateType.REQUESTED)
                .timestampNanos(System.nanoTime());

        headerDecoder.wrap(buffer, 0);
        final var decoder = new QuoteRequestedEvtDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        projection.onQuoteRequested(decoder);
    }

    private void encodeAndProjectProposed(final String quoteReqId, final String quoteId, final long seqNum) {
        final var encoder = new QuoteProposedEvtEncoder();
        encoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .sequenceNumber(seqNum)
                .quoteReqId(quoteReqId)
                .quoteId(quoteId)
                .symbol("AAPL")
                .bidPx(150.0)
                .offerPx(151.0)
                .bidSize(500)
                .offerSize(500)
                .validUntilTime(999L)
                .partyId("DEALER-X")
                .state(RfqStateType.QUOTED)
                .timestampNanos(System.nanoTime());

        headerDecoder.wrap(buffer, 0);
        final var decoder = new QuoteProposedEvtDecoder();
        decoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        projection.onQuoteProposed(decoder);
    }
}

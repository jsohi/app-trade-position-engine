package com.bofa.equity.rfq;

import com.bofa.equity.sbe.RfqSideType;
import com.bofa.equity.sbe.RfqStateType;
import com.bofa.equity.util.RfqTestCodecs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RfqAggregateTest {

    private RfqAggregate aggregate;
    private RfqTestCodecs codecs;

    @BeforeEach
    void setUp() {
        aggregate = new RfqAggregate();
        codecs = new RfqTestCodecs();
    }

    @Test
    void requestQuote_validTransition() {
        codecs.encodeRequestQuoteCmd("RFQ-001", "AAPL", RfqSideType.BUY, 1000, "CLIENT-A");
        final var cmd = codecs.decodeRequestQuoteCmd();

        assertTrue(aggregate.validateRequestQuote(cmd));
        aggregate.applyQuoteRequested(cmd);

        final RfqState state = aggregate.getState("RFQ-001");
        assertNotNull(state);
        assertEquals(RfqStateType.REQUESTED, state.currentState());
        assertEquals("AAPL", state.symbol().trim());
        assertEquals(RfqSideType.BUY, state.side());
        assertEquals(1000, state.quantity());
    }

    @Test
    void duplicateRequestQuote_rejected() {
        codecs.encodeRequestQuoteCmd("RFQ-001", "AAPL", RfqSideType.BUY, 1000, "CLIENT-A");
        final var cmd = codecs.decodeRequestQuoteCmd();
        aggregate.validateRequestQuote(cmd);
        aggregate.applyQuoteRequested(cmd);

        // Encode same quoteReqId again
        codecs.encodeRequestQuoteCmd("RFQ-001", "GOOG", RfqSideType.SELL, 500, "CLIENT-B");
        final var cmd2 = codecs.decodeRequestQuoteCmd();
        assertFalse(aggregate.validateRequestQuote(cmd2));
    }

    @Test
    void proposeQuote_validTransition_REQUESTED_to_QUOTED() {
        codecs.encodeRequestQuoteCmd("RFQ-002", "AAPL", RfqSideType.BUY, 1000, "CLIENT-A");
        var reqCmd = codecs.decodeRequestQuoteCmd();
        aggregate.validateRequestQuote(reqCmd);
        aggregate.applyQuoteRequested(reqCmd);

        codecs.encodeProposeQuoteCmd("RFQ-002", "Q-001", "AAPL", 150.0, 151.0, 500, 500, 999L, "DEALER-X");
        var propCmd = codecs.decodeProposeQuoteCmd();
        assertTrue(aggregate.validateProposeQuote(propCmd));
        aggregate.applyQuoteProposed(propCmd);

        final RfqState state = aggregate.getState("RFQ-002");
        assertEquals(RfqStateType.QUOTED, state.currentState());
        assertEquals("Q-001", state.quoteId().trim());
        assertEquals(150.0, state.bidPx());
        assertEquals(151.0, state.offerPx());
    }

    @Test
    void proposeQuote_onNonExistent_rejected() {
        codecs.encodeProposeQuoteCmd("RFQ-NONE", "Q-001", "AAPL", 150.0, 151.0, 500, 500, 999L, "DEALER-X");
        var cmd = codecs.decodeProposeQuoteCmd();
        assertFalse(aggregate.validateProposeQuote(cmd));
    }

    @Test
    void acceptQuote_validTransition_QUOTED_to_ACCEPTED() {
        setupQuotedState("RFQ-003", "Q-003");

        codecs.encodeAcceptQuoteCmd("RFQ-003", "Q-003", "CLIENT-A");
        final var acceptDecoder = new com.bofa.equity.sbe.AcceptQuoteCmdDecoder();
        final var headerDecoder = new com.bofa.equity.sbe.MessageHeaderDecoder();
        headerDecoder.wrap(codecs.buffer(), 0);
        acceptDecoder.wrapAndApplyHeader(codecs.buffer(), 0, headerDecoder);

        assertTrue(aggregate.validateAcceptQuote(acceptDecoder));
        aggregate.applyQuoteAccepted(acceptDecoder);

        // Terminal entries are evicted from the aggregate
        assertNull(aggregate.getState("RFQ-003"));
    }

    @Test
    void acceptQuote_onREQUESTED_rejected() {
        codecs.encodeRequestQuoteCmd("RFQ-004", "AAPL", RfqSideType.BUY, 1000, "CLIENT-A");
        var reqCmd = codecs.decodeRequestQuoteCmd();
        aggregate.validateRequestQuote(reqCmd);
        aggregate.applyQuoteRequested(reqCmd);

        codecs.encodeAcceptQuoteCmd("RFQ-004", "Q-004", "CLIENT-A");
        final var acceptDecoder = new com.bofa.equity.sbe.AcceptQuoteCmdDecoder();
        final var headerDecoder = new com.bofa.equity.sbe.MessageHeaderDecoder();
        headerDecoder.wrap(codecs.buffer(), 0);
        acceptDecoder.wrapAndApplyHeader(codecs.buffer(), 0, headerDecoder);

        assertFalse(aggregate.validateAcceptQuote(acceptDecoder));
    }

    @Test
    void acceptQuote_byUnauthorizedParty_rejected() {
        setupQuotedState("RFQ-008", "Q-008");

        // Try to accept with a different party (not the requestor)
        codecs.encodeAcceptQuoteCmd("RFQ-008", "Q-008", "INTRUDER");
        final var acceptDecoder = new com.bofa.equity.sbe.AcceptQuoteCmdDecoder();
        final var headerDecoder = new com.bofa.equity.sbe.MessageHeaderDecoder();
        headerDecoder.wrap(codecs.buffer(), 0);
        acceptDecoder.wrapAndApplyHeader(codecs.buffer(), 0, headerDecoder);

        assertFalse(aggregate.validateAcceptQuote(acceptDecoder));
        // RFQ should still be in QUOTED state
        assertEquals(RfqStateType.QUOTED, aggregate.getState("RFQ-008").currentState());
    }

    @Test
    void rejectQuote_validTransition_QUOTED_to_REJECTED() {
        setupQuotedState("RFQ-005", "Q-005");

        codecs.encodeRejectQuoteCmd("RFQ-005", "Q-005", "CLIENT-A", "Price too high");
        final var rejectDecoder = new com.bofa.equity.sbe.RejectQuoteCmdDecoder();
        final var headerDecoder = new com.bofa.equity.sbe.MessageHeaderDecoder();
        headerDecoder.wrap(codecs.buffer(), 0);
        rejectDecoder.wrapAndApplyHeader(codecs.buffer(), 0, headerDecoder);

        assertTrue(aggregate.validateRejectQuote(rejectDecoder));
        aggregate.applyQuoteRejected(rejectDecoder);

        // Terminal entries are evicted from the aggregate
        assertNull(aggregate.getState("RFQ-005"));
    }

    @Test
    void cancelQuote_fromREQUESTED_validTransition() {
        codecs.encodeRequestQuoteCmd("RFQ-006", "AAPL", RfqSideType.BUY, 1000, "CLIENT-A");
        var reqCmd = codecs.decodeRequestQuoteCmd();
        aggregate.validateRequestQuote(reqCmd);
        aggregate.applyQuoteRequested(reqCmd);

        codecs.encodeCancelQuoteCmd("RFQ-006", "CLIENT-A");
        final var cancelDecoder = new com.bofa.equity.sbe.CancelQuoteCmdDecoder();
        final var headerDecoder = new com.bofa.equity.sbe.MessageHeaderDecoder();
        headerDecoder.wrap(codecs.buffer(), 0);
        cancelDecoder.wrapAndApplyHeader(codecs.buffer(), 0, headerDecoder);

        assertTrue(aggregate.validateCancelQuote(cancelDecoder));
        aggregate.applyQuoteCancelled(cancelDecoder);

        // Terminal entries are evicted from the aggregate
        assertNull(aggregate.getState("RFQ-006"));
    }

    @Test
    void cancelQuote_onTerminalState_rejected() {
        setupQuotedState("RFQ-007", "Q-007");

        // Accept it first (evicts from aggregate)
        codecs.encodeAcceptQuoteCmd("RFQ-007", "Q-007", "CLIENT-A");
        final var acceptDecoder = new com.bofa.equity.sbe.AcceptQuoteCmdDecoder();
        final var hd1 = new com.bofa.equity.sbe.MessageHeaderDecoder();
        hd1.wrap(codecs.buffer(), 0);
        acceptDecoder.wrapAndApplyHeader(codecs.buffer(), 0, hd1);
        aggregate.applyQuoteAccepted(acceptDecoder);

        // Now try to cancel — should fail because entry was evicted (unknown quoteReqId)
        codecs.encodeCancelQuoteCmd("RFQ-007", "CLIENT-A");
        final var cancelDecoder = new com.bofa.equity.sbe.CancelQuoteCmdDecoder();
        final var hd2 = new com.bofa.equity.sbe.MessageHeaderDecoder();
        hd2.wrap(codecs.buffer(), 0);
        cancelDecoder.wrapAndApplyHeader(codecs.buffer(), 0, hd2);

        assertFalse(aggregate.validateCancelQuote(cancelDecoder));
    }

    @Test
    void cancelQuote_byUnauthorizedParty_rejected() {
        codecs.encodeRequestQuoteCmd("RFQ-009", "AAPL", RfqSideType.BUY, 1000, "CLIENT-A");
        var reqCmd = codecs.decodeRequestQuoteCmd();
        aggregate.validateRequestQuote(reqCmd);
        aggregate.applyQuoteRequested(reqCmd);

        codecs.encodeCancelQuoteCmd("RFQ-009", "INTRUDER");
        final var cancelDecoder = new com.bofa.equity.sbe.CancelQuoteCmdDecoder();
        final var headerDecoder = new com.bofa.equity.sbe.MessageHeaderDecoder();
        headerDecoder.wrap(codecs.buffer(), 0);
        cancelDecoder.wrapAndApplyHeader(codecs.buffer(), 0, headerDecoder);

        assertFalse(aggregate.validateCancelQuote(cancelDecoder));
    }

    private void setupQuotedState(final String quoteReqId, final String quoteId) {
        codecs.encodeRequestQuoteCmd(quoteReqId, "AAPL", RfqSideType.BUY, 1000, "CLIENT-A");
        var reqCmd = codecs.decodeRequestQuoteCmd();
        aggregate.validateRequestQuote(reqCmd);
        aggregate.applyQuoteRequested(reqCmd);

        codecs.encodeProposeQuoteCmd(quoteReqId, quoteId, "AAPL", 150.0, 151.0, 500, 500, 999L, "DEALER-X");
        var propCmd = codecs.decodeProposeQuoteCmd();
        aggregate.validateProposeQuote(propCmd);
        aggregate.applyQuoteProposed(propCmd);
    }
}

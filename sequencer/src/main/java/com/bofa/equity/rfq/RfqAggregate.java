package com.bofa.equity.rfq;

import com.bofa.equity.sbe.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * RFQ state machine and aggregate root. Validates commands against current state
 * and applies events to mutate in-memory state.
 * <p>
 * Single-threaded — only the sequencer thread calls validate/apply.
 */
public class RfqAggregate {
    private static final Logger logger = LogManager.getLogger(RfqAggregate.class);

    private final Map<String, RfqState> rfqStates = new HashMap<>();

    // ---- Validate commands ----

    public boolean validateRequestQuote(final RequestQuoteCmdDecoder cmd) {
        final String quoteReqId = cmd.quoteReqId();
        if (rfqStates.containsKey(quoteReqId)) {
            logger.warn("Duplicate quoteReqId: {}", quoteReqId);
            return false;
        }
        return true;
    }

    public boolean validateProposeQuote(final ProposeQuoteCmdDecoder cmd) {
        final String quoteReqId = cmd.quoteReqId();
        final RfqState state = rfqStates.get(quoteReqId);
        if (state == null) {
            logger.warn("ProposeQuote for unknown quoteReqId: {}", quoteReqId);
            return false;
        }
        if (state.currentState() != RfqStateType.REQUESTED) {
            logger.warn("ProposeQuote invalid transition from {} for quoteReqId: {}",
                    state.currentState(), quoteReqId);
            return false;
        }
        return true;
    }

    public boolean validateAcceptQuote(final AcceptQuoteCmdDecoder cmd) {
        final String quoteReqId = cmd.quoteReqId();
        final RfqState state = rfqStates.get(quoteReqId);
        if (state == null) {
            logger.warn("AcceptQuote for unknown quoteReqId: {}", quoteReqId);
            return false;
        }
        if (!trimmedEquals(state.requestorPartyId(), cmd.partyId())) {
            logger.warn("AcceptQuote by unauthorized party: {} for quoteReqId: {}",
                    cmd.partyId(), quoteReqId);
            return false;
        }
        if (state.currentState() != RfqStateType.QUOTED) {
            logger.warn("AcceptQuote invalid transition from {} for quoteReqId: {}",
                    state.currentState(), quoteReqId);
            return false;
        }
        return true;
    }

    public boolean validateRejectQuote(final RejectQuoteCmdDecoder cmd) {
        final String quoteReqId = cmd.quoteReqId();
        final RfqState state = rfqStates.get(quoteReqId);
        if (state == null) {
            logger.warn("RejectQuote for unknown quoteReqId: {}", quoteReqId);
            return false;
        }
        if (!trimmedEquals(state.requestorPartyId(), cmd.partyId())) {
            logger.warn("RejectQuote by unauthorized party: {} for quoteReqId: {}",
                    cmd.partyId(), quoteReqId);
            return false;
        }
        if (state.currentState() != RfqStateType.QUOTED) {
            logger.warn("RejectQuote invalid transition from {} for quoteReqId: {}",
                    state.currentState(), quoteReqId);
            return false;
        }
        return true;
    }

    public boolean validateCancelQuote(final CancelQuoteCmdDecoder cmd) {
        final String quoteReqId = cmd.quoteReqId();
        final RfqState state = rfqStates.get(quoteReqId);
        if (state == null) {
            logger.warn("CancelQuote for unknown quoteReqId: {}", quoteReqId);
            return false;
        }
        if (!trimmedEquals(state.requestorPartyId(), cmd.partyId())) {
            logger.warn("CancelQuote by unauthorized party: {} for quoteReqId: {}",
                    cmd.partyId(), quoteReqId);
            return false;
        }
        if (state.isTerminal()) {
            logger.warn("CancelQuote on terminal state {} for quoteReqId: {}",
                    state.currentState(), quoteReqId);
            return false;
        }
        return true;
    }

    private static boolean trimmedEquals(final String a, final String b) {
        return a != null && b != null && a.trim().equals(b.trim());
    }

    // ---- Apply events (mutate state) ----

    public void applyQuoteRequested(final RequestQuoteCmdDecoder cmd) {
        final RfqState state = new RfqState(cmd.quoteReqId());
        state.currentState(RfqStateType.REQUESTED);
        state.symbol(cmd.symbol());
        state.side(cmd.side());
        state.quantity(cmd.quantity());
        state.requestorPartyId(cmd.partyId());
        rfqStates.put(cmd.quoteReqId(), state);
    }

    public void applyQuoteProposed(final ProposeQuoteCmdDecoder cmd) {
        final RfqState state = rfqStates.get(cmd.quoteReqId());
        state.currentState(RfqStateType.QUOTED);
        state.quoteId(cmd.quoteId());
        state.bidPx(cmd.bidPx());
        state.offerPx(cmd.offerPx());
        state.bidSize(cmd.bidSize());
        state.offerSize(cmd.offerSize());
        state.validUntilTime(cmd.validUntilTime());
        state.dealerPartyId(cmd.partyId());
    }

    public void applyQuoteAccepted(final AcceptQuoteCmdDecoder cmd) {
        final RfqState state = rfqStates.remove(cmd.quoteReqId());
        state.currentState(RfqStateType.ACCEPTED);
    }

    public void applyQuoteRejected(final RejectQuoteCmdDecoder cmd) {
        final RfqState state = rfqStates.remove(cmd.quoteReqId());
        state.currentState(RfqStateType.REJECTED);
    }

    public void applyQuoteCancelled(final CancelQuoteCmdDecoder cmd) {
        final RfqState state = rfqStates.remove(cmd.quoteReqId());
        state.currentState(RfqStateType.CANCELLED);
    }

    // ---- Query ----

    public RfqState getState(final String quoteReqId) {
        return rfqStates.get(quoteReqId);
    }

    public int size() {
        return rfqStates.size();
    }
}

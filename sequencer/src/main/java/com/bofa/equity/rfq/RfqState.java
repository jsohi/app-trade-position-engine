package com.bofa.equity.rfq;

import com.bofa.equity.sbe.RfqSideType;
import com.bofa.equity.sbe.RfqStateType;

/**
 * Mutable per-RFQ state held by the sequencer aggregate.
 * Only mutated by the single sequencer thread — no synchronization needed.
 */
public class RfqState {

    private RfqStateType currentState;
    private String quoteReqId;
    private String quoteId;
    private String symbol;
    private RfqSideType side;
    private long quantity;
    private double bidPx;
    private double offerPx;
    private long bidSize;
    private long offerSize;
    private long validUntilTime;
    private String requestorPartyId;
    private String dealerPartyId;

    public RfqState(final String quoteReqId) {
        this.quoteReqId = quoteReqId;
    }

    public RfqStateType currentState() { return currentState; }
    public void currentState(final RfqStateType state) { this.currentState = state; }

    public String quoteReqId() { return quoteReqId; }

    public String quoteId() { return quoteId; }
    public void quoteId(final String quoteId) { this.quoteId = quoteId; }

    public String symbol() { return symbol; }
    public void symbol(final String symbol) { this.symbol = symbol; }

    public RfqSideType side() { return side; }
    public void side(final RfqSideType side) { this.side = side; }

    public long quantity() { return quantity; }
    public void quantity(final long quantity) { this.quantity = quantity; }

    public double bidPx() { return bidPx; }
    public void bidPx(final double bidPx) { this.bidPx = bidPx; }

    public double offerPx() { return offerPx; }
    public void offerPx(final double offerPx) { this.offerPx = offerPx; }

    public long bidSize() { return bidSize; }
    public void bidSize(final long bidSize) { this.bidSize = bidSize; }

    public long offerSize() { return offerSize; }
    public void offerSize(final long offerSize) { this.offerSize = offerSize; }

    public long validUntilTime() { return validUntilTime; }
    public void validUntilTime(final long validUntilTime) { this.validUntilTime = validUntilTime; }

    public String requestorPartyId() { return requestorPartyId; }
    public void requestorPartyId(final String partyId) { this.requestorPartyId = partyId; }

    public String dealerPartyId() { return dealerPartyId; }
    public void dealerPartyId(final String partyId) { this.dealerPartyId = partyId; }

    public boolean isTerminal() {
        return currentState == RfqStateType.ACCEPTED
            || currentState == RfqStateType.REJECTED
            || currentState == RfqStateType.CANCELLED
            || currentState == RfqStateType.EXPIRED;
    }
}

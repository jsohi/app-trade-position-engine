package com.bofa.equity.rfq;

import com.bofa.equity.sbe.RfqSideType;
import com.bofa.equity.sbe.RfqStateType;
import org.apache.logging.log4j.util.StringBuilderFormattable;

/**
 * Read-side cache entry for a single RFQ. Mutable — updated by the projection
 * on the receiver thread. Implements {@link StringBuilderFormattable} for
 * GC-free Log4j2 logging.
 */
public class RfqCacheEntry implements StringBuilderFormattable {

    private long sequenceNumber;
    private String quoteReqId;
    private String quoteId;
    private String symbol;
    private RfqSideType side;
    private long quantity;
    private double bidPx;
    private double offerPx;
    private long bidSize;
    private long offerSize;
    private String partyId;
    private RfqStateType state;
    private long timestampNanos;

    public long sequenceNumber() { return sequenceNumber; }
    public void sequenceNumber(final long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String quoteReqId() { return quoteReqId; }
    public void quoteReqId(final String quoteReqId) { this.quoteReqId = quoteReqId; }

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

    public String partyId() { return partyId; }
    public void partyId(final String partyId) { this.partyId = partyId; }

    public RfqStateType state() { return state; }
    public void state(final RfqStateType state) { this.state = state; }

    public long timestampNanos() { return timestampNanos; }
    public void timestampNanos(final long timestampNanos) { this.timestampNanos = timestampNanos; }

    @Override
    public void formatTo(final StringBuilder buffer) {
        buffer.append("RfqCacheEntry{")
              .append("seq=").append(sequenceNumber)
              .append(", quoteReqId=").append(quoteReqId)
              .append(", quoteId=").append(quoteId)
              .append(", symbol=").append(symbol)
              .append(", state=").append(state)
              .append('}');
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(128);
        formatTo(sb);
        return sb.toString();
    }
}

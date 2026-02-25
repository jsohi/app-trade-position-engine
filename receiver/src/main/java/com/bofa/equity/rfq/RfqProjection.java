package com.bofa.equity.rfq;

import com.bofa.equity.sbe.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Read-side projection that maintains caches from RFQ events.
 * Updated by the single receiver thread — no synchronization needed.
 */
public class RfqProjection {
    private static final Logger logger = LogManager.getLogger(RfqProjection.class);

    private final Map<String, RfqCacheEntry> rfqCache = new HashMap<>();
    private final Map<String, RfqCacheEntry> quoteCache = new HashMap<>();

    public void onQuoteRequested(final QuoteRequestedEvtDecoder evt) {
        final String quoteReqId = evt.quoteReqId().trim();
        final RfqCacheEntry entry = getOrCreateEntry(quoteReqId);
        entry.sequenceNumber(evt.sequenceNumber());
        entry.quoteReqId(quoteReqId);
        entry.symbol(evt.symbol().trim());
        entry.side(evt.side());
        entry.quantity(evt.quantity());
        entry.partyId(evt.partyId().trim());
        entry.state(evt.state());
        entry.timestampNanos(evt.timestampNanos());
        logger.debug("Projected QuoteRequested: {}", entry.toString());
    }

    public void onQuoteProposed(final QuoteProposedEvtDecoder evt) {
        final String quoteReqId = evt.quoteReqId().trim();
        final RfqCacheEntry entry = getOrCreateEntry(quoteReqId);
        entry.sequenceNumber(evt.sequenceNumber());
        entry.quoteId(evt.quoteId().trim());
        entry.bidPx(evt.bidPx());
        entry.offerPx(evt.offerPx());
        entry.bidSize(evt.bidSize());
        entry.offerSize(evt.offerSize());
        entry.state(evt.state());
        entry.timestampNanos(evt.timestampNanos());

        // Index by quoteId
        quoteCache.put(evt.quoteId().trim(), entry);
        logger.debug("Projected QuoteProposed: {}", entry.toString());
    }

    public void onQuoteAccepted(final QuoteAcceptedEvtDecoder evt) {
        final String quoteReqId = evt.quoteReqId().trim();
        final RfqCacheEntry entry = rfqCache.get(quoteReqId);
        if (entry != null) {
            entry.sequenceNumber(evt.sequenceNumber());
            entry.state(evt.state());
            entry.timestampNanos(evt.timestampNanos());
            logger.debug("Projected QuoteAccepted: {}", entry.toString());
        }
    }

    public void onQuoteRejected(final QuoteRejectedEvtDecoder evt) {
        final String quoteReqId = evt.quoteReqId().trim();
        final RfqCacheEntry entry = rfqCache.get(quoteReqId);
        if (entry != null) {
            entry.sequenceNumber(evt.sequenceNumber());
            entry.state(evt.state());
            entry.timestampNanos(evt.timestampNanos());
            logger.debug("Projected QuoteRejected: {}", entry.toString());
        }
    }

    public void onQuoteCancelled(final QuoteCancelledEvtDecoder evt) {
        final String quoteReqId = evt.quoteReqId().trim();
        final RfqCacheEntry entry = rfqCache.get(quoteReqId);
        if (entry != null) {
            entry.sequenceNumber(evt.sequenceNumber());
            entry.state(evt.state());
            entry.timestampNanos(evt.timestampNanos());
            logger.debug("Projected QuoteCancelled: {}", entry.toString());
        }
    }

    public RfqCacheEntry getByQuoteReqId(final String quoteReqId) {
        return rfqCache.get(quoteReqId);
    }

    public RfqCacheEntry getByQuoteId(final String quoteId) {
        return quoteCache.get(quoteId);
    }

    public int rfqCacheSize() {
        return rfqCache.size();
    }

    private RfqCacheEntry getOrCreateEntry(final String quoteReqId) {
        return rfqCache.computeIfAbsent(quoteReqId, k -> new RfqCacheEntry());
    }
}

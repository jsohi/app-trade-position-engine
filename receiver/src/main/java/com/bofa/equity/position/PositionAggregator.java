package com.bofa.equity.position;

import com.bofa.equity.cache.Cache;
import com.bofa.equity.sbe.SideType;
import com.bofa.equity.sbe.TradeDecoder;
import org.HdrHistogram.Histogram;
import org.agrona.collections.BiInt2ObjectMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class PositionAggregator {
    private static final Logger logger = LogManager.getLogger(PositionAggregator.class);

    private static final long HISTOGRAM_MAX_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final Histogram END_TO_END_HISTOGRAM = new Histogram(HISTOGRAM_MAX_NANOS, 3);
    // TRANSPORT_HISTOGRAM is a placeholder for publish→receive latency;
    // it records the same value as END_TO_END_HISTOGRAM until a dedicated publish
    // timestamp is added to the SBE message header for true transport latency measurement.
    private static final Histogram TRANSPORT_HISTOGRAM  = new Histogram(HISTOGRAM_MAX_NANOS, 3);

    // re-usable string buffer to copy chars, without creating new Strings object from trade decoder
    private final StringBuffer tradeReferenceBuffer = new StringBuffer(TradeDecoder.referenceIdLength());
    // pre-allocated log buffer — passed as CharSequence to avoid String allocation on the log call site
    private final StringBuilder positionLogBuffer = new StringBuilder(256);

    // can be replaced with Account/Security Pair class to Position data HashMap with overridden equals and hashcode
    private final BiInt2ObjectMap<PositionData> positionDataByAccountAndSecurity = new BiInt2ObjectMap<>();

    // if we have the count of account and security upfront, better to create objects upfront, otherwise we can create on the fly as well
    public PositionAggregator(final Cache cache) {
        requireNonNull(cache);
        for (int accountId : cache.accountIds().keySet()) {
            for (int securityId : cache.securityIds().keySet()) {
                positionDataByAccountAndSecurity.put(accountId, securityId, new DefaultPositionData(accountId, securityId));
            }
        }
    }

    public void aggregate(final TradeDecoder trade, final long receivedTimeNanos) {
        recordLatency(trade.timestampMillis(), receivedTimeNanos);

        tradeReferenceBuffer.setLength(0);
        trade.getReferenceId(tradeReferenceBuffer);
        logger.info("Aggregating position data using referenceId={}", tradeReferenceBuffer);

        final int accountId = trade.accountId();
        final int securityId = trade.securityId();
        final PositionData currentData = positionDataByAccountAndSecurity.computeIfAbsent(accountId, securityId,
                (i, j) -> new DefaultPositionData(accountId, securityId));

        currentData.update(trade.quantity(), trade.price(), SideType.B == trade.side());

        positionLogBuffer.setLength(0);
        currentData.appendTo(positionLogBuffer);
        logger.debug("{}", positionLogBuffer);
    }

    private void recordLatency(final long tradeTimestampNanos, final long receivedTimeNanos) {
        final long latencyNanos = receivedTimeNanos - tradeTimestampNanos;
        if (latencyNanos < 0 || latencyNanos > HISTOGRAM_MAX_NANOS) {
            logger.warn("Dropping out-of-range latency sample: {}ns (tradeTimestamp={}, receivedTime={})",
                    latencyNanos, tradeTimestampNanos, receivedTimeNanos);
            return;
        }
        END_TO_END_HISTOGRAM.recordValue(latencyNanos);
        TRANSPORT_HISTOGRAM.recordValue(latencyNanos);
    }

    public void resetStats() {
        END_TO_END_HISTOGRAM.reset();
        TRANSPORT_HISTOGRAM.reset();
        logger.info("Stats histograms reset after warm-up phase");
    }

    public void stats() {
        // TODO if we need to log actual account and security String values, we can reverse lookup string ids from Cache
        logger.info("### Logging stats post processing all trades data ### ");

        logger.debug("Position aggregation results={}", positionDataByAccountAndSecurity.toString()); // change to info for checking aggregated data in logs
        positionDataByAccountAndSecurity.forEach(e -> {
            positionLogBuffer.setLength(0);
            e.appendTo(positionLogBuffer);
            logger.debug("{}", positionLogBuffer);
        });

        System.out.println("=== END-TO-END LATENCY (nanos) ===");
        END_TO_END_HISTOGRAM.outputPercentileDistribution(System.out, 1.0);
        System.out.println("=== TRANSPORT LATENCY (nanos) ===");
        TRANSPORT_HISTOGRAM.outputPercentileDistribution(System.out, 1.0);
    }

    PositionData positionData(final int accountId, final int securityId) {
        return positionDataByAccountAndSecurity.get(accountId, securityId);
    }

}

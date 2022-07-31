package com.bofa.equity.position;

import com.bofa.equity.cache.Cache;
import com.bofa.equity.sbe.SideType;
import com.bofa.equity.sbe.TradeDecoder;
import org.HdrHistogram.Histogram;
import org.agrona.collections.BiInt2ObjectMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class PositionAggregator {
    private static final Logger logger = LoggerFactory.getLogger(PositionAggregator.class);

    private static final Histogram HISTOGRAM = new Histogram(TimeUnit.SECONDS.toNanos(10), 3);

    // re-usable string buffer to copy chars, without creating new Strings object from trade decoder
    private final StringBuffer tradeReferenceBuffer = new StringBuffer(TradeDecoder.referenceIdLength());

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

    public void aggregate(final TradeDecoder trade, final long receivedTimeMillis) {
        recordLatency(trade.timestampMillis(), receivedTimeMillis);

        tradeReferenceBuffer.setLength(0);
        trade.getReferenceId(tradeReferenceBuffer);
        logger.info("Aggregating position data using referenceId={}", tradeReferenceBuffer);

        final int accountId = trade.accountId();
        final int securityId = trade.securityId();
        final PositionData currentData = positionDataByAccountAndSecurity.computeIfAbsent(accountId, securityId,
                (i, j) -> new DefaultPositionData(accountId, securityId));

        currentData.update(trade.quantity(), trade.price(), SideType.B == trade.side());

        logger.debug("{}", currentData);
    }

    private void recordLatency(final long tradeTimestampMillis, final long receivedTimeMillis) {
        // record latency from trade creation time to process time.
        // we can also change it to Trade publish time to received time on aeron expanding timestamps on SBE header messages
        HISTOGRAM.recordValue(receivedTimeMillis - tradeTimestampMillis);
    }

    public void stats() {
        // TODO if we need to log actual account and security String values, we can reverse lookup string ids from Cache
        logger.info("### Logging stats post processing all trades data ### ");

        logger.debug("Position aggregation results={}", positionDataByAccountAndSecurity); // change to info for checking aggregated data in logs
        positionDataByAccountAndSecurity.forEach(e -> logger.debug("{}", e));

        // System out for now
        HISTOGRAM.outputPercentileDistribution(System.out, 1000.0);
    }

    PositionData positionData(final int accountId, final int securityId) {
        return positionDataByAccountAndSecurity.get(accountId, securityId);
    }

}

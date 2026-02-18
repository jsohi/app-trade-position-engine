package com.bofa.equity.agents;

import com.bofa.equity.trade.TradeHandler;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.EpochClock;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.agrona.concurrent.SystemEpochClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

public class ReceiveAgent implements Agent {
    private final Logger logger = LoggerFactory.getLogger(ReceiveAgent.class);

    public static final int FRAGMENT_LIMIT = 100; // number of message fragments to limit when polling

    private final EpochClock clock = SystemEpochClock.INSTANCE;

    private final Subscription subscription;
    private final ShutdownSignalBarrier barrier;
    private final int sendCount;
    private final TradeHandler tradeHandler;
    private int currentCount = 0;
    private long processingStartTimeMillis;

    public ReceiveAgent(final Subscription subscription,
                        final ShutdownSignalBarrier barrier,
                        final int sendCount,
                        final TradeHandler tradeHandler) {
        this.subscription = requireNonNull(subscription);
        this.barrier = requireNonNull(barrier);
        this.sendCount = sendCount;
        this.tradeHandler = tradeHandler;
    }

    @Override
    public int doWork() {
        subscription.poll(this::handler, FRAGMENT_LIMIT);
        return 0;
    }

    private void handler(final DirectBuffer directBuffer,
                         final int offset,
                         final int length,
                         final Header header) {
        final long receivedTimeMillis = clock.time();
        final boolean handlerTrade = tradeHandler.handle(directBuffer, offset, receivedTimeMillis);
        if (handlerTrade && ++currentCount >= sendCount) {
            logger.info("Processed {} in {} millis", currentCount, receivedTimeMillis - processingStartTimeMillis);
            barrier.signal();
        }
        if (currentCount == 1) {
            processingStartTimeMillis = receivedTimeMillis;
        }
    }

    @Override
    public String roleName() {
        return "receiver";
    }
}

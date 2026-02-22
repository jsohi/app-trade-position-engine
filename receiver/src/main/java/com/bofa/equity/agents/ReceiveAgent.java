package com.bofa.equity.agents;

import com.bofa.equity.trade.TradeHandler;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.Objects.requireNonNull;

public class ReceiveAgent implements Agent {
    private final Logger logger = LogManager.getLogger(ReceiveAgent.class);

    public static final int FRAGMENT_LIMIT = 100; // number of message fragments to limit when polling

    private final Subscription subscription;
    private final ShutdownSignalBarrier barrier;
    private final int sendCount;
    private final TradeHandler tradeHandler;
    private int currentCount = 0;
    private long processingStartTimeNanos;

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
        final long receivedTimeNanos = System.nanoTime();
        final boolean handledTrade = tradeHandler.handle(directBuffer, offset, length, receivedTimeNanos);
        if (handledTrade && ++currentCount >= sendCount) {
            logger.info("Processed {} in {} nanos", currentCount, receivedTimeNanos - processingStartTimeNanos);
            barrier.signal();
        }
        if (currentCount == 1) {
            processingStartTimeNanos = receivedTimeNanos;
        }
    }

    @Override
    public String roleName() {
        return "receiver";
    }
}

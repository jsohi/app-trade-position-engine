package com.bofa.equity.agents;

import com.bofa.equity.rfq.RfqEventHandler;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Polls RFQ events from Aeron (stream 21) and delegates to {@link RfqEventHandler}
 * for read-side projection updates.
 */
public class RfqReceiveAgent implements Agent {
    private static final Logger logger = LogManager.getLogger(RfqReceiveAgent.class);

    public static final int FRAGMENT_LIMIT = 100;

    private final Subscription subscription;
    private final RfqEventHandler eventHandler;

    public RfqReceiveAgent(final Subscription subscription,
                           final RfqEventHandler eventHandler) {
        this.subscription = requireNonNull(subscription);
        this.eventHandler = requireNonNull(eventHandler);
    }

    @Override
    public int doWork() {
        return subscription.poll(this::onFragment, FRAGMENT_LIMIT);
    }

    private void onFragment(final DirectBuffer buffer,
                            final int offset,
                            final int length,
                            final Header header) {
        eventHandler.handle(buffer, offset, length);
    }

    @Override
    public String roleName() {
        return "rfq-receiver";
    }
}

package com.bofa.equity.agents;

import com.bofa.equity.command.RfqCommandHandler;
import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Single-threaded sequencer agent. Polls RFQ commands from Aeron (stream 20),
 * validates via aggregate, assigns monotonic sequence numbers, and emits events (stream 21).
 */
public class SequencerAgent implements Agent {
    private static final Logger logger = LogManager.getLogger(SequencerAgent.class);

    public static final int FRAGMENT_LIMIT = 100;

    private final Subscription subscription;
    private final RfqCommandHandler commandHandler;

    public SequencerAgent(final Subscription subscription,
                          final RfqCommandHandler commandHandler) {
        this.subscription = requireNonNull(subscription);
        this.commandHandler = requireNonNull(commandHandler);
    }

    @Override
    public int doWork() {
        return subscription.poll(this::onFragment, FRAGMENT_LIMIT);
    }

    private void onFragment(final DirectBuffer buffer,
                            final int offset,
                            final int length,
                            final Header header) {
        commandHandler.handle(buffer, offset, length);
    }

    @Override
    public String roleName() {
        return "sequencer";
    }
}

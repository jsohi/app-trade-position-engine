package com.bofa.equity;

import com.bofa.equity.agents.SequencerAgent;
import com.bofa.equity.command.RfqCommandHandler;
import com.bofa.equity.event.RfqEventPublisher;
import com.bofa.equity.rfq.RfqAggregate;
import com.bofa.equity.rfq.RfqConstants;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SequencerApp {
    private static final Logger logger = LogManager.getLogger(SequencerApp.class);

    public static void main(String[] args) {
        final String aeronDir = System.getProperty("aeron.dir");
        if (aeronDir == null) {
            throw new IllegalStateException("System property -Daeron.dir is required");
        }

        final String channel = validateChannel(System.getProperty("aeron.channel", "aeron:ipc"));

        logger.info("Sequencer starting: aeronDir={}, channel={}, cmdStream={}, evtStream={}",
                aeronDir, channel, RfqConstants.COMMAND_STREAM_ID, RfqConstants.EVENT_STREAM_ID);

        final Aeron.Context aeronCtx = new Aeron.Context().aeronDirectoryName(aeronDir);

        try (Aeron aeron = Aeron.connect(aeronCtx)) {
            final Subscription cmdSubscription = aeron.addSubscription(channel, RfqConstants.COMMAND_STREAM_ID);
            final Publication evtPublication = aeron.addPublication(channel, RfqConstants.EVENT_STREAM_ID);

            final RfqAggregate aggregate = new RfqAggregate();
            final RfqEventPublisher eventPublisher = new RfqEventPublisher(evtPublication);
            final RfqCommandHandler commandHandler = new RfqCommandHandler(aggregate, eventPublisher);

            final SequencerAgent sequencerAgent = new SequencerAgent(cmdSubscription, commandHandler);
            final AgentRunner agentRunner = new AgentRunner(
                    new BusySpinIdleStrategy(),
                    t -> logger.error("Unhandled error in SequencerAgent duty cycle", t),
                    null,
                    sequencerAgent);

            AgentRunner.startOnThread(agentRunner);

            final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
            barrier.await();

            agentRunner.close();
            cmdSubscription.close();
            evtPublication.close();
            logger.info("Sequencer done.");
        }
    }

    private static String validateChannel(String channel) {
        if ("aeron:ipc".equals(channel) || channel.startsWith("aeron:udp?endpoint=")) {
            return channel;
        }
        throw new IllegalArgumentException(
                "Invalid aeron.channel '" + channel + "': must be 'aeron:ipc' or 'aeron:udp?endpoint=<host>:<port>'");
    }
}

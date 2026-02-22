package com.bofa.equity;

import com.bofa.equity.agents.ReceiveAgent;
import com.bofa.equity.cache.Cache;
import com.bofa.equity.position.PositionAggregator;
import com.bofa.equity.trade.TradeHandler;
import io.aeron.Aeron;
import io.aeron.Subscription;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReceiverApp {
    private static final Logger logger = LogManager.getLogger(ReceiverApp.class);

    public static void main(String[] args) {
        final String aeronDir = System.getProperty("aeron.dir");
        if (aeronDir == null) {
            throw new IllegalStateException("System property -Daeron.dir is required");
        }

        final int sendCount  = Integer.parseInt(System.getProperty("send.count", "1000000"));
        final String channel = System.getProperty("aeron.channel", "aeron:ipc");
        final int streamId   = Integer.parseInt(System.getProperty("aeron.stream.id", "10"));

        logger.info("Receiver starting: aeronDir={}, sendCount={}, channel={}, stream={}",
                aeronDir, sendCount, channel, streamId);

        final Aeron.Context aeronCtx = new Aeron.Context().aeronDirectoryName(aeronDir);

        try (Aeron aeron = Aeron.connect(aeronCtx)) {
            final Subscription subscription = aeron.addSubscription(channel, streamId);
            final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();
            final PositionAggregator positionAggregator = new PositionAggregator(Cache.defaultCache());
            final TradeHandler tradeHandler = new TradeHandler(positionAggregator);

            final ReceiveAgent receiveAgent = new ReceiveAgent(subscription, barrier, sendCount, tradeHandler);
            final AgentRunner receiveAgentRunner = new AgentRunner(
                    new BusySpinIdleStrategy(), Throwable::printStackTrace, null, receiveAgent);

            AgentRunner.startOnThread(receiveAgentRunner);

            barrier.await();
            positionAggregator.stats();

            receiveAgentRunner.close();
            subscription.close();
            logger.info("Receiver done.");
        }
    }
}

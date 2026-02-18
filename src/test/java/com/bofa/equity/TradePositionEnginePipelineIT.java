package com.bofa.equity;

import com.bofa.equity.agents.ReceiveAgent;
import com.bofa.equity.agents.SendAgent;
import com.bofa.equity.cache.Cache;
import com.bofa.equity.position.PositionAggregator;
import com.bofa.equity.trade.TradeHandler;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class TradePositionEnginePipelineIT {

    private static final String AERON_CHANNEL = "aeron:ipc";
    private static final int AERON_STREAM_ID = 10;

    private MediaDriver mediaDriver;
    private Aeron aeron;
    private Subscription subscription;
    private Publication publication;
    private AgentRunner sendRunner;
    private AgentRunner receiveRunner;

    @BeforeEach
    void setUp() {
        mediaDriver = MediaDriver.launchEmbedded(new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .threadingMode(ThreadingMode.SHARED)
                .sharedIdleStrategy(new BusySpinIdleStrategy())
                .dirDeleteOnShutdown(true));
        aeron = Aeron.connect(new Aeron.Context()
                .aeronDirectoryName(mediaDriver.aeronDirectoryName()));
        subscription = aeron.addSubscription(AERON_CHANNEL, AERON_STREAM_ID);
        publication = aeron.addPublication(AERON_CHANNEL, AERON_STREAM_ID);
    }

    @AfterEach
    void tearDown() {
        CloseHelper.closeAll(sendRunner, receiveRunner, aeron, mediaDriver);
    }

    @Test
    @Timeout(30)
    void pipeline_allRandomTradesReceivedAndAggregated() {
        final int sendCount = 1_000;
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        final PositionAggregator aggregator = new PositionAggregator(Cache.defaultCache());
        final TradeHandler tradeHandler = new TradeHandler(aggregator);

        final SendAgent sendAgent = new SendAgent(publication, sendCount);
        final ReceiveAgent receiveAgent = new ReceiveAgent(subscription, barrier, sendCount, tradeHandler);

        sendRunner = new AgentRunner(new BusySpinIdleStrategy(), Throwable::printStackTrace, null, sendAgent);
        receiveRunner = new AgentRunner(new BusySpinIdleStrategy(), Throwable::printStackTrace, null, receiveAgent);

        AgentRunner.startOnThread(sendRunner);
        AgentRunner.startOnThread(receiveRunner);

        barrier.await();

        aggregator.stats();
    }
}

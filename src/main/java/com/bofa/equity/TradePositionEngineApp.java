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
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradePositionEngineApp {
    private static final Logger logger = LoggerFactory.getLogger(TradePositionEngineApp.class);

    public static void main(String[] args) {
        logger.info("Starting trade position engine app...");

        final String aeronChannel = "aeron:ipc"; // can use UPD with host details aeron:udp?endpoint=127.0.0.1:2000
        final int aeronStreamID = 10;

        // Change below
        final int sendCount = 1_000_000;

        final IdleStrategy idleStrategySend = new BusySpinIdleStrategy(); // low latency strategy

        final IdleStrategy idleStrategyReceive = new BusySpinIdleStrategy();
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        //construct Media Driver, cleaning up media driver folder on start/stop
        final MediaDriver.Context mediaDriverCtx = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .threadingMode(ThreadingMode.SHARED)
                .sharedIdleStrategy(new BusySpinIdleStrategy())
                .dirDeleteOnShutdown(true);
        final MediaDriver mediaDriver = MediaDriver.launchEmbedded(mediaDriverCtx);

        //construct Aeron, pointing at the media driver's folder
        final Aeron.Context aeronCtx = new Aeron.Context()
                .aeronDirectoryName(mediaDriver.aeronDirectoryName());
        final Aeron aeron = Aeron.connect(aeronCtx);

        logger.info("Aeron dir {}", mediaDriver.aeronDirectoryName());

        //construct the subs and pubs
        final Subscription subscription = aeron.addSubscription(aeronChannel, aeronStreamID);
        final Publication publication = aeron.addPublication(aeronChannel, aeronStreamID);

        //construct the agents
        final SendAgent sendAgent = new SendAgent(publication, sendCount);

        final PositionAggregator positionAggregator = new PositionAggregator(Cache.defaultCache());
        final TradeHandler tradeHandler = new TradeHandler(positionAggregator);
        final ReceiveAgent receiveAgent = new ReceiveAgent(subscription, barrier, sendCount, tradeHandler);

        //construct agent runners
        final AgentRunner sendAgentRunner = new AgentRunner(idleStrategySend,
                Throwable::printStackTrace, null, sendAgent);
        final AgentRunner receiveAgentRunner = new AgentRunner(idleStrategyReceive,
                Throwable::printStackTrace, null, receiveAgent);

        logger.info("Starting app with aeronChannel={}...", aeronChannel);

        //start the runners
        AgentRunner.startOnThread(sendAgentRunner);
        AgentRunner.startOnThread(receiveAgentRunner);

        //wait for the final item to be received before closing
        barrier.await();

        positionAggregator.stats();

        //close the resources
        receiveAgentRunner.close();
        sendAgentRunner.close();
        aeron.close();
        mediaDriver.close();
        logger.info("Trade position engine shut down...");
    }
}

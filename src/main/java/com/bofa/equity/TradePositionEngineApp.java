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
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinityThreadFactory;
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

        final String aeronChannel = "aeron:ipc"; // can use UDP with host details aeron:udp?endpoint=127.0.0.1:2000
        final int aeronStreamID = 10;

        final int warmUpCount = 10_000;
        final int sendCount   = 1_000_000;

        final IdleStrategy idleStrategySend    = new BusySpinIdleStrategy();
        final IdleStrategy idleStrategyReceive = new BusySpinIdleStrategy();

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
        final int auditStreamID = 11; // stream 11 carries regulatory/audit data (referenceId, description)
        final Subscription subscription  = aeron.addSubscription(aeronChannel, aeronStreamID);
        final Publication publication    = aeron.addPublication(aeronChannel, aeronStreamID);
        final Publication auditPublication = aeron.addPublication(aeronChannel, auditStreamID);

        // Shared objects used by both warm-up and main benchmark phases
        final PositionAggregator positionAggregator = new PositionAggregator(Cache.defaultCache());
        final TradeHandler tradeHandler = new TradeHandler(positionAggregator);

        // === WARM-UP PHASE ===
        // Send warmUpCount trades to JIT-compile hot paths and warm CPU caches before recording stats.
        logger.info("Starting warm-up phase with {} trades...", warmUpCount);
        final ShutdownSignalBarrier warmUpBarrier = new ShutdownSignalBarrier();
        final AgentRunner warmUpSendRunner = new AgentRunner(new BusySpinIdleStrategy(),
                Throwable::printStackTrace, null, new SendAgent(publication, auditPublication, warmUpCount));
        final AgentRunner warmUpReceiveRunner = new AgentRunner(new BusySpinIdleStrategy(),
                Throwable::printStackTrace, null,
                new ReceiveAgent(subscription, warmUpBarrier, warmUpCount, tradeHandler));
        AgentRunner.startOnThread(warmUpSendRunner);
        AgentRunner.startOnThread(warmUpReceiveRunner);
        warmUpBarrier.await();
        warmUpReceiveRunner.close();
        warmUpSendRunner.close();
        positionAggregator.resetStats(); // discard warm-up latency measurements
        logger.info("Warm-up complete. Starting main benchmark with {} trades...", sendCount);

        // === MAIN BENCHMARK ===
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        //construct the agents
        final SendAgent sendAgent = new SendAgent(publication, auditPublication, sendCount);
        final ReceiveAgent receiveAgent = new ReceiveAgent(subscription, barrier, sendCount, tradeHandler);

        //construct agent runners
        final AgentRunner sendAgentRunner = new AgentRunner(idleStrategySend,
                Throwable::printStackTrace, null, sendAgent);
        final AgentRunner receiveAgentRunner = new AgentRunner(idleStrategyReceive,
                Throwable::printStackTrace, null, receiveAgent);

        logger.info("Starting app with aeronChannel={}...", aeronChannel);

        // Pin sender and receiver to separate CPU cores for lowest latency.
        // On macOS, sched_setaffinity is unavailable — OpenHFT degrades silently to best-effort.
        // On Linux this will hard-pin each thread to a distinct physical core.
        final AffinityThreadFactory threadFactory =
                new AffinityThreadFactory("trade-engine", AffinityStrategies.DIFFERENT_CORE);
        final Thread sendThread    = threadFactory.newThread(sendAgentRunner);
        final Thread receiveThread = threadFactory.newThread(receiveAgentRunner);
        sendThread.setName("sender");
        receiveThread.setName("receiver");
        sendThread.start();
        receiveThread.start();

        //wait for the final item to be received before closing
        barrier.await();

        positionAggregator.stats();

        //close the resources
        receiveAgentRunner.close();
        sendAgentRunner.close();
        auditPublication.close();
        aeron.close();
        mediaDriver.close();
        logger.info("Trade position engine shut down...");
    }
}

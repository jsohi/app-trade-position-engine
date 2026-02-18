package com.bofa.equity.position;

import com.bofa.equity.agents.ReceiveAgent;
import com.bofa.equity.cache.Cache;
import com.bofa.equity.sbe.SideType;
import com.bofa.equity.trade.TradeHandler;
import com.bofa.equity.util.IntegrationTradePublisher;
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

import static org.junit.jupiter.api.Assertions.*;

public class PositionAggregatorPipelineIT {

    private static final String AERON_CHANNEL = "aeron:ipc";
    private static final int AERON_STREAM_ID = 10;

    private MediaDriver mediaDriver;
    private Aeron aeron;
    private Subscription subscription;
    private Publication publication;
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
        CloseHelper.closeAll(receiveRunner, aeron, mediaDriver);
    }

    @Test
    @Timeout(30)
    void pipeline_singleKnownBuyTrade_positionIsCorrect() {
        final int accountId = 1;
        final int securityId = 1;
        final long quantity = 500;
        final double price = 100.0;
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        final PositionAggregator aggregator = new PositionAggregator(Cache.defaultCache());
        final TradeHandler tradeHandler = new TradeHandler(aggregator);
        final ReceiveAgent receiveAgent = new ReceiveAgent(subscription, barrier, 1, tradeHandler);

        receiveRunner = new AgentRunner(new BusySpinIdleStrategy(), Throwable::printStackTrace, null, receiveAgent);
        AgentRunner.startOnThread(receiveRunner);

        while (!publication.isConnected()) {
            Thread.onSpinWait();
        }

        IntegrationTradePublisher.publish(publication, accountId, securityId, SideType.B, quantity, price);

        barrier.await();

        final PositionData positionData = aggregator.positionData(accountId, securityId);
        assertAll("Buy trade position",
                () -> assertEquals(quantity, positionData.buyQuantity(), "buyQuantity"),
                () -> assertEquals(0, positionData.sellQuantity(), "sellQuantity")
        );
    }

    @Test
    @Timeout(30)
    void pipeline_singleKnownSellTrade_positionIsCorrect() {
        final int accountId = 1;
        final int securityId = 1;
        final long quantity = 500;
        final double price = 100.0;
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        final PositionAggregator aggregator = new PositionAggregator(Cache.defaultCache());
        final TradeHandler tradeHandler = new TradeHandler(aggregator);
        final ReceiveAgent receiveAgent = new ReceiveAgent(subscription, barrier, 1, tradeHandler);

        receiveRunner = new AgentRunner(new BusySpinIdleStrategy(), Throwable::printStackTrace, null, receiveAgent);
        AgentRunner.startOnThread(receiveRunner);

        while (!publication.isConnected()) {
            Thread.onSpinWait();
        }

        IntegrationTradePublisher.publish(publication, accountId, securityId, SideType.S, quantity, price);

        barrier.await();

        final PositionData positionData = aggregator.positionData(accountId, securityId);
        assertAll("Sell trade position",
                () -> assertEquals(0, positionData.buyQuantity(), "buyQuantity"),
                () -> assertEquals(quantity, positionData.sellQuantity(), "sellQuantity")
        );
    }

    @Test
    @Timeout(30)
    void pipeline_knownBuyAndSellTrades_netPositionIsCorrect() {
        final int accountId = 2;
        final int securityId = 5;
        final long buyQuantity = 300;
        final double buyPrice = 50.0;
        final long sellQuantity = 100;
        final double sellPrice = 60.0;
        final ShutdownSignalBarrier barrier = new ShutdownSignalBarrier();

        final PositionAggregator aggregator = new PositionAggregator(Cache.defaultCache());
        final TradeHandler tradeHandler = new TradeHandler(aggregator);
        final ReceiveAgent receiveAgent = new ReceiveAgent(subscription, barrier, 2, tradeHandler);

        receiveRunner = new AgentRunner(new BusySpinIdleStrategy(), Throwable::printStackTrace, null, receiveAgent);
        AgentRunner.startOnThread(receiveRunner);

        while (!publication.isConnected()) {
            Thread.onSpinWait();
        }

        IntegrationTradePublisher.publish(publication, accountId, securityId, SideType.B, buyQuantity, buyPrice);
        IntegrationTradePublisher.publish(publication, accountId, securityId, SideType.S, sellQuantity, sellPrice);

        barrier.await();

        final PositionData positionData = aggregator.positionData(accountId, securityId);
        assertAll("Buy and sell trade net position",
                () -> assertEquals(buyQuantity, positionData.buyQuantity(), "buyQuantity"),
                () -> assertEquals(sellQuantity, positionData.sellQuantity(), "sellQuantity"),
                () -> assertEquals(buyQuantity - sellQuantity, positionData.netQuantity(), "netQuantity")
        );
    }
}

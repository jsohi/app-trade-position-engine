package com.bofa.equity.position;

import com.bofa.equity.agents.ReceiveAgent;
import com.bofa.equity.agents.SendAgent;
import com.bofa.equity.cache.Cache;
import com.bofa.equity.sbe.SideType;
import com.bofa.equity.trade.TradeHandler;
import com.bofa.equity.util.IntegrationTradePublisher;
import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
// Note: @When and @Then each match the corresponding keyword AND "And"/"But" steps with the same expression.
import org.agrona.CloseHelper;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.BusySpinIdleStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// In com.bofa.equity.position package for package-private access to PositionAggregator.positionData().
// Uses Cucumber's @Before/@After (io.cucumber.java), NOT JUnit's.
public class TradePipelineSteps {

    private static final String AERON_CHANNEL = "aeron:ipc";
    private static final int AERON_STREAM_ID  = 10;

    private final PipelineWorld world;

    // PicoContainer injects the shared PipelineWorld instance
    public TradePipelineSteps(final PipelineWorld world) {
        this.world = world;
    }

    @Before
    public void setUp() {
        world.mediaDriver = MediaDriver.launchEmbedded(new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .threadingMode(ThreadingMode.SHARED)
                .sharedIdleStrategy(new BusySpinIdleStrategy())
                .dirDeleteOnShutdown(true));
        world.aeron = Aeron.connect(new Aeron.Context()
                .aeronDirectoryName(world.mediaDriver.aeronDirectoryName()));
        world.subscription = world.aeron.addSubscription(AERON_CHANNEL, AERON_STREAM_ID);
        world.publication  = world.aeron.addPublication(AERON_CHANNEL, AERON_STREAM_ID);
        world.aggregator   = new PositionAggregator(Cache.defaultCache());
    }

    @After
    public void tearDown() {
        CloseHelper.closeAll(world.sendRunner, world.receiveRunner, world.aeron, world.mediaDriver);
    }

    @Given("the Aeron IPC pipeline is started")
    public void pipelineIsStarted() {
        // The Aeron subscription is created in setUp() — the publication is connected as soon as
        // a subscriber exists, regardless of whether the ReceiveAgent is polling yet.
        while (!world.publication.isConnected()) {
            Thread.onSpinWait();
        }
    }

    // --- Random trades scenario ---

    @When("{int} random trades are sent")
    public void randomTradesAreSent(final int count) {
        world.totalTradeCount = count;
        final TradeHandler tradeHandler = new TradeHandler(world.aggregator);
        world.sendRunner = new AgentRunner(new BusySpinIdleStrategy(),
                Throwable::printStackTrace, null, new SendAgent(world.publication, count));
        world.receiveRunner = new AgentRunner(new BusySpinIdleStrategy(),
                Throwable::printStackTrace, null,
                new ReceiveAgent(world.subscription, world.barrier, count, tradeHandler));
        AgentRunner.startOnThread(world.sendRunner);
        AgentRunner.startOnThread(world.receiveRunner);
        world.barrier.await();
    }

    @Then("all trades are received and aggregated")
    public void allTradesReceivedAndAggregated() {
        assertNotNull(world.aggregator);
    }

    // --- Known-trade scenarios ---
    // Trades are published in @When/@And steps. The ReceiveAgent is started lazily in the first
    // @Then assertion, after all trades have been published. Aeron IPC buffers the messages in
    // shared memory so they are not lost before the receiver starts polling.

    // @When also matches "And a {word} trade..." steps — Cucumber matches by expression, not keyword.
    @When("a {word} trade is sent for account {int} security {int} with quantity {long} and price {double}")
    public void aKnownTradeIsSent(final String side, final int accountId, final int securityId,
                                   final long quantity, final double price) {
        publishKnownTrade(side, accountId, securityId, quantity, price);
    }

    private void publishKnownTrade(final String side, final int accountId, final int securityId,
                                    final long quantity, final double price) {
        final SideType sideType = "buy".equalsIgnoreCase(side) ? SideType.B : SideType.S;
        world.totalTradeCount++;
        IntegrationTradePublisher.publish(world.publication, accountId, securityId, sideType, quantity, price);
    }

    @Then("the position for account {int} security {int} has buy quantity {long}")
    public void positionHasBuyQuantity(final int accountId, final int securityId, final long expected) {
        awaitAllKnownTrades();
        assertEquals(expected, world.aggregator.positionData(accountId, securityId).buyQuantity(), "buyQuantity");
    }

    @Then("the position for account {int} security {int} has sell quantity {long}")
    public void positionHasSellQuantity(final int accountId, final int securityId, final long expected) {
        assertEquals(expected, world.aggregator.positionData(accountId, securityId).sellQuantity(), "sellQuantity");
    }

    @Then("the position for account {int} security {int} has net quantity {long}")
    public void positionHasNetQuantity(final int accountId, final int securityId, final long expected) {
        assertEquals(expected, world.aggregator.positionData(accountId, securityId).netQuantity(), "netQuantity");
    }

    // Start the ReceiveAgent with the correct total count (now known since all @When/@And have run),
    // then wait for it to drain the messages already buffered in the Aeron IPC ring.
    private void awaitAllKnownTrades() {
        if (world.receiveRunner == null) {
            final TradeHandler tradeHandler = new TradeHandler(world.aggregator);
            world.receiveRunner = new AgentRunner(new BusySpinIdleStrategy(),
                    Throwable::printStackTrace, null,
                    new ReceiveAgent(world.subscription, world.barrier, world.totalTradeCount, tradeHandler));
            AgentRunner.startOnThread(world.receiveRunner);
        }
        world.barrier.await();
    }
}

package com.bofa.equity;

import com.bofa.equity.agents.RfqReceiveAgent;
import com.bofa.equity.agents.SequencerAgent;
import com.bofa.equity.command.RfqCommandHandler;
import com.bofa.equity.event.RfqEventPublisher;
import com.bofa.equity.rfq.*;
import com.bofa.equity.sbe.*;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.CloseHelper;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the full RFQ pipeline:
 * Client publishes commands → Sequencer validates + emits events → RfqReceiveAgent projects into caches.
 */
public class RfqPipelineIT {

    private static final String CHANNEL = "aeron:ipc";

    private MediaDriver mediaDriver;
    private Aeron aeron;
    private Publication cmdPublication;
    private AgentRunner sequencerRunner;
    private AgentRunner rfqReceiverRunner;
    private RfqProjection projection;

    private final MutableDirectBuffer cmdBuffer = new ExpandableArrayBuffer();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    // Command encoders (reused, flyweight pattern)
    private final RequestQuoteCmdEncoder requestQuoteCmdEncoder = new RequestQuoteCmdEncoder();
    private final ProposeQuoteCmdEncoder proposeQuoteCmdEncoder = new ProposeQuoteCmdEncoder();
    private final AcceptQuoteCmdEncoder acceptQuoteCmdEncoder = new AcceptQuoteCmdEncoder();

    @BeforeEach
    void setUp() {
        mediaDriver = MediaDriver.launchEmbedded(new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnShutdown(true));

        aeron = Aeron.connect(new Aeron.Context()
                .aeronDirectoryName(mediaDriver.aeronDirectoryName()));

        // Client: publishes commands on stream 20
        cmdPublication = aeron.addPublication(CHANNEL, RfqConstants.COMMAND_STREAM_ID);

        // Sequencer: subscribes to commands (stream 20), publishes events (stream 21)
        final Subscription seqCmdSub = aeron.addSubscription(CHANNEL, RfqConstants.COMMAND_STREAM_ID);
        final Publication seqEvtPub = aeron.addPublication(CHANNEL, RfqConstants.EVENT_STREAM_ID);

        final RfqAggregate aggregate = new RfqAggregate();
        final RfqEventPublisher eventPublisher = new RfqEventPublisher(seqEvtPub);
        final RfqCommandHandler commandHandler = new RfqCommandHandler(aggregate, eventPublisher);
        final SequencerAgent sequencerAgent = new SequencerAgent(seqCmdSub, commandHandler);

        sequencerRunner = new AgentRunner(
                new SleepingMillisIdleStrategy(1),
                Throwable::printStackTrace, null, sequencerAgent);
        AgentRunner.startOnThread(sequencerRunner);

        // Receiver: subscribes to events (stream 21), projects into caches
        final Subscription evtSub = aeron.addSubscription(CHANNEL, RfqConstants.EVENT_STREAM_ID);
        projection = new RfqProjection();
        final RfqEventHandler eventHandler = new RfqEventHandler(projection);
        final RfqReceiveAgent rfqReceiveAgent = new RfqReceiveAgent(evtSub, eventHandler);

        rfqReceiverRunner = new AgentRunner(
                new SleepingMillisIdleStrategy(1),
                Throwable::printStackTrace, null, rfqReceiveAgent);
        AgentRunner.startOnThread(rfqReceiverRunner);
    }

    @AfterEach
    void tearDown() {
        CloseHelper.closeAll(sequencerRunner, rfqReceiverRunner, aeron, mediaDriver);
    }

    @Test
    @Timeout(10)
    void fullRfqLifecycle_requestProposeAccept_projectedCorrectly() throws Exception {
        // Wait for publication to connect
        while (!cmdPublication.isConnected()) {
            Thread.sleep(10);
        }

        // 1. RequestQuote
        requestQuoteCmdEncoder.wrapAndApplyHeader(cmdBuffer, 0, headerEncoder)
                .quoteReqId("RFQ-IT-001")
                .symbol("AAPL")
                .side(RfqSideType.BUY)
                .quantity(1000)
                .partyId("CLIENT-A")
                .timestampNanos(System.nanoTime());

        offerUntilSuccess(MessageHeaderEncoder.ENCODED_LENGTH + requestQuoteCmdEncoder.encodedLength());

        // 2. ProposeQuote
        proposeQuoteCmdEncoder.wrapAndApplyHeader(cmdBuffer, 0, headerEncoder)
                .quoteReqId("RFQ-IT-001")
                .quoteId("Q-IT-001")
                .symbol("AAPL")
                .bidPx(149.50)
                .offerPx(150.75)
                .bidSize(500)
                .offerSize(500)
                .validUntilTime(System.nanoTime() + 60_000_000_000L)
                .partyId("DEALER-X")
                .timestampNanos(System.nanoTime());

        offerUntilSuccess(MessageHeaderEncoder.ENCODED_LENGTH + proposeQuoteCmdEncoder.encodedLength());

        // 3. AcceptQuote
        acceptQuoteCmdEncoder.wrapAndApplyHeader(cmdBuffer, 0, headerEncoder)
                .quoteReqId("RFQ-IT-001")
                .quoteId("Q-IT-001")
                .partyId("CLIENT-A")
                .timestampNanos(System.nanoTime());

        offerUntilSuccess(MessageHeaderEncoder.ENCODED_LENGTH + acceptQuoteCmdEncoder.encodedLength());

        // Wait for projection to process all events
        waitForProjectionState("RFQ-IT-001", RfqStateType.ACCEPTED, 5000);

        // Verify final projection state
        final RfqCacheEntry entry = projection.getByQuoteReqId("RFQ-IT-001");
        assertNotNull(entry, "RFQ entry should exist in projection");
        assertEquals(RfqStateType.ACCEPTED, entry.state());
        assertEquals("AAPL", entry.symbol());
        assertEquals(RfqSideType.BUY, entry.side());
        assertEquals(1000, entry.quantity());
        assertEquals(149.50, entry.bidPx());
        assertEquals(150.75, entry.offerPx());
        assertEquals(500, entry.bidSize());
        assertEquals(500, entry.offerSize());

        // Verify quoteId cache
        final RfqCacheEntry byQuoteId = projection.getByQuoteId("Q-IT-001");
        assertNotNull(byQuoteId, "Quote should be indexed by quoteId");
        assertSame(entry, byQuoteId);

        // Verify sequence number was assigned (should be 3 for the accept event)
        assertEquals(3L, entry.sequenceNumber());
    }

    private void offerUntilSuccess(final int length) throws InterruptedException {
        while (cmdPublication.offer(cmdBuffer, 0, length) < 0) {
            Thread.sleep(1);
        }
    }

    private void waitForProjectionState(final String quoteReqId,
                                        final RfqStateType expectedState,
                                        final long timeoutMs) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            final RfqCacheEntry entry = projection.getByQuoteReqId(quoteReqId);
            if (entry != null && entry.state() == expectedState) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Timed out waiting for projection state " + expectedState + " on " + quoteReqId);
    }
}

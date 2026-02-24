package com.bofa.equity.agents;

import com.bofa.equity.command.RfqCommandHandler;
import com.bofa.equity.event.RfqEventPublisher;
import com.bofa.equity.rfq.RfqAggregate;
import com.bofa.equity.rfq.RfqConstants;
import com.bofa.equity.sbe.*;
import com.bofa.equity.util.RfqTestCodecs;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.AgentRunner;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class SequencerAgentTest {

    private static final String CHANNEL = "aeron:ipc";

    private MediaDriver mediaDriver;
    private Aeron aeron;
    private Publication cmdPublication;
    private Subscription evtSubscription;
    private AgentRunner agentRunner;
    private RfqTestCodecs codecs;

    @BeforeEach
    void setUp() {
        final MediaDriver.Context driverCtx = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true)
                .threadingMode(ThreadingMode.SHARED);

        mediaDriver = MediaDriver.launch(driverCtx);

        final Aeron.Context aeronCtx = new Aeron.Context()
                .aeronDirectoryName(mediaDriver.aeronDirectoryName());
        aeron = Aeron.connect(aeronCtx);

        // Client publishes commands to stream 20
        cmdPublication = aeron.addPublication(CHANNEL, RfqConstants.COMMAND_STREAM_ID);
        // Client subscribes to events on stream 21
        evtSubscription = aeron.addSubscription(CHANNEL, RfqConstants.EVENT_STREAM_ID);

        // Sequencer subscribes to commands, publishes events
        final Subscription cmdSubscription = aeron.addSubscription(CHANNEL, RfqConstants.COMMAND_STREAM_ID);
        final Publication evtPublication = aeron.addPublication(CHANNEL, RfqConstants.EVENT_STREAM_ID);

        final RfqAggregate aggregate = new RfqAggregate();
        final RfqEventPublisher eventPublisher = new RfqEventPublisher(evtPublication);
        final RfqCommandHandler commandHandler = new RfqCommandHandler(aggregate, eventPublisher);
        final SequencerAgent sequencerAgent = new SequencerAgent(cmdSubscription, commandHandler);

        agentRunner = new AgentRunner(
                new SleepingMillisIdleStrategy(1),
                Throwable::printStackTrace,
                null,
                sequencerAgent);
        AgentRunner.startOnThread(agentRunner);

        codecs = new RfqTestCodecs();
    }

    @AfterEach
    void tearDown() {
        if (agentRunner != null) agentRunner.close();
        if (aeron != null) aeron.close();
        if (mediaDriver != null) mediaDriver.close();
    }

    @Test
    void fullRfqLifecycle_request_propose_accept() throws Exception {
        // Wait for publications/subscriptions to connect
        while (!cmdPublication.isConnected()) {
            Thread.sleep(10);
        }

        // 1. Send RequestQuoteCmd
        int len = codecs.encodeRequestQuoteCmd("RFQ-100", "AAPL", RfqSideType.BUY, 1000, "CLIENT-A");
        while (cmdPublication.offer(codecs.buffer(), 0, len) < 0) {
            Thread.sleep(1);
        }

        // Receive QuoteRequestedEvt
        final MutableDirectBuffer evtBuffer1 = new ExpandableArrayBuffer();
        pollUntilReceived(evtBuffer1);

        final var requestedEvt = codecs.decodeQuoteRequestedEvt(evtBuffer1);
        assertEquals(1L, requestedEvt.sequenceNumber());
        assertEquals(RfqStateType.REQUESTED, requestedEvt.state());
        assertEquals("AAPL", requestedEvt.symbol().trim());
        assertEquals(RfqSideType.BUY, requestedEvt.side());
        assertEquals(1000L, requestedEvt.quantity());

        // 2. Send ProposeQuoteCmd
        len = codecs.encodeProposeQuoteCmd("RFQ-100", "Q-100", "AAPL", 150.0, 151.0, 500, 500, 999L, "DEALER-X");
        while (cmdPublication.offer(codecs.buffer(), 0, len) < 0) {
            Thread.sleep(1);
        }

        // Receive QuoteProposedEvt
        final MutableDirectBuffer evtBuffer2 = new ExpandableArrayBuffer();
        pollUntilReceived(evtBuffer2);

        final var proposedEvt = codecs.decodeQuoteProposedEvt(evtBuffer2);
        assertEquals(2L, proposedEvt.sequenceNumber());
        assertEquals(RfqStateType.QUOTED, proposedEvt.state());
        assertEquals(150.0, proposedEvt.bidPx());
        assertEquals(151.0, proposedEvt.offerPx());

        // 3. Send AcceptQuoteCmd
        len = codecs.encodeAcceptQuoteCmd("RFQ-100", "Q-100", "CLIENT-A");
        while (cmdPublication.offer(codecs.buffer(), 0, len) < 0) {
            Thread.sleep(1);
        }

        // Receive QuoteAcceptedEvt
        final MutableDirectBuffer evtBuffer3 = new ExpandableArrayBuffer();
        pollUntilReceived(evtBuffer3);

        final var acceptedEvt = codecs.decodeQuoteAcceptedEvt(evtBuffer3);
        assertEquals(3L, acceptedEvt.sequenceNumber());
        assertEquals(RfqStateType.ACCEPTED, acceptedEvt.state());
    }

    @Test
    void sequenceNumbers_areMonotonic() throws Exception {
        while (!cmdPublication.isConnected()) {
            Thread.sleep(10);
        }

        // Send two different RFQ requests
        int len1 = codecs.encodeRequestQuoteCmd("RFQ-A", "GOOG", RfqSideType.SELL, 500, "C1");
        while (cmdPublication.offer(codecs.buffer(), 0, len1) < 0) Thread.sleep(1);

        int len2 = codecs.encodeRequestQuoteCmd("RFQ-B", "MSFT", RfqSideType.BOTH, 200, "C2");
        while (cmdPublication.offer(codecs.buffer(), 0, len2) < 0) Thread.sleep(1);

        final MutableDirectBuffer buf1 = new ExpandableArrayBuffer();
        pollUntilReceived(buf1);
        final var evt1 = codecs.decodeQuoteRequestedEvt(buf1);
        assertEquals(1L, evt1.sequenceNumber());

        final MutableDirectBuffer buf2 = new ExpandableArrayBuffer();
        pollUntilReceived(buf2);
        final var evt2 = codecs.decodeQuoteRequestedEvt(buf2);
        assertEquals(2L, evt2.sequenceNumber());
    }

    private void pollUntilReceived(final MutableDirectBuffer target) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 5000;
        final boolean[] received = {false};
        while (!received[0] && System.currentTimeMillis() < deadline) {
            evtSubscription.poll((buffer, offset, length, header) -> {
                target.putBytes(0, buffer, offset, length);
                received[0] = true;
            }, 1);
            if (!received[0]) {
                Thread.sleep(1);
            }
        }
        assertTrue(received[0], "Timed out waiting for event");
    }
}

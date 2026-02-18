package com.bofa.equity.agents;

import io.aeron.Publication;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.bofa.equity.trade.TradeCodec.encodeTrade;
import static java.util.Objects.requireNonNull;

public class SendAgent implements Agent {
    private final Logger logger = LoggerFactory.getLogger(SendAgent.class);

    // Can also use agrona UnsafeBuffer with fixed capacity for off heap usage, but in memory buffer is sufficient for current use case
    private final MutableDirectBuffer directBuffer = new ExpandableArrayBuffer();

    private final Publication publication;
    private final int sendCount;
    private int currentCountItem = 0;

    public SendAgent(final Publication publication, final int sendCount) {
        this.publication = requireNonNull(publication);
        this.sendCount = sendCount;
        logger.info(" configured to send={}", sendCount);
    }

    @Override
    public int doWork() {
        if (currentCountItem >= sendCount) {
            return 0;
        }

        if (publication.isConnected()) {
            final int encodingLengthPlusHeader = encodeTrade(directBuffer);
            if (publication.offer(directBuffer, 0, encodingLengthPlusHeader) > 0) {
                currentCountItem++;
            }
        } else {
            logger.error("Unable to publish, not connected"); // can throw exception here
        }
        return 0;
    }

    @Override
    public String roleName() {
        return "sender";
    }
}

package com.bofa.equity;

import com.bofa.equity.trade.AuditTradeCodec;
import com.bofa.equity.trade.TradeCodec;
import io.aeron.Aeron;
import io.aeron.Publication;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PublisherApp {
    private static final Logger logger = LogManager.getLogger(PublisherApp.class);

    public static void main(String[] args) {
        final String aeronDir = System.getProperty("aeron.dir");
        if (aeronDir == null) {
            throw new IllegalStateException("System property -Daeron.dir is required");
        }

        final int sendCount     = Integer.parseInt(System.getProperty("send.count", "1000000"));
        final String channel    = System.getProperty("aeron.channel", "aeron:ipc");
        final int streamId      = Integer.parseInt(System.getProperty("aeron.stream.id", "10"));
        final int auditStreamId = Integer.parseInt(System.getProperty("audit.stream.id", "11"));

        logger.info("Publisher starting: aeronDir={}, sendCount={}, channel={}, stream={}",
                aeronDir, sendCount, channel, streamId);

        final Aeron.Context aeronCtx = new Aeron.Context().aeronDirectoryName(aeronDir);

        try (Aeron aeron = Aeron.connect(aeronCtx);
             Publication publication = aeron.addPublication(channel, streamId);
             Publication auditPublication = aeron.addPublication(channel, auditStreamId)) {

            final TradeCodec tradeCodec = new TradeCodec();
            final AuditTradeCodec auditTradeCodec = new AuditTradeCodec();
            final MutableDirectBuffer buffer = new ExpandableArrayBuffer();
            final MutableDirectBuffer auditBuffer = new ExpandableArrayBuffer();

            int sent = 0;
            while (sent < sendCount) {
                final int len = tradeCodec.encodeTrade(buffer);
                final long result = publication.offer(buffer, 0, len);
                if (result > 0) {
                    sent++;
                    if (auditPublication.isConnected()) {
                        final int auditLen = auditTradeCodec.encodeAuditTrade(auditBuffer);
                        auditPublication.offer(auditBuffer, 0, auditLen);
                    }
                } else if (result == Publication.BACK_PRESSURED
                        || result == Publication.ADMIN_ACTION
                        || result == Publication.NOT_CONNECTED) {
                    Thread.onSpinWait();
                } else {
                    logger.error("Publication offer failed permanently: result={}", result);
                    break;
                }
            }

            logger.info("Publisher done: sent={}", sent);
        }
    }
}

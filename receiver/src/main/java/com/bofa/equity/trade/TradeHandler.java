package com.bofa.equity.trade;

import com.bofa.equity.position.PositionAggregator;
import com.bofa.equity.sbe.MessageHeaderDecoder;
import com.bofa.equity.sbe.TradeDecoder;
import com.bofa.equity.sbe.TradeEncoder;
import org.agrona.DirectBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.StringBuilderFormattable;

import java.util.LinkedHashMap;
import java.util.Map;

// using record as trade handling data is immutable here
public record TradeHandler(PositionAggregator positionAggregator) {
    private static final Logger logger = LogManager.getLogger(TradeHandler.class);

    private static final MessageHeaderDecoder MESSAGE_HEADER_DECODER = new MessageHeaderDecoder();
    private static final TradeDecoder TRADE_DECODER = new TradeDecoder();
    // Pre-allocated formattable: Log4j2 calls formatTo() directly on its own internal reused buffer —
    // no StringBuilder or String allocation at the log call site.
    private static final StringBuilderFormattable TRADE_LOG = sb -> TRADE_DECODER.appendTo(sb);

    // Storing the indexed trade data with attributes, however we can even store just index trade reference ids only here and data can be stored separately by another microservice
    // we can also use object pooling here, where position objects are created at startup with initial capacity and even when expanding post load factor changes
    private static final Map<String, Trade> tradeIndexedData = new LinkedHashMap<>();

    public boolean handle(final DirectBuffer directBuffer,
                          final int offset,
                          final int length,
                          final long receivedTimeNanos) {

        if (length < MessageHeaderDecoder.ENCODED_LENGTH) {
            logger.warn("Fragment too short for header: {} bytes", length);
            return false;
        }

        MESSAGE_HEADER_DECODER.wrap(directBuffer, offset);

        if (MESSAGE_HEADER_DECODER.schemaId() != TradeEncoder.SCHEMA_ID) {
            return false;
        }

        // Lookup the applicable flyweight to decode this type of message based on templateId and version.
        final int templateId = MESSAGE_HEADER_DECODER.templateId();
        if (templateId != TradeEncoder.TEMPLATE_ID) {
            throw new IllegalStateException("Template ids do not match");
        }

        final int requiredLength = MessageHeaderDecoder.ENCODED_LENGTH + MESSAGE_HEADER_DECODER.blockLength();
        if (length < requiredLength) {
            logger.warn("Fragment too short for message block: {} bytes, expected at least {}", length, requiredLength);
            return false;
        }

        decode(TRADE_DECODER, directBuffer, offset, MESSAGE_HEADER_DECODER, receivedTimeNanos);

        // TBD, can copying trade encoder values to record Trade object for indexed data
        // tradeIndex.put(TRADE_DECODER.referenceId(), new Trade())
        return true;
    }

    private void decode(final TradeDecoder trade,
                        final DirectBuffer directBuffer,
                        final int offset,
                        final MessageHeaderDecoder headerDecoder,
                        final long receivedTimeNanos) {
        trade.wrapAndApplyHeader(directBuffer, offset, headerDecoder);
        // validation of trade fields can be done here

        logger.debug("Handling {}", TRADE_LOG);
        positionAggregator.aggregate(trade, receivedTimeNanos);
    }

}

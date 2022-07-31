package com.bofa.equity.trade;

import com.bofa.equity.position.PositionAggregator;
import com.bofa.equity.sbe.MessageHeaderDecoder;
import com.bofa.equity.sbe.TradeDecoder;
import com.bofa.equity.sbe.TradeEncoder;
import org.agrona.DirectBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

// using record as trade handling data is immutable here
public record TradeHandler(PositionAggregator positionAggregator) {
    private static final Logger logger = LoggerFactory.getLogger(TradeHandler.class);

    private static final MessageHeaderDecoder MESSAGE_HEADER_DECODER = new MessageHeaderDecoder();
    private static final TradeDecoder TRADE_DECODER = new TradeDecoder();

    // Storing the indexed trade data with attributes, however we can even store just index trade reference ids only here and data can be stored separately by another microservice
    // we can also use object pooling here, where position objects are created at startup with initial capacity and even when expanding post load factor changes
    private static final Map<String, Trade> tradeIndexedData = new LinkedHashMap<>();

    public boolean handle(final DirectBuffer directBuffer,
                          final int offset,
                          final long receivedTimeMillis) {

        MESSAGE_HEADER_DECODER.wrap(directBuffer, offset);

        if (MESSAGE_HEADER_DECODER.schemaId() != TradeEncoder.SCHEMA_ID) {
            return false;
        }

        // Lookup the applicable flyweight to decode this type of message based on templateId and version.
        final int templateId = MESSAGE_HEADER_DECODER.templateId();
        if (templateId != TradeEncoder.TEMPLATE_ID) {
            throw new IllegalStateException("Template ids do not match");
        }

        decode(TRADE_DECODER, directBuffer, offset, MESSAGE_HEADER_DECODER, receivedTimeMillis);

        // TBD, can copying trade encoder values to record Trade object for indexed data
        // tradeIndex.put(TRADE_DECODER.referenceId(), new Trade())
        return true;
    }

    private void decode(final TradeDecoder trade,
                        final DirectBuffer directBuffer,
                        final int offset,
                        final MessageHeaderDecoder headerDecoder,
                        final long receivedTimeMillis) {
        trade.wrapAndApplyHeader(directBuffer, offset, headerDecoder);
        // validation of trade fields can be done here

        logger.debug("Handling {}", trade);
        positionAggregator.aggregate(trade, receivedTimeMillis);
    }

}

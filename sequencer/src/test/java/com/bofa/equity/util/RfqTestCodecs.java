package com.bofa.equity.util;

import com.bofa.equity.sbe.*;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * SBE encode/decode utility for RFQ commands and events in tests.
 * Follows the same pattern as {@code TradeTestCodecs}.
 */
public class RfqTestCodecs {

    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final MutableDirectBuffer buffer = new ExpandableArrayBuffer();

    // Command encoders
    private final RequestQuoteCmdEncoder requestQuoteCmdEncoder = new RequestQuoteCmdEncoder();
    private final ProposeQuoteCmdEncoder proposeQuoteCmdEncoder = new ProposeQuoteCmdEncoder();
    private final AcceptQuoteCmdEncoder acceptQuoteCmdEncoder = new AcceptQuoteCmdEncoder();
    private final RejectQuoteCmdEncoder rejectQuoteCmdEncoder = new RejectQuoteCmdEncoder();
    private final CancelQuoteCmdEncoder cancelQuoteCmdEncoder = new CancelQuoteCmdEncoder();

    // Command decoders
    private final RequestQuoteCmdDecoder requestQuoteCmdDecoder = new RequestQuoteCmdDecoder();
    private final ProposeQuoteCmdDecoder proposeQuoteCmdDecoder = new ProposeQuoteCmdDecoder();
    private final AcceptQuoteCmdDecoder acceptQuoteCmdDecoder = new AcceptQuoteCmdDecoder();
    private final RejectQuoteCmdDecoder rejectQuoteCmdDecoder = new RejectQuoteCmdDecoder();
    private final CancelQuoteCmdDecoder cancelQuoteCmdDecoder = new CancelQuoteCmdDecoder();

    // Event decoders
    private final QuoteRequestedEvtDecoder quoteRequestedEvtDecoder = new QuoteRequestedEvtDecoder();
    private final QuoteProposedEvtDecoder quoteProposedEvtDecoder = new QuoteProposedEvtDecoder();
    private final QuoteAcceptedEvtDecoder quoteAcceptedEvtDecoder = new QuoteAcceptedEvtDecoder();
    private final QuoteRejectedEvtDecoder quoteRejectedEvtDecoder = new QuoteRejectedEvtDecoder();
    private final QuoteCancelledEvtDecoder quoteCancelledEvtDecoder = new QuoteCancelledEvtDecoder();

    public MutableDirectBuffer buffer() {
        return buffer;
    }

    // ---- Encode commands ----

    public int encodeRequestQuoteCmd(final String quoteReqId,
                                     final String symbol,
                                     final RfqSideType side,
                                     final long quantity,
                                     final String partyId) {
        requestQuoteCmdEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .quoteReqId(quoteReqId)
                .symbol(symbol)
                .side(side)
                .quantity(quantity)
                .partyId(partyId)
                .timestampNanos(System.nanoTime());

        return MessageHeaderEncoder.ENCODED_LENGTH + requestQuoteCmdEncoder.encodedLength();
    }

    public int encodeProposeQuoteCmd(final String quoteReqId,
                                     final String quoteId,
                                     final String symbol,
                                     final double bidPx,
                                     final double offerPx,
                                     final long bidSize,
                                     final long offerSize,
                                     final long validUntilTime,
                                     final String partyId) {
        proposeQuoteCmdEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .quoteReqId(quoteReqId)
                .quoteId(quoteId)
                .symbol(symbol)
                .bidPx(bidPx)
                .offerPx(offerPx)
                .bidSize(bidSize)
                .offerSize(offerSize)
                .validUntilTime(validUntilTime)
                .partyId(partyId)
                .timestampNanos(System.nanoTime());

        return MessageHeaderEncoder.ENCODED_LENGTH + proposeQuoteCmdEncoder.encodedLength();
    }

    public int encodeAcceptQuoteCmd(final String quoteReqId,
                                    final String quoteId,
                                    final String partyId) {
        acceptQuoteCmdEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .quoteReqId(quoteReqId)
                .quoteId(quoteId)
                .partyId(partyId)
                .timestampNanos(System.nanoTime());

        return MessageHeaderEncoder.ENCODED_LENGTH + acceptQuoteCmdEncoder.encodedLength();
    }

    public int encodeRejectQuoteCmd(final String quoteReqId,
                                    final String quoteId,
                                    final String partyId,
                                    final String reason) {
        rejectQuoteCmdEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .quoteReqId(quoteReqId)
                .quoteId(quoteId)
                .partyId(partyId)
                .timestampNanos(System.nanoTime())
                .reason(reason);

        return MessageHeaderEncoder.ENCODED_LENGTH + rejectQuoteCmdEncoder.encodedLength();
    }

    public int encodeCancelQuoteCmd(final String quoteReqId,
                                    final String partyId) {
        cancelQuoteCmdEncoder.wrapAndApplyHeader(buffer, 0, headerEncoder)
                .quoteReqId(quoteReqId)
                .partyId(partyId)
                .timestampNanos(System.nanoTime());

        return MessageHeaderEncoder.ENCODED_LENGTH + cancelQuoteCmdEncoder.encodedLength();
    }

    // ---- Decode commands (round-trip testing) ----

    public RequestQuoteCmdDecoder decodeRequestQuoteCmd() {
        headerDecoder.wrap(buffer, 0);
        requestQuoteCmdDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return requestQuoteCmdDecoder;
    }

    public ProposeQuoteCmdDecoder decodeProposeQuoteCmd() {
        headerDecoder.wrap(buffer, 0);
        proposeQuoteCmdDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);
        return proposeQuoteCmdDecoder;
    }

    // ---- Decode events ----

    public QuoteRequestedEvtDecoder decodeQuoteRequestedEvt(final MutableDirectBuffer evtBuffer) {
        headerDecoder.wrap(evtBuffer, 0);
        quoteRequestedEvtDecoder.wrapAndApplyHeader(evtBuffer, 0, headerDecoder);
        return quoteRequestedEvtDecoder;
    }

    public QuoteProposedEvtDecoder decodeQuoteProposedEvt(final MutableDirectBuffer evtBuffer) {
        headerDecoder.wrap(evtBuffer, 0);
        quoteProposedEvtDecoder.wrapAndApplyHeader(evtBuffer, 0, headerDecoder);
        return quoteProposedEvtDecoder;
    }

    public QuoteAcceptedEvtDecoder decodeQuoteAcceptedEvt(final MutableDirectBuffer evtBuffer) {
        headerDecoder.wrap(evtBuffer, 0);
        quoteAcceptedEvtDecoder.wrapAndApplyHeader(evtBuffer, 0, headerDecoder);
        return quoteAcceptedEvtDecoder;
    }

    public QuoteRejectedEvtDecoder decodeQuoteRejectedEvt(final MutableDirectBuffer evtBuffer) {
        headerDecoder.wrap(evtBuffer, 0);
        quoteRejectedEvtDecoder.wrapAndApplyHeader(evtBuffer, 0, headerDecoder);
        return quoteRejectedEvtDecoder;
    }

    public QuoteCancelledEvtDecoder decodeQuoteCancelledEvt(final MutableDirectBuffer evtBuffer) {
        headerDecoder.wrap(evtBuffer, 0);
        quoteCancelledEvtDecoder.wrapAndApplyHeader(evtBuffer, 0, headerDecoder);
        return quoteCancelledEvtDecoder;
    }
}

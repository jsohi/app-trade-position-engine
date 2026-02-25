package com.bofa.equity.rfq;

public final class RfqConstants {

    private RfqConstants() {}

    /** Aeron stream ID for RFQ commands (inbound to sequencer). */
    public static final int COMMAND_STREAM_ID = 20;

    /** Aeron stream ID for RFQ events (outbound from sequencer). */
    public static final int EVENT_STREAM_ID = 21;

    /** SBE schema ID shared by all messages in this schema. */
    public static final int SCHEMA_ID = 100;

    /** Maximum fragments polled per agent duty cycle. */
    public static final int FRAGMENT_LIMIT = 100;

    /** Maximum length (chars) for rejection reason text, matching SBE schema maxValue. */
    public static final int MAX_REASON_LENGTH = 1024;
}

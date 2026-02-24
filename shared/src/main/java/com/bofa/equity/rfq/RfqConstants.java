package com.bofa.equity.rfq;

public final class RfqConstants {

    private RfqConstants() {}

    /** Aeron stream ID for RFQ commands (inbound to sequencer). */
    public static final int COMMAND_STREAM_ID = 20;

    /** Aeron stream ID for RFQ events (outbound from sequencer). */
    public static final int EVENT_STREAM_ID = 21;
}

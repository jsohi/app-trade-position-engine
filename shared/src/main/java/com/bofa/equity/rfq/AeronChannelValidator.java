package com.bofa.equity.rfq;

/**
 * Shared utility for validating Aeron channel strings.
 * Extracted to avoid duplication across PublisherApp, ReceiverApp, and SequencerApp.
 */
public final class AeronChannelValidator {

    private AeronChannelValidator() {}

    /**
     * Validates that the given channel is either {@code aeron:ipc} or a UDP endpoint.
     *
     * @param channel the Aeron channel string
     * @return the validated channel
     * @throws IllegalArgumentException if the channel format is invalid
     */
    public static String validateChannel(final String channel) {
        if ("aeron:ipc".equals(channel) || channel.startsWith("aeron:udp?endpoint=")) {
            return channel;
        }
        throw new IllegalArgumentException(
                "Invalid aeron.channel '" + channel + "': must be 'aeron:ipc' or 'aeron:udp?endpoint=<host>:<port>'");
    }
}

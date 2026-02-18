package com.bofa.equity.trade;

import com.bofa.equity.sbe.SideType;
import org.agrona.concurrent.IdGenerator;
import org.agrona.concurrent.SnowflakeIdGenerator;

import java.util.concurrent.ThreadLocalRandom;

import static java.util.Objects.requireNonNull;

public enum TradeEncoderHelper {
    ;

    // simple implementation but can be extended to have UTF/ASCII chars
    public static final String ALPHA_NUMS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final IdGenerator idGenerator = new SnowflakeIdGenerator(1);
    private static final CharSequence ID_PREFIX = "TRD";

    // append only, no objects created
    static StringBuilder nextReferenceId(final StringBuilder referenceId) {
        requireNonNull(referenceId).setLength(0);
        return referenceId.append(ID_PREFIX).append(idGenerator.nextId());
    }

    static int randomInt(final int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }

    static long randomQuantity() {
        return ThreadLocalRandom.current().nextLong(1000);
    }

    static SideType randomSide() {
        return ThreadLocalRandom.current().nextBoolean() ? SideType.B : SideType.S;
    }

    static double randomPrice() {
        return ThreadLocalRandom.current().nextDouble(200d);
    }

    static StringBuilder randomDescription(final StringBuilder descriptionTemp) {
        descriptionTemp.setLength(0);
        for (int i = 0; i < descriptionTemp.capacity(); i++) {
            descriptionTemp.append(ALPHA_NUMS.charAt(randomInt(ALPHA_NUMS.length())));
        }
        return descriptionTemp; // returning for chaining, not required to return at its passed by reference
    }
}

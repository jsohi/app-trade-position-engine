package com.bofa.equity.cache;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Object2IntHashMap;

public class DefaultCache implements Cache {

    // agorna primitive hashmaps without using autoboxing, which creates garbage
    private final Int2ObjectHashMap<String> accountIds;
    private final Int2ObjectHashMap<String> securityIds;
    private final Object2IntHashMap<String> accountStrIds;
    private final Object2IntHashMap<String> securityStrIds;

    //  ideally this cache will be populated by some data store, but adding dummy values for now
    public DefaultCache() {
        // good enough initial capacity. Garbage created, but not used post startup
        final StringBuilder mapperTemp = new StringBuilder(16);

        final int accountCount = 10; // hard coded but can be moved to some variable
        this.accountIds = new Int2ObjectHashMap<>(accountCount * 2, 0.65F);
        this.accountStrIds = new Object2IntHashMap<>(accountCount * 2, 0.65F, 0);
        for (int i = 1; i <= accountCount; i++) {
            mapperTemp.setLength(0);
            final String strValue = mapperTemp.append("Acc").append(i).toString();
            accountIds.put(i, strValue);
            accountStrIds.put(strValue, i);
        }

        final int securityCount = 2000; // hard coded but can be moved to some variable
        this.securityIds = new Int2ObjectHashMap<>(2000 * 2, 0.65F);
        this.securityStrIds = new Object2IntHashMap<>(2000 * 2, 0.65F, 0);
        for (int i = 1; i <= 2000; i++) {
            mapperTemp.setLength(0);

            // below logic can be improved, but keeping simple for now
            if (i < 10) {
                final String strValue = mapperTemp.append("000").append(i).append(".AX").toString();
                securityIds.put(i, strValue);
                securityStrIds.put(strValue, i);
            } else if (i < 100) {
                final String strValue = mapperTemp.append("00").append(i).append(".AX").toString();
                securityIds.put(i, strValue);
                securityStrIds.put(strValue, i);
            } else if (i < 1000) {
                final String strValue = mapperTemp.append("0").append(i).append(".AX").toString();
                securityIds.put(i, strValue);
                securityStrIds.put(strValue, i);
            } else {
                final String strValue = mapperTemp.append(i).append(".AX").toString();
                securityIds.put(i, strValue);
                securityStrIds.put(strValue, i);
            }
        }
    }

    @Override
    public Int2ObjectHashMap<String> accountIds() {
        return accountIds;
    }

    @Override
    public Int2ObjectHashMap<String> securityIds() {
        return securityIds;
    }

    @Override
    public Object2IntHashMap<String> accountStrIds() {
        return accountStrIds;
    }

    @Override
    public Object2IntHashMap<String> securityStrIds() {
        return securityStrIds;
    }
}

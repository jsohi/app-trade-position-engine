package com.bofa.equity.cache;

import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Object2IntHashMap;

public interface Cache {

    // storing int to account/security ids, as we will be passing int values over wire using SBE, not Strings which takes more bytes

    Int2ObjectHashMap<String> accountIds();

    Int2ObjectHashMap<String> securityIds();

    Object2IntHashMap<String> accountStrIds();

    Object2IntHashMap<String> securityStrIds();

    static Cache defaultCache() {
        return new DefaultCache();
    }

    // Can also cache String account/security ids to Integer here for reverse lookup
}

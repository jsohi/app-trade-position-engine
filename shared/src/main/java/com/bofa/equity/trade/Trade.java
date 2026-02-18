package com.bofa.equity.trade;

import java.util.StringJoiner;

public record Trade(String security, boolean sideBuy, String account, int quantity, double price,
                    String reference, String description) {

    @Override // can be made without creating object by passing a temp string builder and append fields.
    public String toString() {
        return new StringJoiner(", ", Trade.class.getSimpleName() + "[", "]")
                .add("security='" + security + "'")
                .add("sideBuy=" + sideBuy)
                .add("account='" + account + "'")
                .add("quantity=" + quantity)
                .add("price=" + price)
                .add("reference='" + reference + "'")
                .add("description='" + description + "'")
                .toString();
    }
}

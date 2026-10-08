package com.dispatch.rule;

import java.math.BigDecimal;

public enum ComparisonOperator {
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<=");

    private final String symbol;

    ComparisonOperator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public boolean test(BigDecimal actual, BigDecimal threshold) {
        int comparison = actual.compareTo(threshold);
        return switch (this) {
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
        };
    }
}

package com.travelplatform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one place money is rounded. Rupees with two decimals (paise), half-up - the rounding
 * people expect on a bill, and what UPI apps display.
 *
 * <p>Quantities that are measurements (kilometres from the routing API, hours, persons) arrive as
 * numbers; {@link BigDecimal#valueOf(double)} takes their shortest decimal form ("461.237"), not
 * the binary approximation {@code new BigDecimal(double)} would expose.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Money() {
    }

    /** Rounded to whole paise. */
    public static BigDecimal paise(BigDecimal amount) {
        return amount.setScale(SCALE, ROUNDING);
    }

    /** rate x quantity, rounded once at the end - e.g. price per km x distance. */
    public static BigDecimal times(BigDecimal rate, double quantity) {
        return paise(rate.multiply(BigDecimal.valueOf(quantity)));
    }

    /** rate x a whole quantity - e.g. price per hour x hours, price per person x persons. */
    public static BigDecimal times(BigDecimal rate, long quantity) {
        return paise(rate.multiply(BigDecimal.valueOf(quantity)));
    }

    /** A price an owner typed in: must be above zero, kept to the paisa. */
    public static BigDecimal positivePrice(BigDecimal price, String what) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException(what + " must be greater than zero");
        }
        return paise(price);
    }
}

package com.example.wallet.common;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An amount in minor units (paise for INR) plus its ISO-4217 currency.
 *
 * <p>WHY long + addExact: floating point can't represent 0.10 exactly, and a plain {@code a + b}
 * on longs silently wraps around on overflow (a huge credit becomes a huge debit). The
 * {@code Math.*Exact} methods throw ArithmeticException instead, and a loud failure is the
 * only acceptable outcome for money.
 *
 * <p>Arithmetic between different currencies is refused: 100 INR + 100 USD is not 200 of anything.
 * Money may be negative (system accounts can be); whether a balance may go negative is a
 * business rule enforced elsewhere, not a property of the value.
 */
public record Money(long amountMinor, String currency) {

    private static final Pattern ISO_CODE = Pattern.compile("^[A-Z]{3}$");

    public Money {
        Objects.requireNonNull(currency, "currency");
        if (!ISO_CODE.matcher(currency).matches()) {
            throw new IllegalArgumentException("Currency must be a 3-letter uppercase ISO code: " + currency);
        }
        Currency.getInstance(currency); // rejects well-formed but unknown codes like "ABC"
    }

    public static Money of(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    public static Money zero(String currency) {
        return new Money(0, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }

    public Money negate() {
        return new Money(Math.negateExact(amountMinor), currency);
    }

    public boolean isPositive() {
        return amountMinor > 0;
    }

    public boolean isNegative() {
        return amountMinor < 0;
    }

    public boolean isZero() {
        return amountMinor == 0;
    }

    public boolean isLessThan(Money other) {
        requireSameCurrency(other);
        return amountMinor < other.amountMinor;
    }

    /** "1000.50 INR": for logs and statements, not for arithmetic. */
    public String toDecimalString() {
        int digits = Math.max(Currency.getInstance(currency).getDefaultFractionDigits(), 0);
        return BigDecimal.valueOf(amountMinor, digits).toPlainString() + " " + currency;
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }
}

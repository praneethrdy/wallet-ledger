package com.example.wallet.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.example.wallet.common.error.ErrorCode;

class MoneyTest {

    @Test
    void addsAndSubtractsInMinorUnits() {
        Money a = Money.of(10_050, "INR"); // ₹100.50
        Money b = Money.of(2_575, "INR");  // ₹25.75

        assertThat(a.plus(b)).isEqualTo(Money.of(12_625, "INR"));
        assertThat(a.minus(b)).isEqualTo(Money.of(7_475, "INR"));
        assertThat(b.minus(a)).isEqualTo(Money.of(-7_475, "INR"));
    }

    @Test
    void isAValueObject() {
        assertThat(Money.of(100, "INR")).isEqualTo(Money.of(100, "INR")).hasSameHashCodeAs(Money.of(100, "INR"));
        assertThat(Money.of(100, "INR")).isNotEqualTo(Money.of(100, "USD"));
    }

    @Test
    void overflowThrowsInsteadOfWrappingAround() {
        Money max = Money.of(Long.MAX_VALUE, "INR");
        Money min = Money.of(Long.MIN_VALUE, "INR");

        assertThatThrownBy(() -> max.plus(Money.of(1, "INR"))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> min.minus(Money.of(1, "INR"))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(min::negate).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void refusesArithmeticAcrossCurrencies() {
        assertThatThrownBy(() -> Money.of(100, "INR").plus(Money.of(100, "USD")))
                .isInstanceOf(CurrencyMismatchException.class)
                .satisfies(e -> assertThat(((CurrencyMismatchException) e).code()).isEqualTo(ErrorCode.CURRENCY_MISMATCH));
        assertThatThrownBy(() -> Money.of(100, "INR").minus(Money.of(100, "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> Money.of(100, "INR").isLessThan(Money.of(100, "USD")))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inr", "IN", "INRR", "", "ABC", "12A"})
    void rejectsInvalidCurrency(String currency) {
        assertThatThrownBy(() -> Money.of(1, currency)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> Money.of(1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void signPredicatesAndComparison() {
        assertThat(Money.zero("INR").isZero()).isTrue();
        assertThat(Money.of(1, "INR").isPositive()).isTrue();
        assertThat(Money.of(-1, "INR").isNegative()).isTrue();
        assertThat(Money.of(5, "INR").negate()).isEqualTo(Money.of(-5, "INR"));
        assertThat(Money.of(99, "INR").isLessThan(Money.of(100, "INR"))).isTrue();
        assertThat(Money.of(100, "INR").isLessThan(Money.of(100, "INR"))).isFalse();
    }

    @Test
    void formatsUsingTheCurrencysMinorUnitExponent() {
        assertThat(Money.of(100_050, "INR").toDecimalString()).isEqualTo("1000.50 INR");
        assertThat(Money.of(-5, "INR").toDecimalString()).isEqualTo("-0.05 INR");
        assertThat(Money.of(1_500, "JPY").toDecimalString()).isEqualTo("1500 JPY"); // yen has no minor unit
    }
}

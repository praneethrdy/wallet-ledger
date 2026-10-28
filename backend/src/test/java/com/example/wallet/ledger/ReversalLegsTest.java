package com.example.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.wallet.common.Money;

class ReversalLegsTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @Test
    void swapsEveryDirectionAndKeepsAccountsAndAmounts() {
        List<Posting.Leg> original = List.of(
                Posting.Leg.debit(a, Money.of(1_000, "INR")),
                Posting.Leg.credit(b, Money.of(600, "INR")),
                Posting.Leg.credit(c, Money.of(400, "INR")));

        List<Posting.Leg> mirrored = ReversalLegs.mirror(original);

        assertThat(mirrored).containsExactly(
                Posting.Leg.credit(a, Money.of(1_000, "INR")),
                Posting.Leg.debit(b, Money.of(600, "INR")),
                Posting.Leg.debit(c, Money.of(400, "INR")));
    }

    @Test
    void originalPlusReversalNetsToZeroOnEveryAccount() {
        List<Posting.Leg> original = List.of(
                Posting.Leg.debit(a, Money.of(250, "INR")),
                Posting.Leg.credit(b, Money.of(250, "INR")));

        Map<UUID, Long> net = new HashMap<>();
        original.forEach(l -> net.merge(l.accountId(), l.signedAmount(), Long::sum));
        ReversalLegs.mirror(original).forEach(l -> net.merge(l.accountId(), l.signedAmount(), Long::sum));

        assertThat(net.values()).containsOnly(0L);
    }

    @Test
    void mirroredPostingIsStillBalanced() {
        List<Posting.Leg> original = List.of(
                Posting.Leg.debit(a, Money.of(70, "USD")),
                Posting.Leg.credit(b, Money.of(70, "USD")));

        LedgerService.assertBalanced(ReversalLegs.mirror(original)); // throws if not
    }
}

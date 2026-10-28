package com.example.wallet.ledger;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.wallet.common.error.ApiException;
import com.example.wallet.common.error.ErrorCode;

/**
 * Looks up the system accounts seeded by V3. They never change at runtime, so they're cached
 * after the first lookup.
 */
@Component
public class SystemAccounts {

    private record Key(SystemAccountCode code, String currency) {
    }

    private final AccountRepository accounts;
    private final Map<Key, UUID> cache = new ConcurrentHashMap<>();

    public SystemAccounts(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public UUID id(SystemAccountCode code, String currency) {
        UUID id = cache().get(new Key(code, currency));
        if (id == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported currency: " + currency);
        }
        return id;
    }

    /** A currency is supported exactly when it has an EXTERNAL_FUNDING account. */
    public Set<String> supportedCurrencies() {
        Set<String> currencies = new TreeSet<>();
        cache().keySet().stream()
                .filter(k -> k.code() == SystemAccountCode.EXTERNAL_FUNDING)
                .forEach(k -> currencies.add(k.currency()));
        return currencies;
    }

    public void requireSupported(String currency) {
        if (!supportedCurrencies().contains(currency)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported currency: " + currency);
        }
    }

    private Map<Key, UUID> cache() {
        if (cache.isEmpty()) {
            for (Account a : accounts.findByType(AccountType.SYSTEM)) {
                cache.put(new Key(a.getSystemCode(), a.getCurrency()), a.getId());
            }
        }
        return cache;
    }
}

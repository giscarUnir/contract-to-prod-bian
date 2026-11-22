package demo.bian.adapter.out.memory;

import demo.bian.application.AccountStore;
import demo.bian.domain.Account;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

@ApplicationScoped
public class InMemoryAccountStore implements AccountStore {
    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public String nextReference() {
        return String.format(Locale.ROOT, "CA-%010d", sequence.incrementAndGet());
    }

    @Override
    public void create(Account account) {
        if (accounts.putIfAbsent(account.reference(), account) != null) {
            throw new IllegalStateException("Referencia de cuenta duplicada.");
        }
    }

    @Override
    public Optional<Account> find(String reference) {
        return Optional.ofNullable(accounts.get(reference));
    }

    @Override
    public Optional<Account> update(String reference, UnaryOperator<Account> operation) {
        return Optional.ofNullable(accounts.computeIfPresent(reference, (key, account) -> operation.apply(account)));
    }
}

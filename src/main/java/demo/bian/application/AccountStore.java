package demo.bian.application;

import demo.bian.domain.Account;

import java.util.Optional;
import java.util.function.UnaryOperator;

public interface AccountStore {
    String nextReference();
    void create(Account account);
    Optional<Account> find(String reference);
    Optional<Account> update(String reference, UnaryOperator<Account> operation);
}

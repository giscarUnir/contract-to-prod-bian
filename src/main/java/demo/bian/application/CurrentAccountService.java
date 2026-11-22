package demo.bian.application;

import demo.bian.domain.Account;
import demo.bian.domain.Account.Control;
import demo.bian.domain.Account.Currency;
import demo.bian.domain.Account.Payment;
import demo.bian.domain.AccountError;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.UUID;

import static demo.bian.domain.AccountError.Code.CUENTA_NO_EXISTE;
import static demo.bian.domain.AccountError.Code.PAGO_NO_EXISTE;

@ApplicationScoped
public class CurrentAccountService implements CurrentAccounts {
    private final AccountStore store;

    public CurrentAccountService(AccountStore store) {
        this.store = store;
    }

    @Override
    public Account open(String customer, Currency currency, BigDecimal deposit) {
        Account account = Account.open(store.nextReference(), customer, currency, deposit == null ? BigDecimal.ZERO : deposit);
        store.create(account);
        return account;
    }

    @Override
    public Account find(String reference) {
        return store.find(reference).orElseThrow(() -> new AccountError(CUENTA_NO_EXISTE));
    }

    @Override
    public Account control(String reference, Control action) {
        return store.update(reference, account -> account.control(action))
                .orElseThrow(() -> new AccountError(CUENTA_NO_EXISTE));
    }

    @Override
    public Payment pay(String reference, UUID key, BigDecimal amount, Currency currency, String payee) {
        return store.update(reference, account -> account.pay(key, amount, currency, payee))
                .orElseThrow(() -> new AccountError(CUENTA_NO_EXISTE))
                .payments().get(key);
    }

    @Override
    public Payment findPayment(String reference, String paymentReference) {
        return store.find(reference).flatMap(account -> account.payment(paymentReference))
                .orElseThrow(() -> new AccountError(PAGO_NO_EXISTE));
    }
}

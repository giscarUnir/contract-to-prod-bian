package demo.bian.application;

import demo.bian.domain.Account;
import demo.bian.domain.Account.Control;
import demo.bian.domain.Account.Currency;
import demo.bian.domain.Account.Payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface CurrentAccounts {
    Account open(String customer, Currency currency, BigDecimal deposit);
    Account find(String reference);
    Account control(String reference, Control action);
    Payment pay(String reference, UUID key, BigDecimal amount, Currency currency, String payee);
    Payment findPayment(String reference, String paymentReference);
}

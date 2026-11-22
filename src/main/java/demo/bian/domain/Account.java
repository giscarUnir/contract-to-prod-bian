package demo.bian.domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static demo.bian.domain.AccountError.Code.*;

public record Account(String reference, String customer, Currency currency, Status status,
                      BigDecimal balance, Map<UUID, Payment> payments) {
    public enum Currency { PEN, USD }
    public enum Status { ACTIVE, SUSPENDED }
    public enum Control { SUSPEND, RESUME }

    public record Payment(String reference, BigDecimal amount, Currency currency, String payee) {}

    public Account {
        payments = Map.copyOf(payments);
    }

    public static Account open(String reference, String customer, Currency currency, BigDecimal deposit) {
        validateAmount(deposit, BigDecimal.ZERO);
        if (customer.isBlank()) {
            throw new AccountError(SOLICITUD_INVALIDA);
        }
        return new Account(reference, customer, currency, Status.ACTIVE, deposit, Map.of());
    }

    public Account control(Control action) {
        Status next = switch (action) {
            case SUSPEND -> Status.SUSPENDED;
            case RESUME -> Status.ACTIVE;
        };
        if (status == next) {
            throw new AccountError(TRANSICION_INVALIDA);
        }
        return new Account(reference, customer, currency, next, balance, payments);
    }

    public Account pay(UUID key, BigDecimal amount, Currency paymentCurrency, String payee) {
        validateAmount(amount, new BigDecimal("0.01"));
        if (payee.isBlank()) {
            throw new AccountError(SOLICITUD_INVALIDA);
        }

        Payment previous = payments.get(key);
        if (previous != null) {
            if (previous.amount().compareTo(amount) != 0 || previous.currency() != paymentCurrency
                    || !previous.payee().equals(payee)) {
                throw new AccountError(CLAVE_REUTILIZADA);
            }
            return this;
        }

        if (status == Status.SUSPENDED) {
            throw new AccountError(CUENTA_SUSPENDIDA);
        }
        if (currency != paymentCurrency) {
            throw new AccountError(MONEDA_DISTINTA);
        }
        if (balance.compareTo(amount) < 0) {
            throw new AccountError(SALDO_INSUFICIENTE);
        }

        String paymentReference;
        do {
            paymentReference = "PAY-" + UUID.randomUUID().toString().substring(0, 8);
        } while (payment(paymentReference).isPresent());

        var updated = new HashMap<>(payments);
        updated.put(key, new Payment(paymentReference, amount, paymentCurrency, payee));
        return new Account(reference, customer, currency, status, balance.subtract(amount), updated);
    }

    public Optional<Payment> payment(String paymentReference) {
        return payments.values().stream().filter(payment -> payment.reference().equals(paymentReference)).findFirst();
    }

    private static void validateAmount(BigDecimal amount, BigDecimal minimum) {
        if (amount.compareTo(minimum) < 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new AccountError(SOLICITUD_INVALIDA);
        }
    }
}

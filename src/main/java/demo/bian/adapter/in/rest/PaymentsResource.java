package demo.bian.adapter.in.rest;

import demo.bian.application.CurrentAccounts;
import demo.bian.domain.Account;
import demo.bian.generated.api.PaymentsApi;
import demo.bian.generated.model.Currency;
import demo.bian.generated.model.Payments;
import demo.bian.generated.model.PaymentsInitiate;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

public class PaymentsResource implements PaymentsApi {
    private final CurrentAccounts accounts;

    public PaymentsResource(CurrentAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public Response initiateCurrentAccountPayments(String reference, UUID key, PaymentsInitiate request) {
        var payment = accounts.pay(reference, key, request.getPaymentAmount(),
                Account.Currency.valueOf(request.getPaymentCurrency().name()), request.getPayeeReference());
        return Response.status(Response.Status.CREATED).entity(dto(payment)).build();
    }

    @Override
    public Response retrieveCurrentAccountPayments(String reference, String paymentReference) {
        return Response.ok(dto(accounts.findPayment(reference, paymentReference))).build();
    }

    private static Payments dto(Account.Payment payment) {
        return new Payments()
                .paymentsReference(payment.reference())
                .paymentAmount(payment.amount())
                .paymentCurrency(Currency.valueOf(payment.currency().name()))
                .payeeReference(payment.payee())
                .paymentStatus(Payments.PaymentStatusEnum.REGISTERED);
    }
}

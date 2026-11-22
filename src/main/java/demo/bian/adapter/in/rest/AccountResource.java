package demo.bian.adapter.in.rest;

import demo.bian.application.CurrentAccounts;
import demo.bian.domain.Account;
import demo.bian.generated.api.CurrentAccountFacilityApi;
import demo.bian.generated.model.ControlRequest;
import demo.bian.generated.model.Currency;
import demo.bian.generated.model.CurrentAccountFacility;
import demo.bian.generated.model.CurrentAccountFacilityInitiate;
import jakarta.ws.rs.core.Response;

public class AccountResource implements CurrentAccountFacilityApi {
    private final CurrentAccounts accounts;

    public AccountResource(CurrentAccounts accounts) {
        this.accounts = accounts;
    }

    @Override
    public Response initiateCurrentAccountFacility(CurrentAccountFacilityInitiate request) {
        Account created = accounts.open(request.getCustomerReference(),
                Account.Currency.valueOf(request.getAccountCurrency().name()), request.getInitialDepositAmount());
        return Response.status(Response.Status.CREATED).entity(dto(created)).build();
    }

    @Override
    public Response retrieveCurrentAccountFacility(String reference) {
        return Response.ok(dto(accounts.find(reference))).build();
    }

    @Override
    public Response controlCurrentAccountFacility(String reference, ControlRequest request) {
        var action = Account.Control.valueOf(request.getControlActionType().name());
        return Response.ok(dto(accounts.control(reference, action))).build();
    }

    private static CurrentAccountFacility dto(Account account) {
        return new CurrentAccountFacility()
                .currentAccountFacilityReference(account.reference())
                .customerReference(account.customer())
                .accountCurrency(Currency.valueOf(account.currency().name()))
                .accountStatus(CurrentAccountFacility.AccountStatusEnum.valueOf(account.status().name()))
                .accountBalance(account.balance());
    }
}

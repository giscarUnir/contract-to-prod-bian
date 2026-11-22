package demo.bian;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class CurrentAccountAcceptanceTest {
    private static final String ACCOUNTS = "/CurrentAccount";
    private static final String PAY = ACCOUNTS + "/{id}/Payments/Initiate";
    private static final String FIND = ACCOUNTS + "/{id}/Retrieve";
    private static final String CONTROL = ACCOUNTS + "/{id}/Control";
    private static final String FIND_PAYMENT = ACCOUNTS + "/{id}/Payments/{payment}/Retrieve";

    @Test
    void opensAndRetrievesAnAccount() {
        String id = open(100);
        given().get(FIND, id).then().statusCode(200)
                .body("CustomerReference", equalTo("DEMO-CLIENTE"))
                .body("AccountCurrency", equalTo("PEN"))
                .body("AccountStatus", equalTo("Active"))
                .body("AccountBalance", equalTo(100));

        given().contentType(JSON).body(Map.of("CustomerReference", "DEMO", "AccountCurrency", "USD"))
                .post(ACCOUNTS + "/Initiate").then().statusCode(201)
                .body("AccountBalance", equalTo(0));
    }

    @Test
    void rejectsUnknownAccounts() {
        given().get(FIND, "desconocida").then().statusCode(404)
                .body("code", equalTo("CUENTA_NO_EXISTE"));
        control("desconocida", "Suspend").statusCode(404)
                .body("code", equalTo("CUENTA_NO_EXISTE"));
        pay("desconocida", UUID.randomUUID(), "PEN", 1).statusCode(404)
                .body("code", equalTo("CUENTA_NO_EXISTE"));
    }

    @Test
    void suspendsAndReactivatesWithoutAllowingPaymentsWhileSuspended() {
        String id = open(100);
        control(id, "Suspend").statusCode(200).body("AccountStatus", equalTo("Suspended"));
        control(id, "Suspend").statusCode(422).body("code", equalTo("TRANSICION_INVALIDA"));
        pay(id, UUID.randomUUID(), "PEN", 30).statusCode(422).body("code", equalTo("CUENTA_SUSPENDIDA"));
        control(id, "Resume").statusCode(200).body("AccountStatus", equalTo("Active"));
    }

    @Test
    void rejectsWrongCurrencyAndOverdraftWithoutChangingBalance() {
        String id = open(100);
        pay(id, UUID.randomUUID(), "USD", 30).statusCode(422).body("code", equalTo("MONEDA_DISTINTA"));
        pay(id, UUID.randomUUID(), "PEN", 101).statusCode(422).body("code", equalTo("SALDO_INSUFICIENTE"));
        given().get(FIND, id).then().statusCode(200).body("AccountBalance", equalTo(100));
    }

    @Test
    void createsAndRetrievesAPaymentOnlyWithinItsAccount() {
        String account = open(100);
        String payment = pay(account, UUID.randomUUID(), "PEN", 30).statusCode(201)
                .body("PaymentsReference", matchesPattern("PAY-[a-f0-9]{8}"))
                .body("PaymentStatus", equalTo("Registered"))
                .extract().path("PaymentsReference");
        given().get(FIND, account).then().statusCode(200).body("AccountBalance", equalTo(70));
        given().get(FIND_PAYMENT, account, payment).then().statusCode(200)
                .body("PaymentsReference", equalTo(payment))
                .body("PaymentAmount", equalTo(30))
                .body("PayeeReference", equalTo("DEMO-BENEFICIARIO"));
        given().get(FIND_PAYMENT, open(0), payment).then().statusCode(404)
                .body("code", equalTo("PAGO_NO_EXISTE"));
        given().get(FIND_PAYMENT, account, "no-existe").then().statusCode(404)
                .body("code", equalTo("PAGO_NO_EXISTE"));
    }

    @Test
    void debitsOnceForConcurrentRetriesAndRejectsChangedPayload() throws Exception {
        String id = open(100);
        UUID key = UUID.randomUUID();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> reference(id, key));
            var second = executor.submit(() -> reference(id, key));
            assertEquals(first.get(), second.get());
            assertEquals(first.get(), reference(id, key));
        }
        pay(id, key, "PEN", 31).statusCode(422).body("code", equalTo("CLAVE_REUTILIZADA"));
        given().get(FIND, id).then().statusCode(200).body("AccountBalance", equalTo(70));
    }

    @Test
    void rejectsInvalidRequestsInTheContractErrorFormat() {
        given().contentType(JSON).body(Map.of("AccountCurrency", "PEN"))
                .post(ACCOUNTS + "/Initiate").then().statusCode(400)
                .body("code", equalTo("SOLICITUD_INVALIDA"));
        String id = open(100);
        given().contentType(JSON).body(Map.of("PaymentAmount", 1, "PaymentCurrency", "PEN", "PayeeReference", "DEMO"))
                .header("Idempotency-Key", "inválido").post(PAY, id).then().statusCode(400)
                .body("code", equalTo("SOLICITUD_INVALIDA"));
        pay(id, UUID.randomUUID(), "PEN", 0.001).statusCode(400).body("code", equalTo("SOLICITUD_INVALIDA"));
    }

    private static String open(int deposit) {
        return given().contentType(JSON)
                .body(Map.of("CustomerReference", "DEMO-CLIENTE", "AccountCurrency", "PEN", "InitialDepositAmount", deposit))
                .post(ACCOUNTS + "/Initiate").then().statusCode(201)
                .body("CurrentAccountFacilityReference", matchesPattern("CA-[0-9]{10}"))
                .extract().path("CurrentAccountFacilityReference");
    }

    private static ValidatableResponse pay(String account, UUID key, String currency, Number amount) {
        return given().contentType(JSON).header("Idempotency-Key", key)
                .body(Map.of("PaymentAmount", amount, "PaymentCurrency", currency, "PayeeReference", "DEMO-BENEFICIARIO"))
                .post(PAY, account).then();
    }

    private static String reference(String id, UUID key) {
        return pay(id, key, "PEN", 30).statusCode(201).extract().path("PaymentsReference");
    }

    private static ValidatableResponse control(String id, String action) {
        return given().contentType(JSON).body(Map.of("ControlActionType", action))
                .put(CONTROL, id).then();
    }
}

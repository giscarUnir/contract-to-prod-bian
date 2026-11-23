#!/bin/sh
set -eu

base=${BASE_URL:-http://current-account:8080}
body=$(mktemp)
trap 'rm -f "$body"' EXIT

check() {
    expected=$1
    method=$2
    path=$3
    assertion=$4
    shift 4
    status=$(curl --silent --show-error --connect-timeout 3 --max-time 5 \
        --output "$body" --write-out '%{http_code}' --request "$method" \
        --header 'Content-Type: application/json' "$@" "$base$path")
    if [ "$status" != "$expected" ] || ! jq -e "$assertion" "$body" >/dev/null; then
        printf 'FALLO %s %s. HTTP esperado %s, obtenido %s.\n' "$method" "$path" "$expected" "$status" >&2
        cat "$body" >&2
        exit 1
    fi
    printf 'OK %s %s -> %s\n' "$method" "$path" "$status"
}

check 200 GET /q/health/ready '.status == "UP"'
check 400 POST /CurrentAccount/Initiate '.code == "SOLICITUD_INVALIDA"' \
    --data '{"AccountCurrency":"PEN"}'
check 201 POST /CurrentAccount/Initiate \
    '(.CurrentAccountFacilityReference | test("^CA-[0-9]{10}$")) and .AccountBalance == 100' \
    --data '{"CustomerReference":"DEMO-K8S","AccountCurrency":"PEN","InitialDepositAmount":100}'
account=$(jq -er '.CurrentAccountFacilityReference' "$body")
account_path="/CurrentAccount/$account"
check 200 GET "$account_path/Retrieve" \
    '.CustomerReference == "DEMO-K8S" and .AccountCurrency == "PEN" and .AccountStatus == "Active" and .AccountBalance == 100'
check 404 GET /CurrentAccount/desconocida/Retrieve '.code == "CUENTA_NO_EXISTE"'
check 200 PUT "$account_path/Control" '.AccountStatus == "Suspended"' --data '{"ControlActionType":"Suspend"}'

payment_body='{"PaymentAmount":30,"PaymentCurrency":"PEN","PayeeReference":"DEMO-K8S"}'
payment_key="Idempotency-Key: $(cat /proc/sys/kernel/random/uuid)"
check 422 POST "$account_path/Payments/Initiate" '.code == "CUENTA_SUSPENDIDA"' \
    --header "Idempotency-Key: $(cat /proc/sys/kernel/random/uuid)" --data "$payment_body"
check 200 PUT "$account_path/Control" '.AccountStatus == "Active"' --data '{"ControlActionType":"Resume"}'
check 201 POST "$account_path/Payments/Initiate" \
    '(.PaymentsReference | test("^PAY-[a-f0-9]{8}$")) and .PaymentStatus == "Registered"' \
    --header "$payment_key" --data "$payment_body"
payment=$(jq -er '.PaymentsReference' "$body")
check 201 POST "$account_path/Payments/Initiate" ".PaymentsReference == \"$payment\"" \
    --header "$payment_key" --data "$payment_body"
check 200 GET "$account_path/Payments/$payment/Retrieve" \
    ".PaymentsReference == \"$payment\" and .PaymentAmount == 30 and .PayeeReference == \"DEMO-K8S\""
check 404 GET "$account_path/Payments/desconocido/Retrieve" '.code == "PAGO_NO_EXISTE"'
check 200 GET "$account_path/Retrieve" '.AccountBalance == 70'

# Criterios de aceptación

1. Abrir una cuenta responde **201**, con referencia `CA-` y diez dígitos, estado `Active` y el depósito indicado. El valor por defecto es cero.
2. Consultar una cuenta devuelve sus datos. Una cuenta inexistente responde **404** con `CUENTA_NO_EXISTE`.
3. Suspender y reactivar cambian el estado. Repetir el estado responde **422** con `TRANSICION_INVALIDA`. La cuenta inexistente responde **404**.
4. Pagar con cargo a una cuenta inexistente responde **404** con `CUENTA_NO_EXISTE`.
5. Pagar desde una cuenta suspendida responde **422** con `CUENTA_SUSPENDIDA`.
6. Pagar en otra moneda responde **422** con `MONEDA_DISTINTA`.
7. Pagar por encima del saldo responde **422** con `SALDO_INSUFICIENTE`.
8. Un pago válido responde **201**, con estado `Registered`, referencia `PAY-` y ocho caracteres. El saldo se reduce por el monto exacto.
9. Repetir un pago con la misma clave UUID devuelve la misma referencia y un solo débito, incluso si ambas solicitudes llegan a la vez. Si cambia el monto, la moneda o el beneficiario, responde **422** con `CLAVE_REUTILIZADA`.
10. Consultar un pago de la cuenta responde **200**. Un pago inexistente o de otra cuenta responde **404** con `PAGO_NO_EXISTE`.
11. Datos obligatorios ausentes, un UUID inválido o un importe con más de dos decimales significativos devuelven **400** con `SOLICITUD_INVALIDA`.

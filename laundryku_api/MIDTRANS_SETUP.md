# Midtrans QRIS Sandbox setup

LaundryKu reads Midtrans credentials from the backend process environment. The repository does not load `.env` automatically and must never contain a real Server Key.

Required environment variables for Apache/PHP:

```text
LAUNDRYKU_MIDTRANS_ENV=sandbox
LAUNDRYKU_MIDTRANS_SERVER_KEY=<Sandbox Server Key from Midtrans MAP>
```

Restart Apache after setting the variables and confirm that the PHP/Apache process receives them. Only the Sandbox environment is enabled by the current implementation.

Configure the Midtrans Payment Notification URL to a stable public HTTPS URL ending in:

```text
/laundryku_api/api/midtrans_notification.php
```

Midtrans cannot call `localhost`, `127.0.0.1`, or Android emulator address `10.0.2.2`. A development tunnel may be used, but its temporary URL and credentials must stay outside this repository.

For a real sandbox test, create QRIS in the Android app and paste the returned QR image URL into the [official QRIS sandbox simulator](https://simulator.sandbox.midtrans.com/openapi/qris/index). Do not scan a sandbox QR with a real payment application.

References:

- [Midtrans QRIS Core API](https://docs.midtrans.com/reference/qris)
- [HTTP notification and signature verification](https://docs.midtrans.com/docs/https-notification-webhooks)
- [Get transaction status](https://docs.midtrans.com/docs/get-status-api-requests)
- [Sandbox payment testing](https://docs.midtrans.com/docs/testing-payment-on-sandbox)

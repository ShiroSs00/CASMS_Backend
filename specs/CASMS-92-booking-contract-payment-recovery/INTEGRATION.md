# Deployment and frontend integration

Apply V94 through the normal reviewed Flyway deployment. Do not run it manually on a shared database. Duplicate legacy guest idempotency keys or normalized user emails intentionally stop migration for reconciliation.

Set `CASMS_BOOKING_EMAIL_ENABLED=true`, `CASMS_BOOKING_EMAIL_FROM`, `CASMS_BOOKING_EMAIL_PORTAL_URL`,
`SPRING_MAIL_HOST=smtp.gmail.com`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` (Gmail app password),
and JavaMail SMTP properties `mail.smtp.auth=true`, `mail.smtp.starttls.enable=true`, `mail.smtp.starttls.required=true`.
Supply secrets through deployment secret management. Configure connect/read/write timeouts (5 seconds recommended).
The application defaults email dispatch off. Undelivered messages remain queued; restrict database access to outbox bodies because pending account-setup links are bearer credentials.

Frontend: retain quote guest token and Idempotency-Key through timeout; recover using `X-Guest-Token` or the original actor JWT.
Do not loop-create bookings when recovery is pending/not found. Render totalPaidAmount, outstandingBalance, fullyPaid and installment paid/outstanding projections from booking detail. Use `/itineraries/mine` for owned trip days and exact bookingPassengerId for activity/check-in APIs.

Implement `/activate-booking-account` at the configured portal: read `#token`, submit token and new password to POST `/api/auth/booking-account/activate`, then use existing login. Existing accounts continue their normal login; newly provisioned accounts receive their username in the confirmation email. Setup links expire after seven days. Expired links require controlled support reissue; no public reset endpoint is added.

SMTP delivery is at least once. No live provider credentials or actual SMTP send were used in tests. No raw contracts, signed files or contract notes should be submitted or stored.

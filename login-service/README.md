# Login service

`login-service` runs at `http://localhost:8085`. Start all services with the repository-root launcher, then open `/login` or `/register`. Registration guides the user through account type, credentials, and customer/dispatcher details. The address field uses the same Google Places autocomplete as the domain services; configure `GOOGLE_MAPS_BROWSER_API_KEY` for browser suggestions.

Registration saves a BCrypt-hashed request, then asks the appropriate domain service to create an inactive profile through its private endpoint. Login-service creates a linked inactive account and shows a receipt. If a downstream service is unavailable, the request remains pending or marked for repair in `/admin/accounts`; the administrator can retry it. Provisioning uses the registration-request ID as an idempotency key, so retrying after a timeout does not create a second profile.

An active development administrator is seeded once with username `admin` and initial password `admin1234!`. The seed never resets a changed password. Sign in and change this password at `/admin/accounts` before using real data. The admin can enable or disable provisioned accounts there. Enabling activates the domain profile before login access; disabling revokes login access before deactivating the profile. Partial failures leave a visible, retryable desired state. New accounts cannot sign in until enabled.

The launcher supplies distinct provisioning, session-validation, and allocation-reference secrets; see the root `.env.example`. Set each to a different random string of at least 32 characters in the ignored root `.env` file for stable restarts. If unset, the launcher generates ephemeral values and shares each only with its intended services. Direct startup requires the relevant environment variables. Internal endpoints accept credentials only from loopback addresses in local development. For deployment across hosts, use HTTPS and a local trusted proxy/network boundary; do not expose internal endpoints on the public internet.

Browser login creates a 256-bit opaque `SESSION` cookie and stores only its SHA-256 hash. The cookie is host-only, `HttpOnly`, `SameSite=Lax`, and expires after eight hours by default. Set `LOGIN_COOKIE_SECURE=true` when serving over HTTPS and keep every local service on `localhost` so the cookie can reach services on different ports. `LOGIN_SESSION_LIFETIME` accepts a duration such as `PT8H`. Login and registration forms use a separate CSRF cookie. Repeated failed logins receive a local in-memory backoff; a shared limiter is needed for multiple login-service instances.

The Android app uses `POST /api/v1/mobile/login`, `POST /api/v1/mobile/register/{customer,dispatcher}`, and `POST /api/v1/mobile/logout`. Login returns the same opaque session credential once in JSON; Android encrypts it using an AES key held by Android Keystore. Registration returns an approval-pending receipt and always creates an inactive account/profile. The mobile app does not expose admin account management; admins must use the web app at `/admin/accounts`. These JSON endpoints are intended for the native client, not browser JavaScript, and do not enable cross-origin browser access.

## Routes and access

| Route or operation | Anonymous | Admin | Customer | Dispatcher |
| --- | --- | --- | --- | --- |
| `GET /login`, `GET /register`, `GET /register/{customer,dispatcher}` | allowed | allowed | allowed | allowed |
| `POST /login` | allowed with CSRF | allowed | allowed if active | allowed if active |
| `/api/v1/mobile/login` | allowed | allowed | allowed if active | allowed if active |
| `/api/v1/mobile/register/{customer,dispatcher}` | creates inactive request/account | creates inactive request/account | creates inactive request/account | creates inactive request/account |
| `/api/v1/mobile/logout` | revokes the supplied mobile session | allowed | allowed | allowed |
| `GET /logout`, `POST /logout` | login redirect / CSRF-protected sign-out | allowed | allowed | allowed |
| `POST /register/{customer,dispatcher}` | creates an inactive request/account | allowed | allowed | allowed |
| `GET /register/received/{id}` | receipt | receipt | receipt | receipt |
| `GET /admin/accounts`, `POST /admin/password` | denied | allowed | denied | denied |
| `POST /admin/requests/{id}/retry`, `POST /admin/accounts/{id}/active` | denied | allowed with CSRF | denied | denied |

Customer-service, dispatcher-service, allocation-service, and taro-service validate the `SESSION` cookie with login-service for each protected request. Existing management routes are admin-only; customers can use `/portal/allocations` in customer-service, and dispatchers can use `/portal/deliveries` in dispatcher-service. Admins can select a profile explicitly in either portal. Allocation-service enforces ownership for portal requests. API requests without a valid session get 401, wrong roles get 403, and an unavailable login-service yields 503. Browser GET pages redirect to the configured login URL. Server forms and JavaScript mutations send per-service HMAC CSRF tokens. Only the public Google Places browser configuration and separately authenticated internal provisioning/reference reads bypass the session check. Android supports account creation, login, and logout; its earlier arbitrary customer/dispatcher selectors are no longer reachable. Account/allocation administration remains web-only.

## Private provisioning contract

- `POST /internal/v1/customers` or `/internal/v1/dispatchers` creates an inactive profile. `X-Login-Provisioning-Key` carries the service-specific secret and `X-Registration-Request-Id` is a positive, retry-safe registration ID. The request body contains profile details only; any supplied `active` value is ignored by the internal DTO.
- `PUT /internal/v1/{customers,dispatchers}/{id}/active` changes domain activation with the same secret and JSON `{ "active": true|false }`. Only login-service calls this from the admin workflow.
- `DELETE /internal/v1/{customers,dispatchers}/{id}` is an authenticated inactive-profile compensation operation; it refuses to remove an active profile.
- The public `POST /api/v1/customers` and `POST /api/v1/dispatchers` routes and both services' create pages/buttons have been removed. Public profile update requests cannot modify `active`.

`POST /internal/v1/sessions/introspect` uses one of four distinct `X-Service-Validation-Key` credentials and JSON `{ "token": "<opaque SESSION token>" }`. It returns only `accountId`, `username`, `role`, `profileId`, `active`, and `expiresAt`, never hashes or passwords. Missing/invalid credentials get 403; invalid, expired, revoked, or inactive sessions get 401. The endpoint is loopback-only and uncached. The four domain services validate on every request, so deactivation takes effect without a validation cache. Portal services forward only short-lived, one-use identity assertions to allocation-service, which checks ownership again.

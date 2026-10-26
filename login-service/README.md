# Login service

`login-service` runs at `http://localhost:8085`. Start all services with the repository-root launcher, then open `/login` or `/register`. Registration guides the user through account type, credentials, and customer/dispatcher details. The address field uses the same Google Places autocomplete as the domain services; configure `GOOGLE_MAPS_BROWSER_API_KEY` for browser suggestions.

Registration saves a BCrypt-hashed request, then asks the appropriate domain service to create an inactive profile through its private endpoint. Login-service creates a linked inactive account and shows a receipt. If a downstream service is unavailable, the request remains pending or marked for repair in `/admin/accounts`; the administrator can retry it. Provisioning uses the registration-request ID as an idempotency key, so retrying after a timeout does not create a second profile.

An active development administrator is seeded once with username `admin` and initial password `admin1234!`. The seed never resets a changed password. Sign in and change this password at `/admin/accounts` before using real data. The admin can enable or disable provisioned accounts there. Enabling activates the domain profile before login access; disabling revokes login access before deactivating the profile. Partial failures leave a visible, retryable desired state. New accounts cannot sign in until enabled.

The launcher supplies distinct `LOGIN_CUSTOMER_PROVISIONING_SECRET` and `LOGIN_DISPATCHER_PROVISIONING_SECRET` values. Set both to different random strings of at least 32 characters in the ignored root `.env` file for stable restarts. If unset, the launcher generates ephemeral values and shares each only with login-service and its matching domain service. Direct startup requires these environment variables. Internal domain endpoints accept the secret only from loopback addresses in local development. For deployment across hosts, use HTTPS, private networking, and a trusted proxy/network boundary; do not expose the endpoints on the public internet. A manually started service using a different secret cannot provision or activate profiles.

Login creates a 256-bit opaque `SESSION` cookie and stores only its SHA-256 hash. The cookie is host-only, `HttpOnly`, `SameSite=Lax`, and expires after eight hours by default. Set `LOGIN_COOKIE_SECURE=true` when serving over HTTPS and keep every local service on `localhost` so the cookie can reach services on different ports. `LOGIN_SESSION_LIFETIME` accepts a duration such as `PT8H`. Login and registration forms use a separate CSRF cookie. Repeated failed logins receive a local in-memory backoff; a shared limiter is needed for multiple login-service instances.

## Routes and access

| Route or operation | Anonymous | Admin | Customer | Dispatcher |
| --- | --- | --- | --- | --- |
| `GET /login`, `GET /register`, `GET /register/{customer,dispatcher}` | allowed | allowed | allowed | allowed |
| `POST /login` | allowed with CSRF | allowed | allowed if active | allowed if active |
| `POST /register/{customer,dispatcher}` | creates an inactive request/account | allowed | allowed | allowed |
| `GET /register/received/{id}` | receipt | receipt | receipt | receipt |
| `GET /admin/accounts`, `POST /admin/password` | denied | allowed | denied | denied |
| `POST /admin/requests/{id}/retry`, `POST /admin/accounts/{id}/active` | denied | allowed with CSRF | denied | denied |

The account cookie is currently enforced by login-service only. Customer-service, dispatcher-service, allocation-service, and taro-service still need Phase 4 session validation and authorization before their management APIs/pages are secure. Customer/dispatcher allocation portals and ownership checks are later phases. An ID supplied by the browser must never be treated as proof of ownership.

## Private provisioning contract

- `POST /internal/v1/customers` or `/internal/v1/dispatchers` creates an inactive profile. `X-Login-Provisioning-Key` carries the service-specific secret and `X-Registration-Request-Id` is a positive, retry-safe registration ID. The request body contains profile details only; any supplied `active` value is ignored by the internal DTO.
- `PUT /internal/v1/{customers,dispatchers}/{id}/active` changes domain activation with the same secret and JSON `{ "active": true|false }`. Only login-service calls this from the admin workflow.
- `DELETE /internal/v1/{customers,dispatchers}/{id}` is an authenticated inactive-profile compensation operation; it refuses to remove an active profile.
- The public `POST /api/v1/customers` and `POST /api/v1/dispatchers` routes and both services' create pages/buttons have been removed. Public profile update requests cannot modify `active`.

Planned Phase 4 validation uses `POST /internal/v1/sessions/introspect` with separate per-service credentials. It will return minimum principal data (`accountId`, `username`, `role`, `profileId`, `active`, `expiresAt`), never hashes or passwords. Protected services must reject missing, expired, revoked, or inactive sessions and enforce role and ownership themselves. This endpoint is not implemented yet.

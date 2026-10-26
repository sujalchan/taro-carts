# Login service: registration request phase

This service currently provides sign-in page design and working customer/dispatcher account request forms. Run `./mvnw spring-boot:run` from this directory, or use the repository-root `start-services.sh` / `start-services.bat` launcher, then open `http://localhost:8085/login` or `/register`. Registration guides users through account type, account details, and role-specific details. The local wrapper delegates to the Maven wrapper already included with `customer-service`.

Submitting a registration form validates the fields, hashes the password with BCrypt, and saves a pending request in `login.db`. A confirmation page appears only after that save succeeds. The request is not yet an active account: administrator approval, customer/dispatcher provisioning, sessions, and sign-in are later phases. Customer and dispatcher records must be created only through login-service's future private provisioning calls. The sign-in button remains disabled.

Both registration views use the same New Zealand Google Places address autocomplete as the existing customer and dispatcher forms. Set `GOOGLE_MAPS_BROWSER_API_KEY` or use the root launcher with that value in the ignored `.env` file. The browser key must allow the login-service origin `http://localhost:8085` as well as the existing customer and dispatcher origins.

## Route and access contract

| Route or operation | Anonymous | Admin | Customer | Dispatcher |
| --- | --- | --- | --- | --- |
| `GET /login`, `GET /register`, `GET /register/{customer,dispatcher}` | allowed | future redirect to home | future redirect to home | future redirect to home |
| `POST /login` | allowed | redirect to home | redirect to home | redirect to home |
| `POST /register/{customer,dispatcher}` | saves pending request | future redirect to home | future redirect to home | future redirect to home |
| `GET /register/received/{id}` | receipt for saved request | receipt | receipt | receipt |
| Admin account approvals | denied | allowed | denied | denied |
| Customer portal and allocation requests | denied | any customer selected explicitly | own customer ID | denied |
| Dispatcher portal and assigned deliveries | denied | any dispatcher selected explicitly | denied | own dispatcher ID |
| Existing management pages and APIs | denied | allowed | denied | denied |

Every protected service must check a session with login-service and authorize from the returned role and linked profile ID. An ID sent by the browser is a selector, never proof of ownership. Public create routes in customer-service and dispatcher-service, including their add buttons and forms, must be removed in the provisioning phase.

## Planned wire contracts

- Session cookie: host-only `SESSION`, random opaque value, `HttpOnly`, `SameSite=Lax`, `Path=/`, `Secure` under HTTPS. Persist only a SHA-256 token hash in `login.db`.
- Introspection: `POST /internal/v1/sessions/introspect`, restricted to services with validation credentials. Request contains the opaque token. Successful response is `accountId`, `username`, `role`, `profileId`, `active`, and `expiresAt`. Missing, expired, revoked, or inactive sessions fail closed.
- Private provisioning: `POST /internal/v1/customers` and `POST /internal/v1/dispatchers`, each authenticated with a credential only login-service possesses. The profile service forces `active=false` and ignores or rejects any caller-provided active value.
- Registration input: common `username`, `password`, `confirmPassword`; profile `name`, `contactName`, `phone`, `address`; account type comes from the route, not a trusted form field. Requests are stored pending. The later provisioning phase must create both account and profile inactive.
- Internal portal calls: after validating the browser session, customer-service or dispatcher-service sends a short-lived signed identity assertion to allocation-service. Allocation-service checks the assertion and enforces role plus ownership again.
- Authentication errors: browser pages redirect to login with an allowlisted return URL; APIs return `401` for missing or invalid sessions and `403` for insufficient role or ownership. Never follow arbitrary `returnTo` URLs.

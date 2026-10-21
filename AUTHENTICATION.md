# Authentication and roles

The authentication service owns accounts, password hashes, roles, OAuth/OIDC sign-in, and token signing. It uses its own SQLite database on port `8084`. Customer, taro, and allocation services validate signed tokens. They do not read the authentication database.

## Start the services

For local development, open a Command Prompt in each service folder and run `mvnw.cmd spring-boot:run`. Start auth-service first, then customer-service, taro-service, and allocation-service. No environment variables are needed. The local profile supplies the internal service credential used by auth-service and allocation-service.

Open `http://localhost:8084/login` and sign in with **username `admin`** and **password `TaroAdmin2026!`**. The initial administrator is created only when the auth database has no accounts. Existing accounts keep their saved passwords; this project’s existing local administrator password was changed to the documented value. The authentication service saves its signing key and account database locally, and both are ignored by Git.

The local profile contains demo credentials. For a deployment, activate a different profile and supply private values for `auth.admin.password` and `auth.service-client-secret`. The auth and allocation services must use the same client secret. The default issuer is `http://localhost:8084`; configure it consistently in all four services if changing it.

Open `http://localhost:8084/accounts` and sign in as the administrator to create customer and dispatcher accounts. The account list shows each account's ID. Link a customer account ID on the customer create/edit page. Assign a dispatcher account ID on the allocation list page. Each service keeps its own customer or assignment data; auth-service does not store it.

Signing in directly at `http://localhost:8084/login` opens the Allocation Service at `http://localhost:8082/allocations`. After signing in, administrators can open `http://localhost:8084/accounts` to manage accounts. OAuth sign-ins started by another service return to that service.

The **Log out** button on each web service clears the local browser session cookies for auth-service and all three web services, then opens the sign-in page. Logging out directly from auth-service clears the same cookies. Each web service uses its own CSRF cookie so forms remain valid when multiple services are open in the same browser.

## Access rules

| Role | Access |
| --- | --- |
| `ADMIN` | Customer and taro management pages, all allocations, approval, dispatcher assignment, allocation editing |
| `CUSTOMER` | Create allocations for the linked active customer, read that customer's allocations, cancel a request before approval |
| `DISPATCHER` | Read assigned allocations and move them from approved through delivered |
| `SERVICE` | Allocation service reads customer and taro reference APIs using client credentials |

Customer and dispatcher requests use `GET /api/v1/allocations` for a filtered list and `GET /api/v1/allocations/{id}` for an authorized order. Customer creation uses `POST /api/v1/allocations` and the customer's own `customerId`. A dispatcher is assigned by an administrator using `PUT /api/v1/allocations/{id}/dispatcher` with `{"accountId":"<dispatcher UUID>"}`. `PATCH /api/v1/allocations/{id}/status` accepts `{"status":"IN_TRANSIT"}` and checks the role, assignment, and current status. Administrators can also approve requested allocations on the web list page.

The server enforces this progress: `REQUESTED_ALLOCATION` → `APPROVED_ALLOCATION` → `PREPARING_ALLOCATION` → `AWAITING_DISPATCH` → `IN_TRANSIT` → `DELIVERED`. An administrator may cancel an active allocation; its customer may cancel while it is still requested. The administrator edit API remains available for correcting allocation data and status.

## Clients

Web pages redirect to the authentication service for sign-in. The web OAuth client ID is `taro-web`. The future Android app should use client ID `taro-android` with authorization code and PKCE; its registered redirect URI is `nz.ac.aut.comp713.tarocartmobile:/oauth2redirect`. No Android sign-in screens have been added yet.

Access tokens last 15 minutes. Refresh tokens are configured for seven days, but OAuth authorizations currently live in memory, so a restart requires a new sign-in. Account records and the signing key persist. For a deployment, use HTTPS and persist authorization grants before relying on refresh tokens across restarts.

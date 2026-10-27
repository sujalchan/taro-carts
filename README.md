# Taro Carts

Taro Carts is a distributed web and mobile system for managing customers, taro products, weekly allocations, and allocation fulfilment.

The project extends an existing COMP713 Assessment 2 distributed web/API application into a multi-client system containing a web interface, native Android application, independently running backend services, persistent storage, live updates, and service-to-service communication.

## Project Status

Taro Carts is currently under development for COMP713 Assessment 3.

The initial project baseline contains:

- Customer management
- Taro type management
- Weekly allocation management
- REST APIs
- Server-rendered web interfaces
- JavaScript client-rendered interfaces
- SQLite persistence
- Input validation and structured error handling
- Communication between `allocation-service`, `customer-service`, and `taro-service`

Assessment 3 development extends this baseline with:

- Native Android application
- Multi-user shared workflows
- Allocation status tracking
- Mobile-specific functionality
- WebSocket live updates
- WebSocket reconnection and state restoration
- Advanced service integration
- Additional integration and reliability testing

---

## System Overview

Taro Carts is designed around two main user roles:

### Manager

The manager primarily uses the web application to:

- Manage customers
- Manage taro types
- Create weekly allocations
- Update allocations
- Monitor allocation progress
- View status changes made by workers

### Worker

The worker uses the Android application to:

- View available allocations
- View allocation details
- Update allocation progress
- Complete a meaningful part of the shared allocation workflow
- Use mobile-specific functionality such as device location

Actions performed by one user can affect what the other user sees or can do.

---

## Architecture

The project contains five independently running Spring Boot backend services and a native Android client. `login-service` handles registration, provisions inactive customer/dispatcher profiles, and lets the seeded admin enable or disable linked accounts. The other services validate its opaque session on each protected request; existing management routes require an admin session. Customers use `http://localhost:8081/portal/allocations` to view and request their own allocations; dispatchers use `http://localhost:8084/portal/deliveries` to view and advance assigned deliveries. Allocation ownership is enforced in allocation-service.

```text
Web manager (admin session) ──────→ allocation-service :8082 ───→ allocation.db
                                       │          │
                                     HTTP       HTTP
                                       ↓          ↓
                              customer-service  taro-service
                                   :8081            :8083
                                     │                │
                                     ↓                ↓
                                customer.db        taro.db

Browser ───→ dispatcher-service :8084 ───→ dispatcher.db
Browser ───→ login-service :8085 (registration, login, admin approval)
                       │
                       └── private provisioning/activation → customer-service :8081
                                                          or dispatcher-service :8084
```

Each backend service owns its own persistent data.

`allocation-service` stores customer and taro type IDs only. It retrieves minimal customer and dispatcher reference data through service-key-protected internal endpoints, and taro type data from `taro-service` over HTTP; it does not access their databases directly.

---

## Repository Structure

```text
taro-carts/
│
├── customer-service/
│   ├── pom.xml
│   ├── mvnw
│   └── src/
│
├── dispatcher-service/
│   ├── pom.xml
│   ├── mvnw
│   └── src/
│
├── taro-service/
│   ├── pom.xml
│   ├── mvnw
│   └── src/
│
├── allocation-service/
│   ├── pom.xml
│   ├── mvnw
│   └── src/
│
├── android-app/
│   ├── app/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradlew
│
├── docs/
│
└── README.md
```

The Spring Boot services use **Maven**, while the Android application uses **Gradle**.

The build systems are independent and do not need to match because communication between components occurs through network interfaces rather than through their build systems.

---

## Technology Stack

### Backend

- Java 21
- Spring Boot 4.1.1
- Spring Web
- Spring Data JPA
- Jakarta Validation
- Hibernate
- SQLite
- Spring `RestClient`
- Thymeleaf
- OpenAPI / Swagger
- Maven

### Web Client

- Thymeleaf
- HTML
- CSS
- Vanilla JavaScript
- Fetch API

### Android Client

- Android Studio
- Java
- Gradle
- Android SDK
- REST API communication

### Testing

- JUnit 5
- Mockito
- MockMvc
- Manual REST testing using Swagger and `curl`

---

## Backend Services

### Customer Service

`customer-service` runs on:

```text
http://localhost:8081
```

It owns customer records and supports retrieval, updates, and search. Only login-service can create or activate customers through private endpoints.
New customers require an address. Existing customers without one can be viewed and must enter an address when edited.
Its SQLite database is `customer.db`.

### Dispatcher Service

`dispatcher-service` runs on:

```text
http://localhost:8084
```

It lists, searches, and edits dispatcher profiles. Only login-service can create or activate dispatchers, and newly provisioned profiles are inactive. Its SQLite database is `dispatcher.db`. Server-rendered pages are `/dispatchers` and `/dispatchers/{id}/edit`; client-rendered pages are `/dispatchers-client.html` and `/dispatcher-edit-client.html`. Edit forms allow an unchanged saved address and require a Places selection for changes.

### Google Maps address suggestions and validation

Enable **Maps JavaScript API** and **Places API (New)** in a billing-enabled Google Cloud project. The browser forms require a complete street address selected from Google Places suggestions before submission and store Google's formatted address. The API endpoints still require a nonblank address, but do not call Google's address validation service.

Set the browser key in the ignored repository-root `.env` file:

```properties
GOOGLE_MAPS_BROWSER_API_KEY=your-google-api-key
```

Restrict the browser key to the customer-service, dispatcher-service, and login-service web origins (`http://localhost:8081`, `http://localhost:8084`, and `http://localhost:8085`) and the Maps JavaScript API and Places API (New). The root `start-services.bat`, `start-services.ps1`, and `start-services.sh` launchers read this key and pass it to those three services; environment variables take precedence. When starting one of them directly, set `GOOGLE_MAPS_BROWSER_API_KEY` for address suggestions and selection checks. The login-service registration forms use the same Google Places address fill before provisioning an inactive account.

For Android address search and the login-service endpoint, add these values to the ignored `android-app/local.properties` file, then sync, rebuild, and reinstall the app:

```properties
GOOGLE_MAPS_ANDROID_API_KEY=your-google-api-key
LOGIN_SERVICE_BASE_URL=http://10.0.2.2:8085
```

If `GOOGLE_MAPS_ANDROID_API_KEY` is omitted, the Android build uses `GOOGLE_MAPS_BROWSER_API_KEY` from the repository-root `.env` file. A key restricted to browser origins will not authorize Android requests; in that case, create an Android-restricted key for the app and put it in `android-app/local.properties`.

Android registration uses the Places picker when the Android key is configured and requires a selected New Zealand street address with a street number before the account request can be submitted. Without a configured key, the address can be entered manually. The Android app supports customer/dispatcher registration requests and sign-in, but not admin account management.

On the registration address step, **Use current location** opens Android's native precise/approximate foreground location permission prompt when permission has not already been granted. The app sends the coordinates to login-service, which uses the Google Geocoding API to suggest a nearby New Zealand street address. The user must confirm that address before registration uses it; GPS and reverse geocoding cannot reliably identify a particular entrance or building. An older login-service without this endpoint falls back to Android's built-in geocoder, which needs no app API key. Enable the Geocoding API for the project. For production, set `GOOGLE_MAPS_SERVER_API_KEY` in the root `.env` to a server-restricted key; the launchers pass it only to login-service. If it is unset, local development falls back to `GOOGLE_MAPS_BROWSER_API_KEY`, which is already exposed to browser clients and therefore is not a private server key. Serve the mobile API over HTTPS outside local emulator development.

The Android customer **Edit information** screen offers the same precise-location suggestion. It keeps the current address until the user confirms the suggested address, and also allows address search or manual edits.

If the Android build runs out of Java heap while merging dex files, set `org.gradle.jvmargs=-Xmx3g` in the ignored `android-app/gradle.properties` file and rebuild.

### Taro Service

`taro-service` runs on:

```text
http://localhost:8083
```
It owns taro types and supports creation, retrieval, updates, and search. 
Its SQLite database is `taro.db`.

### Allocation Service

`allocation-service` runs on:

```text
http://localhost:8082
```

It owns:

- Weekly allocations
- Allocation items

Main responsibilities include:

- Creating weekly allocations
- Retrieving allocations
- Updating allocations
- Deleting allocations
- Managing multiple taro items within an allocation
- Supporting custom weekly prices
- Falling back to standard taro prices
- Preventing duplicate weekly allocations
- Preventing duplicate taro types within an allocation
- Validating allocation quantities
- Communicating with customer-service and taro-service

Persistent database:

```text
allocation.db
```

## Existing Web Interfaces

The project currently contains both server-rendered and client-rendered interfaces.

### Server-Rendered Pages

Spring MVC and Thymeleaf are used to produce HTML on the server.

Communication flow:

```text
Browser
   ↓
Spring MVC Controller
   ↓
Service
   ↓
Repository
   ↓
SQLite
   ↓
Thymeleaf
   ↓
Rendered HTML
   ↓
Browser
```

### Client-Rendered Pages

The JavaScript interfaces retrieve JSON from the REST APIs using `fetch()`.

Communication flow:

```text
Browser
   ↓
HTML + JavaScript
   ↓
REST API
   ↓
Service
   ↓
Repository
   ↓
SQLite
   ↓
JSON
   ↓
JavaScript updates DOM
```

---

## REST API

### Customer Service

Base URL:

```text
http://localhost:8081/api/v1
```

Customer operations include:

```text
GET    /customers
POST   /customers
GET    /customers/{id}
PUT    /customers/{id}
```

Customer searching:

```text
GET /customers?search=<value>
```

### Dispatcher Service

Base URL: `http://localhost:8084/api/v1`

```text
GET  /dispatchers
POST /dispatchers
GET  /dispatchers/{id}
PUT  /dispatchers/{id}
GET  /google-places/config
```

Dispatcher searching uses `GET /dispatchers?search=<value>` and checks name, contact name, phone, and address case-insensitively. `GET /google-places/config` returns the browser Places API key used by the forms. Creation requires `name` and `address`; `contactName` and `phone` are optional. The `active` flag defaults to `true` when omitted on create; omitting it on update preserves the existing value. On update, omitted contact or phone values are cleared. Duplicate names are rejected case-insensitively after whitespace is removed and return `409`. Browser forms require an address selected from Google Places suggestions; the API requires a nonblank address. See the [Dispatcher Service Swagger UI](http://localhost:8084/swagger-ui/index.html) for request and response schemas.

### Taro Service

Base URL: `http://localhost:8083/api/v1`

Taro type operations include:

```text
GET    /taro-types
POST   /taro-types
GET    /taro-types/{id}
PUT    /taro-types/{id}
```

Taro type searching:

```text
GET /taro-types?search=<value>
```

### Allocation Service

Base URL:

```text
http://localhost:8082/api/v1
```

Allocation operations include:

```text
GET     /allocations?customerId={customerId}
GET     /allocations/dispatcher/{dispatcherId}
GET     /allocations/dispatcher/me
POST    /allocations
POST    /allocations/customer-request
GET     /allocations/{id}
PUT     /allocations/{id}
POST    /allocations/{id}/pay
DELETE  /allocations/{id}/customer-request
PUT     /allocations/{id}/delivery-status
DELETE  /allocations/{id}
GET     /allocations/{id}/invoice.pdf?inline={true|false}
GET     /customer-service-reference/customers?includeInactive={true|false}
GET     /taro-service-reference/taro-types
GET     /dispatcher-service-reference/dispatchers
```

When updating an allocation to `AWAITING_DISPATCH`, `IN_TRANSIT`, or `DELIVERED`, include a valid `dispatcherId` in the JSON body. Allocation-service verifies the ID with dispatcher-service and returns `DISPATCHER_REQUIRED` (400) when it is missing or `DISPATCHER_NOT_FOUND` (404) when it does not identify an existing dispatcher. Allocation responses include `dispatcherId` when assigned. The edit pages show dispatcher names in the dropdown and submit each dispatcher's ID.

Customer/dispatcher sessions can only list and open their own or assigned allocations; admins can use explicit customer or dispatcher filters. The customer request body may omit `customerId`: allocation-service derives it from the authenticated profile and rejects a forged ID. Payment and cancellation similarly derive the acting customer, so the old `customerId` query parameter is optional and cannot grant access. Only admins can use general create, update, and delete endpoints. An assigned dispatcher can advance `AWAITING_DISPATCH` to `IN_TRANSIT` and then `DELIVERED` through the narrow delivery-status endpoint (`{"deliveryStatus":"IN_TRANSIT"}`); general updates remain admin-only. Invoice downloads enforce the same ownership check before accessing an existing PDF.

For delivery navigation, the service randomly chooses and saves one starting address per allocation from the configurable `DELIVERY_START_ADDRESSES` list, separate from dispatcher account addresses. Dispatchers can open **Navigate** for assigned deliveries awaiting dispatch or in transit, from either the web portal or the Android app. The same map shows the start, the delivery destination, and a truck marker in the middle. If no driver position was previously shared, the truck and covered route are labelled as estimates. Administrators can open **View location** for in-transit deliveries. Delivered addresses remain visible to administrators in the web portal but are omitted from dispatcher delivery lists on both clients. The browser map uses `GOOGLE_MAPS_BROWSER_API_KEY` and requires the Maps JavaScript, Geocoding, and Routes APIs for the full road route.

---

## Swagger

Customer Service:

```text
http://localhost:8081/swagger-ui/index.html
```

Dispatcher Service:

```text
http://localhost:8084/swagger-ui/index.html
```

Allocation Service:

```text
http://localhost:8082/swagger-ui/index.html
```

Taro Service:

```text
http://localhost:8083/swagger-ui/index.html
```

Allocation Service:

```text
http://localhost:8082/swagger-ui/index.html
```

Raw OpenAPI definitions are available at:

```text
http://localhost:8081/v3/api-docs
http://localhost:8084/v3/api-docs
http://localhost:8083/v3/api-docs
http://localhost:8082/v3/api-docs
```

---

## Data Model

The current system contains five main persistent entities.

### Customer

Stores information including:

- ID
- Name
- Contact name
- Phone number
- Active status

### Dispatcher

Stores:

- ID
- Dispatcher name
- Contact name
- Phone number
- Address

### TaroType

Stores:

- ID
- Name
- Standard price

### WeeklyAllocation

Stores:

- ID
- Customer ID
- Week start date

A customer can only have one allocation for a particular week.

### AllocationItem

Stores:

- ID
- Weekly allocation reference
- Taro type ID
- Quantity
- Price per kilogram

A taro type cannot appear more than once in the same weekly allocation.

---

## Allocation Rules

### Week Normalisation

Dates supplied for weekly allocations are normalised to the Monday of their week.

For example:

```text
Tuesday 29 December 2026
              ↓
Monday 28 December 2026
```

This prevents multiple allocations being created for the same customer using different dates within the same week.

### Custom Pricing

An allocation item may provide a custom price.

If a custom price is supplied:

```text
custom price → stored with allocation item
```

If no custom price is supplied:

```text
allocation-service
       ↓
taro-service
       ↓
retrieve taro standard price
       ↓
use standard price
```

### Quantities

Allocation quantities must be positive whole numbers.

Values such as:

```text
0
-5
1.5
```

are rejected.

---

## Validation and Error Handling

The APIs return structured errors for invalid or failed requests.

Examples include:

```text
400 Bad Request
```

for invalid request data.

```text
404 Not Found
```

for resources that do not exist.

```text
409 Conflict
```

for duplicate data or conflicting operations.

```text
503 Service Unavailable
```

when a required backend service cannot be contacted.

Validation includes:

- Required fields
- Phone number format
- Price validation
- Positive whole-number quantities
- Existing customer references
- Existing taro type references
- Duplicate customer names
- Duplicate taro type names
- Duplicate weekly allocations
- Duplicate taro types within an allocation

---

## Assignment 3 Development

The following features are being added during Assessment 3.

### Shared Workflow

The planned workflow involves two roles.

```text
Manager
   ↓
creates / prepares allocation
   ↓
allocation-service
   ↓
Worker sees allocation in Android app
```

The reverse workflow is:

```text
Worker
   ↓
updates allocation status
   ↓
allocation-service
   ↓
persistent status change
   ↓
WebSocket event
   ↓
Manager's web interface updates
```

### Allocation Status

Weekly allocations will support workflow states such as:

```text
READY
IN_PROGRESS
DELIVERED
```

These states will allow the Android and web clients to participate in the same workflow.

### Android Application

The native Android application will initially contain approximately two to three screens.

Planned screens include:

```text
Allocation List
      ↓
Allocation Details
      ↓
Status / Completion Workflow
```

The application will make real HTTP requests to `allocation-service`.

### Mobile Capability

A mobile-specific feature will be added to support the worker workflow.

The planned feature is device location when completing an allocation, with a manual fallback if location access is unavailable or permission is denied.

### WebSocket Updates

The manager web interface will receive live allocation updates through WebSocket.

Example:

```text
Android worker
     ↓
marks allocation DELIVERED
     ↓
REST request
     ↓
allocation-service
     ↓
database update
     ↓
WebSocket event
     ↓
manager web page changes automatically
```

No manual refresh or HTTP polling should be required for the live update.

Following a WebSocket reconnection, the web client will retrieve the current server state before continuing to process live events.

### Advanced Integration

The planned advanced integration path is **Services and gRPC**.

The existing:

```text
allocation-service
      ↓ REST
customer-service + taro-service
```

communication will be extended or replaced with:

```text
allocation-service
      ↓ gRPC
customer-service + taro-service
```

External clients will continue using REST.

A `.proto` contract will define the communication between the allocation, customer, and taro services.

---

## Prerequisites

Before running the project, install:

- Java 21
- Git
- Android Studio
- Android SDK
- A suitable Android emulator or physical Android device

The Maven and Gradle wrappers included with the projects should be used where possible.

---

## Running the Backend

The launchers start all five backend services, including `login-service` at `http://localhost:8085/login`. The seeded development admin can sign in with `admin` / `admin1234!` and should immediately change that password at `/admin/accounts`. Registration provisions an inactive customer or dispatcher profile and linked account; the admin enables it from `/admin/accounts`. If provisioning is temporarily unavailable, the request remains visible there for retry.

The launcher supplies separate provisioning, session-validation, allocation-reference, and portal assertion secrets. See `.env.example` for their names. Set each to a distinct random value of at least 32 characters in the ignored root `.env` file for stable restarts; unset values are generated for a full launcher run. Direct service startup requires the relevant secrets in its environment. Private endpoints accept credentials only from loopback in local development; use TLS and a local trusted proxy/network boundary for deployment beyond one host. Restart the full stack if you rely on ephemeral launcher secrets.

Browser management pages and APIs now require an admin `SESSION` cookie from login-service. Existing POST/PUT/DELETE forms and JavaScript requests also require a service CSRF token. Missing or expired sessions return 401 for APIs (browser pages redirect to login), wrong roles return 403, and login-service outages return 503. Allocation-service's internal reads from customer-, dispatcher-, and taro-service use separate reference keys rather than the browser cookie.

All five services show a branded browser error page for access denied, missing pages, unavailable services, and unexpected failures. API and internal endpoints keep their JSON error responses; error pages do not expose stack traces or internal exception messages.

Customer/dispatcher portal controllers validate the browser session locally, then sign each allocation-service request with `X-Allocation-Identity-Assertion`. The HMAC assertion includes origin, account/role/profile, issued/expiry time, HTTP method/path, and a one-use nonce. It expires within 45 seconds and is never a browser credential. The root launchers share separate customer and dispatcher assertion keys only with the matching portal service and allocation-service. Admins can open either portal and explicitly select a customer or dispatcher to view.

Administrators assign deliveries from `/allocations` by choosing an active dispatcher on an allocation card. The admin allocation creation forms accept optional custom item prices; those saved prices are used when delivery issues the invoice. Once issued, an admin can use **Amend Invoice Prices** to update prices with a required reason. The prior PDF and total are retained as a downloadable revision. Customer pages provide both inline invoice viewing and downloads. All service navigation uses a common role-aware menu, and **Sign out** opens a confirmation page at login-service before the session is revoked.

To launch all five services together:

- macOS/Linux: run `./start-services.sh` from the repository root. Logs are written to `logs/`; press Ctrl+C to stop the services.
- Windows: run `start-services.bat` from the repository root. Each service opens in its own Command Prompt window; close the windows to stop them.

These scripts use the included Maven wrappers and require Java 21.

### 1. Start Customer Service

From the repository root:

```bash
cd customer-service
./mvnw spring-boot:run
```

Customer-service will start on:

```text
http://localhost:8081
```

### 2. Start Dispatcher Service

From the repository root:

```bash
cd dispatcher-service
./mvnw spring-boot:run
```

Dispatcher-service starts on `http://localhost:8084`.

### 3. Start Taro Service

In another terminal:

```bash
cd taro-service
./mvnw spring-boot:run
```

Taro-service starts at `http://localhost:8083`.

Set `TARO_SERVICE_URL` to override the allocation service's taro-service URL (default `http://localhost:8083`). Set `CUSTOMER_SERVICE_URL` to override its customer-service URL (default `http://localhost:8081`).

For an existing installation, stop the services and back up `customer-service/customer.db` before moving the `taro_type` rows into `taro-service/taro.db`. Preserve each row's `id` so allocation records continue to resolve. Start `taro-service` once to create its schema, stop it, then from the repository root run:

```bash
sqlite3 taro-service/taro.db "ATTACH DATABASE 'customer-service/customer.db' AS legacy; INSERT INTO taro_type (id, name, normalized_name, description, standard_price) SELECT id, name, normalized_name, description, standard_price FROM legacy.taro_type; DETACH DATABASE legacy;"
```

Verify the migrated rows before removing the legacy `taro_type` table from `customer-service/customer.db`. New installations have no data to migrate.

### 4. Start Allocation Service

In another terminal:

```bash
cd allocation-service
./mvnw spring-boot:run
```

Allocation-service will start on:

```text
http://localhost:8082
```

To perform a clean build before starting:

```bash
./mvnw clean spring-boot:run
```

---

## Running the Android Application

Open:

```text
android-app/
```

as a project in Android Studio.

Allow Gradle to synchronise the project and then run the application using an Android emulator or supported physical device.
After changing the app, use Android Studio's Run action or `:app:installDebug` to replace the APK on the emulator; compiling Kotlin alone does not update the installed app.

### Android Emulator Backend Address

When the backend is running on the same computer as the Android emulator, the Android application cannot use:

```text
http://localhost:8082
```

because `localhost` refers to the emulator itself.

The standard Android emulator host address is:

```text
http://10.0.2.2:8082
```

The Android client should therefore use an API base URL similar to:

```text
http://10.0.2.2:8082/
```

The Android app signs in against login-service. New customer and dispatcher
accounts remain pending until enabled by an administrator. The previous
selection-based “act as any customer/dispatcher” screens are removed so mobile
users cannot choose another profile's identity. After sign-in, use the matching
web portal for allocation and delivery workflows; admin account management is
web-only. On Android 17 and newer, allow the app's local network request so it
can reach the emulator host.

A physical Android device will require the backend computer's reachable local network address instead.

The dispatcher view also needs dispatcher-service on port 8084.

---

## Running Tests

Run the customer-service tests:

```bash
cd customer-service
./mvnw test
```

Run the taro-service tests:

```bash
cd taro-service
./mvnw test
```

Run the allocation-service tests:

```bash
cd allocation-service
./mvnw test
```

Android tests can be run through Android Studio or through the Gradle wrapper once the Android project is implemented.

---

## Resetting Local Databases

Stop the relevant service before deleting its database.

Customer database:

```text
customer.db
```

Allocation database:

```text
allocation.db
```

Deleting a database file will cause the service to create a new local database when restarted.

Do not delete production or important persistent data using this method.

---

## Assessment 2 Baseline

Taro Carts extends the Taro Allocation System originally developed as an individual COMP713 Assessment 2 project.

The Assessment 2 baseline includes:

- Spring Boot customer-service and taro-service
- Spring Boot allocation-service
- Separate SQLite databases
- REST APIs
- Customer CRUD functionality
- Taro type CRUD functionality
- Weekly allocation CRUD functionality
- Search functionality
- Input validation
- Structured API errors
- Custom and standard pricing
- Week normalisation
- Server-rendered interfaces
- Client-rendered JavaScript interfaces
- Automated backend tests

Assessment 3 commits represent new or revised work performed after this baseline.

---

## Development Progress

Assessment 3 development is intended to occur through regular, meaningful commits.

Major development milestones include:

```text
Assessment 2 baseline imported
        ↓
Shared workflow and status model
        ↓
Android project
        ↓
Android-to-API integration
        ↓
Mobile capability
        ↓
WebSocket prototype
        ↓
WebSocket reconnection
        ↓
gRPC integration
        ↓
Failure handling
        ↓
Integration and non-functional testing
        ↓
Final documentation and demonstration
```

Dated checkpoints and supporting commit hashes will be recorded as development progresses.

---

## Known Limitations

The project is intended as an educational distributed-systems project rather than a production deployment.

Current or expected limitations include:

- Local development deployment
- SQLite databases
- No full production authentication or authorization system
- Simple user-interface design
- Limited scalability compared with a production database architecture
- Local service configuration
- Android and Assignment 3 integration features remain under active development

Further limitations will be documented as the project develops.

---

## License

Copyright © 2026 Static Talent Group Limited. All Rights Reserved.

This project and its source code are proprietary to Static Talent Group Limited.

The source code is made available for viewing, study, and educational reference purposes only.

Copying, redistribution, modification, sublicensing, commercial use, or use of substantial portions of this project in another academic submission is prohibited without prior written permission from Static Talent Group Limited.

See the `LICENSE` file for full terms.

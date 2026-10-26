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

The project contains four independently running Spring Boot backend services and a native Android client.

```text
Web manager / Android worker ───→ allocation-service :8082 ───→ allocation.db
                                       │          │
                                     HTTP       HTTP
                                       ↓          ↓
                              customer-service  taro-service
                                   :8081            :8083
                                     │                │
                                     ↓                ↓
                                customer.db        taro.db

Browser ───→ login-service :8084 (login/register forms; receipt-only prototype)
```

Each backend service owns its own persistent data.

`allocation-service` stores customer and taro type IDs only. It retrieves customer data from `customer-service` and taro type data from `taro-service` over HTTP; it does not access either database directly.

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
├── login-service/
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

It owns customer records and supports customer creation, retrieval, updates, search, and activation.
New customers require an address. Existing customers without one can be viewed and must enter an address when edited.
Its SQLite database is `customer.db`.

### Google Places address suggestions

Enable **Maps JavaScript API** and **Places API (New)** in a billing-enabled Google Cloud project. One unrestricted key can be used for both the website and Android app during development: put the same key in both settings below. Separate keys are recommended later so the website key can be restricted to the customer-service website and the Android key to app package `nz.ac.aut.comp713.tarocartmobile` and its signing certificate. One key cannot have both website and Android application restrictions.

For the web customer create and edit forms, put the key in the ignored repository-root `.env` file:

```properties
GOOGLE_MAPS_BROWSER_API_KEY=your-google-api-key
```

The root `start-services.bat`, `start-services.ps1`, and `start-services.sh` launchers read this file and pass the key only to `customer-service`. An existing `GOOGLE_MAPS_BROWSER_API_KEY` environment variable takes precedence. If starting `customer-service` directly, set that environment variable first.

For Android customer editing, add this line to the ignored `android-app/local.properties` file, then sync and rebuild the app:

```properties
GOOGLE_MAPS_ANDROID_API_KEY=your-google-api-key
```

The address remains editable on both platforms. If a key is not configured or Google cannot find the address, users can enter it manually. Android currently edits customers but does not create them.

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

### Login Service

`login-service` runs on:

```text
http://localhost:8084
```

It provides server-rendered login and registration forms and matching versioned API endpoints. For now, submissions are validated and acknowledged as received; no authentication, account creation, or credential storage is performed.

---

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
GET     /allocations
POST    /allocations
GET     /allocations/{id}
PUT     /allocations/{id}
DELETE  /allocations/{id}
```

### Login Service

Base URL: `http://localhost:8084/api/v1`

```text
POST /auth/login
POST /auth/register
```

Both endpoints return `202 Accepted` with a receipt message. Invalid input returns the shared `{code, message, path}` error shape. The OpenAPI contract is `login-service/contracts/login-api.yaml`.

Additional Assignment 3 operations will be added as shared workflows are implemented.

---

## Swagger

Customer Service:

```text
http://localhost:8081/swagger-ui/index.html
```

Taro Service:

```text
http://localhost:8083/swagger-ui/index.html
```

Allocation Service:

```text
http://localhost:8082/swagger-ui/index.html
```

Login Service:

```text
http://localhost:8084/swagger-ui/index.html
```

Raw OpenAPI definitions are available at:

```text
http://localhost:8081/v3/api-docs
http://localhost:8083/v3/api-docs
http://localhost:8082/v3/api-docs
http://localhost:8084/v3/api-docs
```

---

## Data Model

The current system contains four main persistent entities.

### Customer

Stores information including:

- ID
- Name
- Contact name
- Phone number
- Active status

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

A `.proto` contract will define the communication between the three backend services.

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

All three backend services should be running for the complete system to operate.

To launch all three services together:

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

### 2. Start Taro Service

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

### 3. Start Allocation Service

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

After customer selection, the Android app opens the weekly allocation list.
Each allocation card shows its customer,
week start, order and payment statuses, and item quantities and prices. Start the
allocation, customer, and taro services before opening the list in the app. The API
base URL is defined by `API_BASE_URL` in `android-app/app/src/main/java/nz/ac/aut/comp713/tarocartmobile/data/AllocationApi.kt`.

On launch, the Android app asks which active customer to act as. It then lists only
that customer's allocations. The customer can request a week with one or more taro
types and whole-number quantities; the service applies its default
`REQUESTED_ALLOCATION` and `UNPAID` statuses and standard prices. The app offers
deletion only for allocations requested through the customer app while they are
still requested. The customer-request API checks requester, ownership, and status
again. Customer selection is not authentication; this is a development workflow
until login is added.

For a customer's unpaid or overdue allocation, the app shows **Pay** after the
requested stage and before cancellation. Confirming records `PAID` through the
allocation API and refreshes an existing invoice PDF. It does not collect money.

When an allocation has an issued invoice, its card offers **View invoice PDF**.
The invoice opens inside the app with page and zoom controls. On Android 17 and
newer, allow the app's local network request so it can reach the emulator host.

A physical Android device will require the backend computer's reachable local network address instead.

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

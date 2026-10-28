# Taro Carts

Taro Carts is for a **AUT COMP713 assignment**: a distributed web and Android app for customer profiles, taro products, weekly allocations, payments, invoices, and deliveries. The five Spring Boot services run separately and keep their own SQLite databases, the Android client is written in Kotlin with Jetpack Compose.

## Start here

Use a **full JDK 21** (not a JRE), preferably for both the Maven services and Android Studio's Gradle JDK. The backend projects target Java 21. Install Android Studio and an Android SDK/emulator only if you want to run the mobile app.

1. From the repository root, copy `.env.example` to the ignored `.env`. Add Google Maps keys if you need address search, geocoding, or maps. The launchers generate missing internal service secrets for a full local run; set distinct 32+ character values in `.env` if you restart services individually.
2. Start **all five services** from the root. The launchers use each service's Maven wrapper and pass the required shared secrets:

   | Platform | Command |
   | --- | --- |
   | macOS/Linux | `./start-services.sh` |
   | Windows Command Prompt | `start-services.bat` |
   | Windows PowerShell | `.\start-services.ps1` |

3. Open the [login page](http://localhost:8085/login) in your browser. Choose **Create account** there to register a customer or dispatcher.

On macOS/Linux, logs go to `logs/` and Ctrl+C stops the stack. On Windows, the launcher opens a Command Prompt window for each service; close those windows to stop them. If startup reports a port in use, stop the previous stack before relaunching.

## Services and project layout

| Directory | Port | Role |
| --- | ---: | --- |
| `login-service/` | 8085 | Registration, sign-in, sessions, admin approval, and account activation |
| `customer-service/` | 8081 | Customer profiles and the customer web portal |
| `dispatcher-service/` | 8084 | Dispatcher profiles, assigned deliveries, and delivery navigation |
| `taro-service/` | 8083 | Taro types and standard prices |
| `allocation-service/` | 8082 | Weekly allocations, items, status, payments, and PDF invoices |
| `android-app/` | — | Customer and dispatcher mobile client |

Each service's `pom.xml`, `src/main/resources/application.properties`, and `src/main/` code are the main backend files. Running a service from its directory creates its own ignored `*.db` file there. The Android entry point is `android-app/app/src/main/java/nz/ac/aut/comp713/tarocartmobile/MainActivity.kt`; its UI is under `ui/`, and API/session code is under `data/`. See [login-service/README.md](login-service/README.md) for the authentication endpoints and [docs/](docs/) for additional notes.

## Accounts and authentication

The development admin is seeded **once** by `login-service`: username **`admin`**, initial password **`admin1234!`**. Sign in at the [login page](http://localhost:8085/login), go to [admin accounts](http://localhost:8085/admin/accounts), and change the password. The seed does not reset a changed password.

Customer and dispatcher registration creates an **inactive** account and profile. The admin must enable it at `/admin/accounts` before that user can sign in; failed provisioning can also be retried there. Admin approval is available in the web app, not Android.

Browser sign-in uses an opaque, HttpOnly `SESSION` cookie. Each protected service validates it with `login-service`; management pages require the admin role, and write requests use CSRF protection. Android receives an opaque session token, stores it encrypted with Android Keystore, and checks it while the app is open. The root launchers provide separate secrets for service validation, provisioning, and internal allocation requests.

## Android setup and common build issue

Open `android-app/` as the project in Android Studio, sync Gradle, then run on an emulator. For Android address search, put an Android-restricted Places key and the emulator login URL in the ignored `android-app/local.properties` (keep any existing `sdk.dir` line):

```properties
GOOGLE_MAPS_ANDROID_API_KEY=your-android-api-key
LOGIN_SERVICE_BASE_URL=http://10.0.2.2:8085
```

The emulator reaches the host computer at `10.0.2.2`. The allocation/customer/dispatcher URLs are currently set to that address in `android-app/app/src/main/java/nz/ac/aut/comp713/tarocartmobile/data/AllocationApi.kt`, and the dispatcher map URL is in `ui/DispatcherNavigationScreen.kt`. A physical device needs those URLs and the login URL changed to the computer's reachable LAN address. Android account activation remains a web admin task.

If Android compilation runs out of heap (often during dex merging), edit the ignored `android-app/gradle.properties` and **replace** its `org.gradle.jvmargs` line with:

```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
```

Give Gradle at least 3 GB of available RAM, restart the Gradle daemon or Android Studio, and rebuild. Set Android Studio's Gradle JDK to JDK 21; if `org.gradle.java.home` is present in `gradle.properties`, it must point to **your** JDK 21 installation with `jlink`, not another developer's machine path.

For browser address search and maps, set `GOOGLE_MAPS_BROWSER_API_KEY` in the root `.env`. `GOOGLE_MAPS_SERVER_API_KEY` is for server geocoding; use separate restricted keys for browser, Android, and server use. See `.env.example` for the optional settings.

## Design reasoning

Each service owns its data and exchanges requests over HTTP so the assignment can demonstrate separate service boundaries without sharing a database. Registration starts inactive so an administrator can confirm that the linked customer or dispatcher profile was provisioned before granting access. The seeded admin makes that first approval possible on a fresh installation; the launch scripts distribute matching internal secrets to the services for local development.

## Running integration tests

Run the focused customer and dispatcher portal tests from their service directories:

```powershell
cd customer-service
.\mvnw.cmd -Dtest=CustomerPortalWebIntegrationTest test

cd ..\dispatcher-service
.\mvnw.cmd -Dtest=DispatcherPortalWebIntegrationTest test

cd ..\allocation-service
.\mvnw.cmd -Dtest=DeliveryLocationServiceTest test
```

The Android customer and dispatcher client tests need a running emulator or connected device:

```powershell
cd android-app
.\gradlew.bat :app:connectedDebugAndroidTest
```

The portal tests use mocked downstream services, the Android tests use a local HTTP stand-in, and the allocation test verifies location authorization and database persistence.

## Known limitations

- This is a local assignment deployment. SQLite and the current HTTP service calls are simple to run but are not designed for high concurrency or deployment across untrusted networks. WebSocket live updates and gRPC service calls are not implemented.
- Android's allocation and dispatcher URLs currently target the standard emulator host address; a physical device needs the address changes described above. Address search and map features also depend on configured Google Maps keys and APIs.
- **Authentication:** The initial admin password is a development bootstrap credential and must be changed. The app uses its own password login without MFA or SSO. Login attempt backoff is held in memory, so multiple login-service instances would need a shared limiter.
- **Deployment security:** Local HTTP uses `LOGIN_COOKIE_SECURE=false`, and browser cookies are scoped to the local host. A deployment beyond one trusted machine needs HTTPS, secure cookies, stable per-service secrets, and protected internal endpoints. Launcher-generated secrets change between full runs, so set them in `.env` if services will restart separately. See [login-service/README.md](login-service/README.md) for the authentication details.

## License

Copyright © 2026 Sujal Chand and Kiran Matthew. All Rights Reserved. This proprietary source is available for viewing, study, and educational reference only. Copying, redistribution, modification, commercial use, or reuse in another academic submission requires prior written permission. See [LICENSE.md](LICENSE.md) for the full terms.

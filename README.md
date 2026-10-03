# Naam Jap

Native Android application for Naam Jap, built with Kotlin, Jetpack Compose and Material 3.

## Requirements

- JDK 17
- Android SDK Platform 35 and platform tools
- Android Studio or JDK 17 for local builds; the checked-in Gradle Wrapper downloads Gradle 8.11.1 automatically

The app supports Android 8.0 (API 26) and newer. Supabase email authentication and authenticated cloud practice storage are implemented.

## Supabase setup

Gradle resolves `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` in this order: Gradle properties, environment variables, ignored `local.properties`, then `local.properties.example`. Copy the example to `local.properties` to override its defaults locally; `local.properties` is ignored by Git. Gradle rejects keys that do not use Supabase's `sb_publishable_` format. Never put a secret or `service_role` key in the Android app.

For GitHub Actions builds, add repository Secrets named `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`; the workflow passes them to Gradle without printing them. Add `com.naamjap.app://auth-callback` to the Supabase project's allowed redirect URLs for email confirmation and password recovery. The app uses this callback for PKCE auth links.

The existing practice columns and RLS policies have been verified. Before using cloud practice, profile, or account deletion features, review [`supabase/phase3_predeploy_checks.sql`](supabase/phase3_predeploy_checks.sql) and then apply [`supabase/migrations/202610030001_phase3_profile_and_session_rpc.sql`](supabase/migrations/202610030001_phase3_profile_and_session_rpc.sql) in a backed-up Supabase project. The migration preserves existing rows and policies; it adds profile/session-link columns, ownership/uniqueness constraints, event receipts, and authenticated RPCs. Existing completed sessions are not guessed into the record ledger; use the reviewed [`supabase/phase3_legacy_session_reconciliation_template.sql`](supabase/phase3_legacy_session_reconciliation_template.sql) where needed.

## Build

Open this directory in Android Studio and allow Gradle sync, then run the `app` configuration on a device or emulator. From PowerShell:

```shell
.\gradlew.bat assembleDebug testDebugUnitTest
```

On macOS or Linux, use `./gradlew assembleDebug testDebugUnitTest`.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

The `.github/workflows/android-build.yml` workflow installs JDK 17 and Android SDK Platform 35, then uses the checked-in Gradle Wrapper to compile the debug APK and run unit tests on pushes and pull requests. The APK is uploaded as the `naam-jap-debug-apk-<commit>` workflow artifact for 14 days.

### Push to GitHub and download the APK

If this folder is not already a Git repository, open PowerShell in the project folder and run:

```shell
git init
git add .
git commit -m "Prepare Naam Jap Android build"
git branch -M main
git remote add origin https://github.com/<YOUR-ACCOUNT>/<YOUR-REPOSITORY>.git
git push -u origin main
```

Replace the remote URL with the HTTPS URL of an empty GitHub repository. If a remote is already configured, skip `git remote add` and push to the existing remote. After the Actions run completes, open the run on GitHub and download the APK from its **Artifacts** section.

## Structure

- `app/src/main/java/com/naamjap/app/navigation`: single navigation graph and five primary destinations, plus the manual record route
- `feature/*`: screen-specific immutable UI state, ViewModels, and Compose screens
- `ui/theme`: centralized light/dark colors, typography, shapes, and spacing tokens
- `ui/components`: reusable glass navigation, surfaces, cards, actions, goal progress, inputs, and empty states

The Jap counter, manual records, home dashboard, history, profile editing, naam types, daily goal, sign out, and account deletion use authenticated Supabase operations. Session event receipts support retry-safe count actions and derive pause-aware elapsed time without adding columns to `jap_sessions`. Insights still uses illustrative preview data; daily-goal rows have no effective date, so the verified schema supports one current goal per account rather than per-day goal history. Theme preference remains stored in DataStore.

## Next phases

The feature and navigation boundaries leave room for local storage, repository implementations, offline-first counting, remote sync, widgets, and notifications without coupling those systems to screen composables.

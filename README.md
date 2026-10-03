# Naam Jap

Native Android application for Naam Jap, built with Kotlin, Jetpack Compose and Material 3.

## Requirements

- JDK 17
- Android SDK Platform 35 and platform tools
- Android Studio or JDK 17 for local builds; the checked-in Gradle Wrapper downloads Gradle 8.11.1 automatically

The app supports Android 8.0 (API 26) and newer. No credentials, network permissions, or backend services are required for the current UI phase.

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

The five primary screens and manual record form are UI foundations. Theme selection updates immediately and is saved with the Activity's UI state, but is not stored as a durable preference. The dashboard/history start with empty user data; the Insights chart uses values explicitly labeled as sample preview only. Jap, manual record save, and future preference controls do not persist data or run a counting engine.

## Next phases

The feature and navigation boundaries leave room for local storage, repository implementations, offline-first counting, remote sync, widgets, and notifications without coupling those systems to screen composables.

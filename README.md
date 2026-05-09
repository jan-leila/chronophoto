# chronophoto

An Android app for building chronophotographic timelapses — projects where you take one photo a day (or on some other cadence) and stitch them together into a video. Designed for use cases like:

- "Selfie a day" projects
- Documenting a healing process after an injury
- Tracking visible changes over time (hair growth, plant growth, construction, etc.)

The app is opinionated about *consistency*: same framing, same camera settings, same lighting, same location — so the resulting timelapse shows the subject changing rather than the photographer's environment changing.

## Features

### 1. Photo capture with rich metadata
Each photo is saved alongside the metadata needed by later features:

- **Timestamp** — used to construct the timeline
- **Camera settings** (exposure, ISO, white balance, focal length, etc.) — used to reproduce the same look across photos
- **Ambient brightness** (light sensor) — used to flag lighting discrepancies between photos
- **GPS location** — used to confirm the user is in the same spot for outdoor projects

Each metadata category is independently toggleable per project, so privacy-conscious users (or those for whom a category doesn't apply, e.g. GPS for indoor projects) can opt out.

### 2. Overlay & continuity assist
While composing a new photo:

- Overlay the previous photo on the live viewfinder at adjustable opacity to match framing
- Auto-apply the previous photo's camera settings (when available)
- Show a real-time lighting discrepancy indicator (current ambient brightness vs. last photo's)

### 3. Persistent scheduled reminders
Per project, the user can set a reminder time. The notification:

- Re-fires periodically until the photo is taken **or** the user explicitly dismisses it
- Respects do-not-disturb / system notification rules

### 4. Multiple parallel projects
A user can run several chronophotos in parallel — e.g. documenting a haircut from front, back, left, right. Each project has its own settings, schedule, photo set, and metadata preferences.

### 5. Video compilation
Export a project's photos into a video timelapse. User-controllable frame rate, resolution, and ordering.

## Implementation order

The order below is roughly dependency-driven: each phase builds on the previous one, and features that affect the data model come early so we don't have to rewrite storage later.

### Phase 1 — Foundation
1. **Project scaffolding** — Android module, Kotlin + Jetpack Compose, base navigation
2. **Data model with multi-project support** — projects, photos, per-project settings (Room database). Multi-project is baked in from day one because retrofitting it is painful.
3. **Basic photo capture** — CameraX integration, save photo to project storage
4. **Photo gallery / project view** — list projects, view photos within a project

### Phase 2 — Metadata
5. **Metadata capture pipeline** — timestamp, camera settings, ambient light sensor, GPS, with per-project toggles
6. **Metadata viewing** — display metadata for any photo

### Phase 3 — Continuity assist
7. **Previous-photo overlay** — render last photo at adjustable opacity over the live viewfinder
8. **Settings reapplication** — copy previous photo's camera settings into the current capture
9. **Lighting discrepancy indicator** — compare live ambient brightness to last photo's, show delta

### Phase 4 — Reminders
10. **Reminder scheduling** — per-project time-of-day reminder configuration
11. **Persistent notification logic** — re-fire until photo taken or explicitly dismissed (WorkManager + notifications)

### Phase 5 — Export
12. **Video compilation** — encode photos into a timelapse video with configurable framerate and resolution
13. **Share / export flow** — save to device, share via system share sheet

### Phase 6 — Polish (as needed)
- Onboarding for a new project (setting up the framing reference shot)
- Backup / export of project data
- Theming, accessibility, localization

## Tech stack (proposed)

- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Camera**: CameraX (with Camera2 interop for fine-grained settings control)
- **Database**: Room
- **Background work**: WorkManager (for reminder re-fires)
- **Video**: MediaCodec / Media3 Transformer
- **Build**: Gradle (Kotlin DSL)
- **Min SDK**: 26 (Android 8.0) — covers >95% of devices and gives us modern Camera2/CameraX APIs
- **Target SDK**: latest stable

## Development environment

A Nix flake (`flake.nix`) provides the Android SDK, JDK, Kotlin, Gradle, and the emulator. Enter the dev shell with:

```sh
nix develop
```

Android Studio is *not* provided by the flake — install it separately if you want the IDE. The flake gives you everything needed to build, run, and test from the command line.

### Dev loop

The flake exposes a few `nix run .#<name>` apps so you don't have to memorize the underlying commands. Run from the project root:

| Command | What it does |
| --- | --- |
| `nix run .#avd` | Create the `chronophoto_test` AVD if missing (Pixel 5, API 34, x86_64) |
| `nix run .#emulator` | Start the emulator (auto-creates the AVD on first run) |
| `nix run .#install` | `./gradlew installDebug` — build the APK and install it onto the running device |
| `nix run .#launch` | Launch `com.chronophoto/.MainActivity` on the running device |
| `nix run .#run` | `install` + `launch` in one shot (this is also `nix run .` / the default) |
| `nix run .#logcat` | Tail logcat filtered to the app's PID (or everything, if the app isn't running) |

Typical workflow:

```sh
nix run .#emulator        # one terminal, leave it running
nix run .#run             # another terminal, after every code change
nix run .#logcat          # optional, third terminal for debugging
```

# GPS ProBuild

Field management for a home renovation contractor. Customers, jobs, tasks, materials,
crew, photos and hours — offline first, on the phone, on the job site.

Built for **GPS Probuild Inc.**, Pickering, Ontario.

**Status: foundation build (step 1 of 14).** The database schema, design system,
device roles and navigation shell are complete and installable. Customer and job
screens are next. See [Build order](#build-order).

---

## What this is

A single-purpose Android app with no backend, no accounts and no subscription.
Everything works with the radio off, which is the point — a lot of this work happens
in basements.

Multi-device works without a server: the owner's phone holds the book of record, and
crew phones carry only their assigned jobs. Work moves between them as `.gpbpkt`
files sent over whatever channel is convenient — WhatsApp, email, Drive, or a cable.
Cost figures are stripped at export, so a crew phone never physically contains the
contract value.

Full design and data specification: `GPS-ProBuild-App-Specification.md`.

---

## Getting started

Requires **Android Studio** (Ladybug or newer) and **JDK 17**.

```bash
git clone https://github.com/<owner>/<repo>.git
cd <repo>
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

On first launch the app asks for a device name and a role. Choose **Owner** for
Gordon's phone; choose **Field** for crew phones. The owner role sets a 6-digit PIN,
which is required to switch a phone back to Owner later.

---

## Project layout

```
app/src/main/java/ca/gpsprobuild/app/
├── core/util/          Money, dates, phone and postal formatting
├── data/
│   ├── local/          Room database, entities, DAOs, SyncMeta
│   └── prefs/          DataStore settings, device identity, owner PIN
├── di/                 Hilt modules
├── domain/model/       Enums and domain types
└── ui/
    ├── theme/          Colour, type, shape, status colours
    ├── components/     Shared composables (MoneyText, StatusChip, EmptyState…)
    ├── navigation/     Routes and the role-aware bottom bar
    └── screens/        One package per screen
```

### Two things worth knowing before you edit anything

**Money is always a `Long` count of cents.** Never a `Double`. A job with forty
material lines will drift by real dollars otherwise, and that drift shows up in an
argument about a final invoice. `Money.kt` has the arithmetic; use it.

**Every entity carries `@Embedded val sync: SyncMeta`.** Room's autoincrement `id`
is device-local and will collide the moment two phones create a task. `SyncMeta.syncId`
is the real cross-device identity, and foreign keys serialize as `syncId` in packets.
Adding an entity without it will break sync in a way that is painful to unpick later.

---

## Signing and releases

Distribution is sideload. Every build is signed with the same key so updates install
over the top and keep existing data.

### Create the keystore once

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias gpsprobuild \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=GPS Probuild Inc., O=GPS Probuild Inc., L=Pickering, ST=Ontario, C=CA"
```

> **Back this file up somewhere outside the repo, along with its passwords.**
> With sideload distribution there is no Play App Signing safety net. Lose the key
> and every phone has to uninstall and reinstall, losing local data. This is the
> highest-consequence file in the project.

### Local release builds

Create `keystore.properties` in the repo root (gitignored):

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=…
keyAlias=gpsprobuild
keyPassword=…
```

### CI secrets

Add these under Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.keystore` |
| `KEYSTORE_PASSWORD` | store password |
| `KEY_ALIAS` | `gpsprobuild` |
| `KEY_PASSWORD` | key password |

### Cutting a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

`release.yml` builds a signed APK and AAB, attaches both to a GitHub release, and
deletes the decoded keystore from the runner in an `if: always()` step.

`versionCode` comes from the Actions run number and `versionName` from the tag, so
version numbers never have to be hand-edited.

### In-app updates

Settings → Check for updates reads the GitHub Releases API for this repo, and offers
to download and install a newer APK. This is the only network call the app makes, it
is behind a toggle, and it needs the repo to be public.

Set the repo coordinates in `gradle.properties` or on the command line:

```properties
githubOwner=tfitzgerald
githubRepo=gps-probuild
```

---

## Bumping versions

`gradle/libs.versions.toml` pins a known-compatible set. AGP, Kotlin, KSP and the
Compose compiler plugin must move **as a group** — bumping Kotlin alone will fail
with a compiler-version mismatch that reads like an unrelated error. Room and Hilt
are independent.

Current: AGP 8.7.3 · Kotlin 2.0.21 · KSP 2.0.21-1.0.28 · Room 2.6.1 · Hilt 2.52 ·
compileSdk 35.

---

## Data safety

Room schemas are exported to `app/schemas/` and committed, so migrations can be
tested against real historical versions. **Never add `fallbackToDestructiveMigration()`** —
a field phone that silently wipes itself on upgrade loses a week of hours that exist
nowhere else.

Cloud auto-backup is deliberately disabled for the database and photo directories.
Customer contact details live in there, and the supported recovery path is the
in-app ZIP backup, which the owner controls and stores wherever they choose.

---

## Build order

| | Step | Status |
|---|---|---|
| 1 | Foundation — schema with sync metadata, theme, roles, navigation | **Done** |
| 2 | Customers — list, detail, form, call/text/email/directions | Next |
| 3 | Jobs — list, board, detail, wizard, status pipeline, timeline | |
| 4 | Tasks — in-job tab, templates, swipe and drag, cross-job view | |
| 5 | Materials — in-job tab, suppliers, cross-job buy list | |
| 6 | Photos — capture, grid, viewer, categories, before/after | |
| 7 | Crew — staff records and assignments | |
| 8 | Time and money — entries, timer, expenses, change orders, costing | |
| 9 | Schedule — appointments, agenda/week/month, reminders | |
| 10 | Dashboard — aggregate queries and attention badges | |
| 11 | Reports — PDF engine, seven reports, CSV export | |
| 12 | Backup and restore, settings | |
| 13 | Field sync — packets, outbox, review changes, pending leads | |
| 14 | In-app updater, first tagged release | |

Steps 1–12 produce a complete single-device app. Step 13 turns it into a crew system.

---

## Licences

Bundled fonts, under the SIL Open Font License:

- **Barlow Semi Condensed** — Jeremy Tribby (`app/src/main/res/font/LICENSE-Barlow.txt`)
- **Inter** — Rasmus Andersson (`app/src/main/res/font/LICENSE-Inter.txt`)

# Tether - Keep your children in orbit

**Tether is a personal fork of [OpenTagViewer](https://github.com/parawanderer/OpenTagViewer), geared towards tracking your kids using family-owned AirTags and other compatible Find My tags from Android.** It builds on the upstream project's work with a family-focused identity and a small set of authentication fixes; it is not an official OpenTagViewer release.

<p align="center">
  <img src="branding/tether-orbit.png" width="180" alt="Tether Orbit icon: two mint companion dots and open arcs on deep teal" />
</p>

[![Android build and tests](https://github.com/zachatrocity/tether/actions/workflows/zach-apk.yml/badge.svg?branch=brand%2Ftether)](https://github.com/zachatrocity/tether/actions/workflows/zach-apk.yml)

## What it does

- View reported locations of compatible tags on Android, including tags attached to your children's backpacks or belongings.
- Keep location history using the functionality inherited from OpenTagViewer.
- Sign in using the patched Mac-compatible authentication flow, with an explicit remote-server option.
- Use a distinct **Tether** app identity and **Orbit** icon, without replacing the upstream app.

Tether shows tag reports from the Find My network, not a continuous GPS feed from a child's phone. Reports can be delayed or unavailable; this rebrand does not add live child tracking, geofence alerts, or emergency monitoring.

## Install

Download the APK from [Tether releases](https://github.com/zachatrocity/tether/releases). Current builds are personal testing builds and may be marked as prereleases.

- **App name:** Tether
- **Android package:** `io.zachs.tether`
- **Install alongside:** OpenTagViewer and the earlier OpenTagViewer (Zach) test build

A new package has separate app storage. Keep your working old installation until you have imported your tag export and signed into Tether. Your existing login and tag database are not transferred automatically.

### Obtainium

Add `https://github.com/zachatrocity/tether` as a **new app** in Obtainium:

1. Enable **Include prereleases** for testing builds.
2. Set the **release-title filter** to `^Tether` to exclude the earlier OpenTagViewer-branded release.
3. Remove any old version-pinning filter from this new entry.

This repository is public; no GitHub token is required just to access its releases.

## Setup and compatibility

Tether uses the upstream tag formats and setup procedures. See the [OpenTagViewer wiki](https://github.com/parawanderer/OpenTagViewer/wiki) for exporting tags from macOS, supported trackers, and account setup. The desktop exporter is still OpenTagViewer's; Tether does not ship a renamed exporter.

The login screen exposes **Try a remote server** before a failed sign-in. A remote Mac-compatible Anisette helper is optional infrastructure: configure your own endpoint rather than relying on a server embedded in this app. Machine identities matter to Apple sessions, so changing providers may require signing in again.

The name and icon express the intended family use. They do not broaden the upstream app's tracker compatibility or promise that every accessory works.

## Development

See [CONTRIBUTING.md](CONTRIBUTING.md) for build instructions and [branding/README.md](branding/README.md) for the Orbit artwork and reproducible asset generator. The source namespace stays upstream-compatible even though the installed package is `io.zachs.tether`.

The personal build workflow runs Python tests, JVM tests, and targeted Android emulator checks for login, native authentication-library loading, and installed branding. These are not a substitute for testing actual Apple account access and tag reports on your device.

## Upstream, credits, and licenses

Tether is based on **[OpenTagViewer by parawanderer and contributors](https://github.com/parawanderer/OpenTagViewer)**. The Mac-emulation authentication work incorporates **[Rob Lazzurs' upstream PR #274](https://github.com/parawanderer/OpenTagViewer/pull/274)**. Additional personal-fork changes expose remote-server selection and preserve remote Mac identity across authentication and session restoration.

- OpenTagViewer's original **MIT license and copyright notice** are retained in [LICENSE.TXT](LICENSE.TXT).
- Third-party attribution remains in [NOTICE](NOTICE), including the Apache-2.0-derived AirGuard portions.
- The native Mac-emulation component uses **[Unicorn 2.1.4 (GPLv2)](https://github.com/unicorn-engine/unicorn/tree/2.1.4)**. The APK is **not represented as MIT-only**; the branding change does not change its dependency licenses.
- Apple's CoreADI is downloaded and hash-checked at runtime, not bundled in the APK.

This is a personal project, not affiliated with Apple, Google, or an official OpenTagViewer release. Report fork-specific problems in [this repository's issues](https://github.com/zachatrocity/tether/issues).

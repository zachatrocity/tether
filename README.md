# Tether - Keep what matters most within orbit.

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
- Configure optional ntfy delivery for the existing opt-in AirTag left-behind alerts.

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

If Apple stops accepting a saved session, Tether keeps imported AirTags and location history on
the phone and explains why sign-in is required again. The disconnected-account notice on Family
Overview includes a **Login** action instead of leaving the user at a dead end.

### Optional ntfy endpoint

Open **Settings → AirTag notifications** to configure an ntfy server, topic, and optional
access token. The server address may include a reverse-proxy path. Delivery is off by default;
use **Send test notification** before enabling it.

The server address, topic, and enabled state persist in Tether's private app preferences. The
access token is encrypted with a dedicated key in Android Keystore, stays on this device, and is
not shown again after it is saved. HTTPS protects the token and notification in transit. LAN
HTTP endpoints are allowed for self-hosting, but both the token and notification are then sent
unencrypted.

When an enabled AirTag's existing left-behind rule fires, Tether posts the local Android
notification and starts the chosen alarm first, then schedules one ntfy request. The remote
message contains only the user-chosen AirTag label, relative last-heard age, and cautious
“may have been left behind” wording; the request time also reveals the alert time to the
configured server and topic. It does not send coordinates, place names, Apple account data,
family-profile names, or location history.

Disabled or incomplete ntfy settings send nothing. A timeout, connection refusal, or non-success
HTTP response does not delay or remove the local alert and does not stop background scanning.
Failed requests are not queued or retried; Tether logs only the outcome category and HTTP status,
when available.

The name and icon express the intended family use. They do not broaden the upstream app's tracker compatibility or promise that every accessory works.

## Family Overview

After sign-in, Tether opens the Family Overview instead of the map. It groups AirTags under
local, user-named family profiles and keeps AirTags without a valid profile in an explicit
**Unassigned AirTags** section, where each row leads with its unique AirTag name and shows the
assignment state second. Each row keeps the AirTag's emoji as its avatar, falling back to the
tag-type icon when no emoji is set. Tap a row for that AirTag's details, or use the avatar action
to rename its profile or assign an unassigned AirTag.

Each row distinguishes a recent sighting heard by this phone, a current saved report, a stale
saved report, and no report. Its age is green below 30 minutes, yellow from 30 minutes to two
hours, and red from two hours onward. The wording still names the evidence source and marks stale
data as not current; cached rows remain visible while data is loading or an account is
disconnected. Disconnection and refresh failures appear as a compact notice rather than displacing
the AirTag list with a persistent dashboard card.

Pull down on Family Overview to request fresh Apple reports for the AirTags currently assigned to
profiles. Unassigned AirTags are not queried. The list reloads from saved evidence after the
batch, and a partial failure keeps existing reports visible while naming the problem.

The floating menu on Family Overview and Map links to Family Overview, Map, Report history,
Add AirTag, and Settings. Family Overview keeps Map as its direct floating action; Report
history and Add AirTag remain available from the menu.

Family profiles are stored only in the app database. Upgrading creates no people or associations
and does not rewrite imported AirTags, account state, tag settings, or saved reports. Profile
data is not synced or shared.

## AirTag details and report history

An AirTag detail page keeps two evidence sources separate. **Nearby now** and the last local
Bluetooth sighting describe what this Android phone heard, with the phone position when one was
saved. **Last Apple network report** separately shows its age, place or coordinate fallback,
source, horizontal accuracy, and a mini map centred on that report. The map uses the selected
provider and the AirTag's emoji or tag-type icon. Reports over one hour old say **not current**;
no saved Apple position says **No report yet**. Horizontal accuracy is an uncertainty radius,
never a distance to the AirTag or a person.

From the same page, **Check again** asks Apple for a newer report, **Play sound** uses the existing
nearby Bluetooth permission flow, and **Full history** opens that AirTag's detailed saved history.
Rename and settings remain below the evidence summary. The associated-profile list names every
AirTag assigned to the same local profile and links to each other AirTag's detail page.

The global **Report history** destination is a factual feed of saved local sightings, Apple
network reports, stale reports, and actual refresh failures. It does not infer arrivals,
departures, routines, trips, or child presence. Each AirTag event retains a **Full history**
action for the detailed per-tag record.

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

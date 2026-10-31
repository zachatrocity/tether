# Tether — Orbit

Personal Android rebrand of OpenTagViewer. Upstream copyright and licensing remain in
LICENSE.TXT and NOTICE; this branding does not change third-party license obligations.

- App name: **Tether**
- Application ID: `io.zachs.tether`
- Teal: `#073D39`; mint: `#A5F3C7`
- Mark: two companion dots and two open orbit arcs. No promise of real-time location.
- Geometry and launcher assets: `scripts/tether_brand.py` (Pillow required).
- Adaptive foreground and monochrome marks are native Android vectors. In-app marks
  follow the existing Material theme for light/dark and wallpaper-color compatibility.

The source namespace and export format remain upstream-compatible. Changing application
ID creates a separate installation: keep the working old app until tags are imported and
the new app has been signed in. The existing personal-fork signing key remains unchanged.

This Android-only release does not rename or republish the upstream desktop exporter.

Debug builds deliberately inherit the main icon resources. The inherited debug source-set
background override turned the first Tether build magenta; TetherBrandingTest caught it
on the emulator. Do not restore those overrides without updating the branding contract.

# Markdown Reader 1.1.1

Fixes in-place updates (Obtainium, F-Droid clients, manual APK installs).

- **Stable release signing** — releases were previously signed with the CI runner's throwaway debug key, so every release had a different signature and Android refused to update over the installed app. Releases are now signed with a fixed key, so future updates install in place.

> **One-time step:** because 1.1 (and 1.0) were signed with different keys, this update cannot install over them — uninstall the app once and install this APK. Every release from here on updates in place.

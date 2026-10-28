Signing key rotated (the previous key was published by mistake). Android will not update over the old build, so uninstall System Tools once, then install this one. Updates work normally afterwards.

Your changes are kept: App Systemizer reads everything from the module in /data/adb/modules/systools, which survives uninstalling the app. After reinstalling, grant root to the app again in your root manager and your systemized and de-systemized apps show up as before.

- In-app updates from GitHub releases, with Stable and Dev channels
- App Systemizer: one module, keep-or-remove de-systemize
- Work Profile Apps and Root Shell

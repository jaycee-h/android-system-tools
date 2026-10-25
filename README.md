# System Tools

A personal Android toolbox for rooted devices, built feature by feature.

- Kotlin + Jetpack Compose, Material 3 Expressive with Monet dynamic color (Android 12+)
- Root access via [libsu](https://github.com/topjohnwu/libsu) (works with Magisk, KernelSU, APatch)
- minSdk 29 (Android 10), targetSdk 37

## Tools

| Tool | Description |
| --- | --- |
| App Systemizer | Systemize user apps (priv-app or app) and de-systemize system apps, via a module in `/data/adb/modules`. Applies on reboot, revertible. |
| Work Profile Apps | Enable any installed app in the work profile (or another user) with `pm install-existing`, no store needed. |
| Root Shell | Run commands as root and view the output |

### How systemizing works

- **Systemize:** creates module `systools_sys_<package>`, which mounts a copy of the APK(s) (and any extracted native libs)
  at `/system/priv-app/<package>` or `/system/app/<package>`. For priv-app, the privileged permissions the app
  requests are allowlisted in `/system/etc/permissions` so the device doesn't bootloop on allowlist enforcement.
  The `/data` install stays as an update of the new "system" app, so data and store updates are kept.
- **De-systemize:** creates module `systools_desys_<package>`, which hides the app's system directory with a `.replace`
  marker. A system app that was never updated is first installed as a user update so it survives.
- **Revert:** flags the module for removal on next boot (or deletes it right away if it was never applied).
  The modules also show up in KernelSU/Magisk/APatch, so you can remove them there if needed.

## Building

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

Both build types are signed with the release key in `signing/` (kept in this private repo
on purpose), and `versionCode` is the git commit count, so any newer build installs over an
older one. Don't lose or change the key, or updates will need an uninstall first.

Every push also builds both APKs on GitHub Actions; download them from the run's
`system-tools-apk` artifact.

## Adding a tool

1. Create a package under `app/src/main/java/com/pagnchanak/systemtools/tools/<name>/`
   with a `@Composable` screen (and a `ViewModel` if it has state). Wrap it in `ToolScaffold`
   and use the shared components in `ui/components` (`GroupedListItem`, `SearchField`, ...).
2. Run root commands through `RootShell.run(...)` (in `root/RootShell.kt`), never `Shell` directly.
3. Register it in `tools/ToolRegistry.kt`. It appears on the home screen and gets
   its own route and top bar automatically. Set `requiresRoot = false` if it works without root.

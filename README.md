# System Tools

A personal Android toolbox for rooted devices, built feature by feature.

- Kotlin + Jetpack Compose, Material 3 Expressive with Monet dynamic color (Android 12+)
- Root access via [libsu](https://github.com/topjohnwu/libsu) (works with Magisk, KernelSU, APatch)
- minSdk 29 (Android 10), targetSdk 37

## Tools

| Tool | Description |
| --- | --- |
| App Systemizer | Systemize user apps (priv-app or app) and de-systemize system apps (keep as user app, or remove completely), all through one module. Applies on reboot, revertible. |
| Work Profile Apps | Enable any installed app in the work profile (or another user) with `pm install-existing`, no store needed. |
| Root Shell | Run commands as root and view the output |

### How systemizing works

Everything lives in **one** module, `/data/adb/modules/systools` (shown as "System Tools" in
KernelSU/Magisk/APatch). Its `systools.list` records each change:

- **Systemize:** a copy of the APK(s) (and any extracted native libs) is mounted at `/system/priv-app/<package>`
  or `/system/app/<package>`. For priv-app, the privileged permissions the app requests are allowlisted in
  `/system/etc/permissions` so the device doesn't bootloop on allowlist enforcement. The `/data` install stays
  as an update of the new "system" app, so data and store updates are kept.
- **De-systemize, keep user app:** the app's system directory is hidden (`.replace` marker, or a whiteout for a
  bare `.apk`). A system app that was never updated is first installed as a user update so it survives with its data.
- **De-systemize, remove completely:** the system copy is hidden the same way and the app and its data are
  deleted. If a user copy is left over (an updated system app), the module's `service.sh` uninstalls it once
  after boot.
- **Revert / restore:** the entry is dropped from the list.

Every change rebuilds the module from the list and stages it in `/data/adb/modules_update/systools`, the root
managers' own update path, so the mounted module is never edited in place and the swap happens on reboot. Undoing a
pending change before rebooting just discards the staged update. If something goes wrong, deleting
`/data/adb/modules/systools` (e.g. from recovery) restores everything.

Builds before 0.3 created one module per app (`systools_sys_*`, `systools_desys_*`). They keep working, and the
first change made with a newer build folds them into `systools` and flags the old ones for removal.

## Building

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

Release builds are signed in CI with a key held in repo secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, optional `KEY_PASSWORD`; alias `systemtools`). Local builds fall back to the debug key and cannot update a CI-signed install. Keep the key backed up and never change it, or updates will need an uninstall first.

## Branches, releases and updates

- Work happens on `dev`. Every push to `dev` builds and replaces the rolling **dev** pre-release.
- To cut a stable release, bump `version.txt`, update `release-notes.md` and merge `dev` into `main`.
  CI tags `v<version>` and publishes it as the **Latest** release.
- Releases carry the APK and an `update.json`. No workflow artifacts are kept, and
  `cleanup.yml` removes leftovers and old runs so Actions storage stays near zero.
- **In-app updates:** Settings (gear icon on the home screen) lets you pick the Stable or Dev channel,
  turn on the check at launch, and check/install manually. The app finds the release through the GitHub API
  and falls back to `update.json` when the API limit is reached; the APK checksum is verified before installing.
  Updates need the repo's releases to be publicly downloadable.

## Adding a tool

1. Create a package under `app/src/main/java/com/pagnchanak/systemtools/tools/<name>/`
   with a `@Composable` screen (and a `ViewModel` if it has state). Wrap it in `ToolScaffold`
   and use the shared components in `ui/components` (`GroupedListItem`, `SearchField`, ...).
2. Run root commands through `RootShell.run(...)` (in `root/RootShell.kt`), never `Shell` directly.
3. Register it in `tools/ToolRegistry.kt`. It appears on the home screen and gets
   its own route and top bar automatically. Set `requiresRoot = false` if it works without root.

# System Tools

A personal Android toolbox for rooted devices, built feature by feature.

- Kotlin + Jetpack Compose (Material 3, dynamic color)
- Root access via [libsu](https://github.com/topjohnwu/libsu) (works with Magisk, KernelSU, APatch)
- minSdk 29 (Android 10), targetSdk 37

## Tools

| Tool | Description |
| --- | --- |
| Root Shell | Run commands as root and view the output |

## Building

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

The release build is signed with the debug key so it installs without extra setup.
Every push also builds both APKs on GitHub Actions; download them from the run's
`system-tools-apk` artifact.

## Adding a tool

1. Create a package under `app/src/main/java/com/pagnchanak/systemtools/tools/<name>/`
   with a `@Composable` screen (and a `ViewModel` if it has state).
2. Run root commands through `RootShell.run(...)` (in `root/RootShell.kt`), never `Shell` directly.
3. Register it in `tools/ToolRegistry.kt`. It appears on the home screen and gets
   its own route and top bar automatically. Set `requiresRoot = false` if it works without root.

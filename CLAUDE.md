# System Tools – notes for Claude

Personal Android toolbox for rooted devices. Package `com.pagnchanak.systemtools`. Repo: `pagnchanak/android-system-tools`.

## Owner's standing rules

- **Commits:** author and committer are the owner (`pagnchanak <chea.pagnchanak@gmail.com>`).
  Never add `Co-Authored-By: Claude`, session links or any other AI attribution to commits, tags or PRs.
  Set `git config user.name pagnchanak` / `user.email chea.pagnchanak@gmail.com` before committing.
- **Branches:** work on `dev`. Merge `dev` into `main` (fast-forward) only to cut a stable release.

## Build and release (CI only)

- `.github/workflows/build.yml` builds on every push. No workflow artifacts are uploaded.
- **Dev:** push to `dev` -> replaces the rolling `dev` pre-release (`SystemTools-<version>-dev.<run>.apk` + `update.json`).
- **Stable:** bump `version.txt`, write `release-notes.md`, merge to `main` -> CI creates tag `v<version>` and the
  stable release (marked Latest).
- `versionCode` = workflow run number (shared by both channels); local builds use the commit count.
- Signing: repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD` (alias `systemtools`, optional `KEY_PASSWORD`). The repo is public: never commit a keystore or passwords.
- `.github/workflows/cleanup.yml` deletes leftover artifacts and old runs after each build and weekly.
- In-app updates (`update/`, `ui/settings/`): the app follows the channel it was built from, or the one chosen in
  Settings. It reads the release via the GitHub API and falls back to `update.json` (SHA-256 verified).
  

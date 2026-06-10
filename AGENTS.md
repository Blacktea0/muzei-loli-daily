# AGENTS.md

Quick orientation for AI coding agents working in this repo.

## Project overview

Android app (Kotlin + Jetpack Compose + Material 3) that acts as a [Muzei Live Wallpaper](https://muzei.co/) art source, pulling daily artwork from the [Loli Commons API](https://loliconey.tsuki.ga/). Users can react to artwork via [Bangumi](https://bgm.tv/) OAuth.

- Package: `me.eroi.lolidaily.muzei`
- Min SDK 28, Target/Compile SDK 37, JVM 17, Kotlin 2.3.21
- Single module: `:app`

## Build & run

```bash
# Build APK
./gradlew assembleDebug

# Build + install to connected device
./gradlew installDebug

# Format all Kotlin sources (4-space indent, ktlint_official)
./gradlew ktlintFormat

# Check formatting (dry-run, CI)
./gradlew ktlintCheck

# Static analysis (includes ktlintCheck)
./gradlew lint

# Full build
./gradlew build
```

A git pre-commit hook runs `ktlintFormat` automatically. There are no tests defined yet.

**Important**: Always use `./gradlew installDebug` to install the APK to a connected device. Do NOT use `adb install` directly, as it may cause signature mismatch issues.

## Mock server

See [`mock/README.md`](mock/README.md) for API endpoints, fixture editing, ADB configuration commands, and common test scenarios.

```bash
./gradlew startMockServer   # start in background
./gradlew stopMockServer    # stop
./gradlew mockLogs          # view server.log
```

## Scripts

See [`scripts/README.md`](scripts/README.md) for available scripts and usage instructions.

## CI/CD

See [`BUILD.md`](BUILD.md) for workflow details, version numbering, and required GitHub Secrets.

## Architecture

See [`PRODUCT.md`](PRODUCT.md) for architecture, data flow, and key design decisions.

## Conventional Commits

All commits MUST follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>[(<scope>)]: <description>
```

- Description MUST be lowercase, imperative, no trailing period
- English only
- Examples:
  - `feat(ui): add fullscreen image viewer`
  - `fix(api): handle network timeout in reaction fetch`
  - `refactor(db): extract entity mapper`
  - `chore(build): update target SDK to 37`

# CI/CD

GitHub Actions workflow (`.github/workflows/release.yml`) automates build and release.

## Workflow

- **Trigger**: manual dispatch or tag push (`v*`)
- **Lint job**: runs `ktlintCheck` and `lint`
- **Build job**: decodes keystore from secrets, builds release APK, uploads as artifact
- **Release job**: creates GitHub Release with APK (only on tag push)

## Version Numbering

Tags like `v1.2.3` are converted to:

- `VERSION_NAME`: `1.2.3`
- `VERSION_CODE`: `MAJOR * 1000000 + MINOR * 1000 + PATCH` (e.g., `1002003`)

This allows up to 999 patches per minor version and 999 minor versions per major version.

## Required GitHub Secrets

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded release keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias (e.g. `release`) |
| `KEY_PASSWORD` | Key password |

See [`scripts/README.md`](scripts/README.md) for the `setup-keystore.sh` script that generates these values.

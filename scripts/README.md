# Scripts

Utility scripts for project setup and CI/CD.

## setup-keystore.sh

Generates a release keystore and encodes it to Base64 for GitHub Secrets configuration.

```bash
./scripts/setup-keystore.sh
```

What it does:

1. Generates a 2048-bit RSA keystore (`release.keystore`) with alias `release`
2. Encodes the keystore to Base64
3. Prints the Base64 string and instructions for adding GitHub Secrets

### GitHub Secrets to configure

| Secret | Value |
|--------|-------|
| `KEYSTORE_BASE64` | Base64-encoded keystore (script output) |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_ALIAS` | `release` |
| `KEY_PASSWORD` | Your key password |

Configure at: Repository Settings → Secrets and variables → Actions

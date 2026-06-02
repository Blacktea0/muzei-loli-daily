#!/bin/bash
# Generate release keystore and encode to Base64
# Usage: ./scripts/setup-keystore.sh

set -e

KEYSTORE_FILE="release.keystore"
KEY_ALIAS="release"

echo "=== Generating Release Keystore ==="
echo ""

# Check if file already exists
if [ -f "$KEYSTORE_FILE" ]; then
    echo "Warning: $KEYSTORE_FILE already exists"
    read -p "Overwrite? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Cancelled"
        exit 0
    fi
fi

# Generate keystore
keytool -genkey -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000

echo ""
echo "=== Keystore Generated Successfully ==="
echo ""

# Encode to Base64
echo "=== Base64 Encoding ==="
echo ""
echo "Add the following to GitHub Secrets:"
echo ""
echo "KEYSTORE_BASE64:"
echo "----------------------------------------"
base64 -i "$KEYSTORE_FILE" | tr -d '\n'
echo ""
echo "----------------------------------------"
echo ""
echo "Other Secrets to add:"
echo "  KEYSTORE_PASSWORD: Your keystore password"
echo "  KEY_ALIAS: $KEY_ALIAS"
echo "  KEY_PASSWORD: Your key password"
echo ""
echo "Configure at: Repository Settings → Secrets and variables → Actions"

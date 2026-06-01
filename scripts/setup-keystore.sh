#!/bin/bash
# 生成 release keystore 并编码为 Base64
# 用法: ./scripts/setup-keystore.sh

set -e

KEYSTORE_FILE="release.keystore"
KEY_ALIAS="release"

echo "=== 生成 Release Keystore ==="
echo ""

# 检查是否已存在
if [ -f "$KEYSTORE_FILE" ]; then
    echo "警告: $KEYSTORE_FILE 已存在"
    read -p "是否覆盖? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "已取消"
        exit 0
    fi
fi

# 生成 keystore
keytool -genkey -v \
    -keystore "$KEYSTORE_FILE" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000

echo ""
echo "=== Keystore 生成完成 ==="
echo ""

# 编码为 Base64
echo "=== Base64 编码 ==="
echo ""
echo "请将以下内容添加到 GitHub Secrets:"
echo ""
echo "KEYSTORE_BASE64:"
echo "----------------------------------------"
base64 -i "$KEYSTORE_FILE" | tr -d '\n'
echo ""
echo "----------------------------------------"
echo ""
echo "其他需要添加的 Secrets:"
echo "  KEYSTORE_PASSWORD: 你设置的 keystore 密码"
echo "  KEY_ALIAS: $KEY_ALIAS"
echo "  KEY_PASSWORD: 你设置的 key 密码"
echo ""
echo "配置位置: 仓库 Settings → Secrets and variables → Actions"

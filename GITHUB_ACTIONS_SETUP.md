# GitHub Actions 配置说明

## 工作流概览

项目配置了以下 GitHub Actions 工作流：

### 触发条件

- **Push to main**: 运行 lint + 构建 APK（不发布）
- **Pull Request**: 运行 lint + 构建 APK（不发布）
- **Push tag `v*`**: 运行 lint + 构建 APK + 创建 GitHub Release

### 工作流程

```
Push/PR → Lint Check → Build APKs → Upload Artifacts
                                          ↓ (if tag v*)
                                    Create Release
```

## 配置签名（可选）

默认情况下，release 构建会使用 debug 签名。如需正式签名：

### 方式一：使用脚本（推荐）

```bash
chmod +x scripts/setup-keystore.sh
./scripts/setup-keystore.sh
```

脚本会：
1. 生成 keystore 文件
2. 输出 Base64 编码
3. 列出需要配置的 Secrets

### 方式二：手动配置

#### 1. 生成 Keystore

```bash
keytool -genkey -v -keystore release.keystore \
  -alias your-alias \
  -keyalg RSA -keysize 2048 -validity 10000
```

### 2. 编码为 Base64

```bash
base64 -i release.keystore | tr -d '\n'
```

### 3. 配置 GitHub Secrets

在仓库 Settings → Secrets and variables → Actions 中添加：

| Secret 名称 | 说明 |
|-------------|------|
| `KEYSTORE_BASE64` | Base64 编码的 keystore 文件 |
| `KEYSTORE_PASSWORD` | Keystore 密码 |
| `KEY_ALIAS` | Key 别名 |
| `KEY_PASSWORD` | Key 密码 |

## 发布流程

### 自动发布

```bash
# 1. 更新版本号（可选，会自动从 tag 提取）
# 在 app/build.gradle.kts 中的 versionName 会被环境变量覆盖

# 2. 创建并推送 tag
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions 会自动：
1. 运行代码检查
2. 构建 debug 和 release APK
3. 创建 GitHub Release
4. 上传 APK 到 Release

### 版本号规则

- `VERSION_NAME`: 从 tag 提取（如 `v1.2.3` → `1.2.3`）
- `VERSION_CODE`: 自动计算（如 `1.2.3` → `10203`）

## 查看构建状态

- 在仓库 Actions 页面查看工作流运行状态
- 每次构建会上传 APK artifacts，可在 Actions 详情页下载
- Release 页面包含正式发布的 APK

## 手动触发

如需手动触发发布，可使用 GitHub CLI：

```bash
gh workflow run release.yml --ref main
```

注意：手动触发不会创建 Release，只构建 APK。

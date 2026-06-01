# GitHub Actions 配置完成

## 已完成的修改

### 1. app/build.gradle.kts
- 添加了版本号环境变量支持（`VERSION_CODE`, `VERSION_NAME`）
- 添加了签名配置（从环境变量读取）

### 2. .github/workflows/release.yml
创建了完整的 CI/CD 工作流：
- **Lint Check**: ktlint + lint 检查
- **Build APKs**: 构建 debug 和 release APK
- **Create Release**: 推送 tag 时自动创建 GitHub Release

### 3. 配套文件
- `GITHUB_ACTIONS_SETUP.md`: 详细配置说明
- `scripts/setup-keystore.sh`: Keystore 生成脚本

## 使用方法

### 快速开始（无签名）

```bash
# 推送代码到 main 分支
git add .
git commit -m "feat: add GitHub Actions CI/CD"
git push origin main

# 创建 release
git tag v1.0.0
git push origin v1.0.0
```

### 配置签名（可选）

```bash
# 运行 keystore 生成脚本
chmod +x scripts/setup-keystore.sh
./scripts/setup-keystore.sh

# 按照输出配置 GitHub Secrets
```

## 工作流程

```
Push main → Lint → Build → Upload Artifacts
                              ↓ (if tag v*)
                        Create Release + Upload APKs
```

## 版本号规则

- Tag: `v1.2.3`
- VERSION_NAME: `1.2.3`
- VERSION_CODE: `10203` (自动计算)

## 验证配置

1. 推送代码后查看 GitHub Actions 页面
2. 检查 lint 和 build 是否成功
3. 推送 tag 后检查 Release 页面是否创建

## 下一步

1. 配置签名（如果需要正式签名的 APK）
2. 测试工作流：推送代码 → 创建 tag → 检查 Release
3. 根据需要调整工作流配置

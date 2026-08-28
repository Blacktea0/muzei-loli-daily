# Loli Daily Muzei

<p align="center">
  <img src="assets/header.png" alt="Loli Daily Muzei" width="100%">
</p>

[English](README_EN.md)

一个 [Muzei Live Wallpaper](https://muzei.co/) 艺术源插件，每天从 [Loli Commons API](https://loliconey.tsuki.ga/) 获取投稿的插图并可设为手机壁纸。

## 功能特性

### 基础功能

支持网页端每日萝莉的大部分功能，包括：

- 查看图片
- 贴贴互动
- 查看评论（发送评论以及给评论贴贴仍不支持）

除此之外，这个应用还是一个 Muzei 的插件，可以将每日的图片设为壁纸。

### 额外功能

除了一些网页端现有的功能，这个应用做了一些我认为可能有用的功能：

- 支持添加图片收藏，让看过一次的图片不再随时间消失
- 支持通过其他应用（当前支持X、Pixiv、BiliBili）分享到这个应用，自动填充图片文件和图片信息用于投稿
- 支持在投稿队列中查看本机提交但尚未展示的图片，并在图片出场后自动清理
- 支持直接在应用搜索角色，这里感谢 [Bangumi Research](https://chii.ai/) 提供的搜索服务
- 支持导出图片
- ……

## 构建

### 环境要求

- JDK 17
- Android SDK (compileSdk 37)

### 构建步骤

```bash
# 克隆仓库
git clone https://github.com/Blacktea0/muzei-loli-daily.git
cd muzei-loli-daily

# 调试构建
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug
```

### 签名配置

在项目根目录创建 `.env` 文件配置签名信息：

```env
KEYSTORE_FILE=release.keystore
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=release
KEY_PASSWORD=your_key_password
```

或参考 [`scripts/README.md`](scripts/README.md) 使用 `setup-keystore.sh` 脚本生成签名配置。

## 许可

本项目为开源项目。详情请参阅 [GitHub 仓库](https://github.com/Blacktea0/muzei-loli-daily)。

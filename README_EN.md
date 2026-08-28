# Loli Daily Muzei

<p align="center">
  <img src="assets/header.png" alt="Loli Daily Muzei" width="100%">
</p>

[中文](README.md)

A [Muzei Live Wallpaper](https://muzei.co/) art source plugin that fetches daily illustrations from the [Loli Commons API](https://loliconey.tsuki.ga/) and sets them as your phone wallpaper.

## Features

### Core Features

Supports most features available on the Daily Loli web client, including:

- View images
- Interact with "pat" reactions
- View comments (sending comments and patting comments are not yet supported)

In addition, this app works as a Muzei plugin that can automatically set the daily image as your wallpaper.

### Extra Features

Beyond the existing web client functionality, this app includes some features I believe are useful:

- **Image collection** — save images so they don't disappear over time
- **Share from other apps** — supports sharing images from X, Pixiv, and BiliBili into this app, auto-filling image files and metadata for submissions
- **Submission queue** — view images submitted from this device that have not appeared yet; published entries are removed automatically
- **Character search** — search characters directly in the app, powered by [Bangumi Research](https://chii.ai/)
- **Image export**

## Building

### Prerequisites

- JDK 17
- Android SDK (compileSdk 37)

### Build Steps

```bash
# Clone the repository
git clone https://github.com/Blacktea0/muzei-loli-daily.git
cd muzei-loli-daily

# Debug build
./gradlew assembleDebug

# Install to device
./gradlew installDebug
```

### Signing Configuration

Create a `.env` file in the project root with your signing credentials:

```env
KEYSTORE_FILE=release.keystore
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=release
KEY_PASSWORD=your_key_password
```

Or refer to [`scripts/README.md`](scripts/README.md) to use the `setup-keystore.sh` script for generating signing configuration.

## License

This is an open-source project. See the [GitHub repository](https://github.com/Blacktea0/muzei-loli-daily) for details.

# Android Rufus

Android Rufus is a production-grade application that allows users to create bootable USB drives directly from their Android devices using USB OTG, inspired by Rufus for Windows.

## Features

- **Modern Interface**: Designed using Material 3 and Jetpack Compose. Follows a clean, dark-themed "Sleek Interface".
- **USB Device Detection**: Automatically scans and lists connected USB drives over OTG.
- **Image Selection**: Leverages Android's Storage Access Framework (SAF) to pick ISO, IMG, or ZIP images from local storage.
- **Robust Architecture**: Built on Clean Architecture principles with separated UI, Domain, and Data modules. Uses Coroutines + StateFlows for reactive UI updates.
- **Background Operations**: Progress streams safely through a `WriteEngine` (currently simulated, ready for low-level NDK/block-device integration).

## Tech Stack

- **Kotlin**
- **Jetpack Compose**
- **MVVM Architecture**
- **Coroutines + Flow**
- **Material 3**
- **Storage Access Framework (SAF)**

## Project Structure

- `app/src/main/java/com/example/ui/` - Jetpack Compose UI components and ViewModels.
- `app/src/main/java/com/example/domain/` - Business logic, Models, and Repository interfaces.
- `app/src/main/java/com/example/data/` - Concrete implementations for Repositories and Engines.
- `app/src/main/java/com/example/di/` - Dependency Injection configurations.

## Setup Guide

1. Clone or download the repository to your local machine.
2. Open the project in Android Studio (Giraffe or newer recommended).
3. Sync the Gradle project to download dependencies.
4. To test USB OTG features, you must test on a physical Android device (API 26+) using a USB-C/OTG adapter. Emulators do not fully support OTG block device interactions.

## Architecture

```
[UI Layer (Compose + ViewModel)]
            |
            v
[Domain Layer (Use Cases & Interfaces)] <--- [Models: WriteConfig, UsbDeviceDomainModel, ImageFile]
            |
            v
[Data Layer (Repositories & Engines)] ---> [Android APIs: UsbManager, SAF]
```

## TODO / Future Roadmap

- [ ] Implement actual low-level block writing via NDK/C++ engine for raw image flashing.
- [ ] Implement `WorkManager` for persistent background writing.
- [ ] Add Room Database for history and write logs persistence.
- [ ] Extend `UsbManager` polling with dynamic `BroadcastReceiver` for instant attach/detach events.
- [ ] Handle complex file systems (NTFS, EXT4) using custom userspace file system drivers if necessary.

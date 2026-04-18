<p align="center">
  <img src="app/src/main/res/drawable/ic_launcher_master.png" alt="RoutePix Logo" width="120" />
</p>

<h1 align="center">📸 RoutePix</h1>

<p align="center">
  <strong>The only collaborative photo app that uses Telegram as free, unlimited cloud storage.</strong>
</p>

<p align="center">
  <em>No subscriptions. No server costs. No compression. Your photos, your bot, your rules.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF?logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/Firebase-Auth_%7C_Firestore-FFCA28?logo=firebase&logoColor=black" />
  <img src="https://img.shields.io/badge/Telegram_Bot_API-Storage_Engine-26A5E4?logo=telegram&logoColor=white" />
  <img src="https://img.shields.io/badge/Releases-12+-brightgreen" />
</p>

---

## 🤔 The Problem

Every photo sharing solution compromises on something:

| Solution | The Catch |
|----------|-----------|
| Google Photos | Free tier eliminated. 15 GB shared across Gmail, Drive, Photos. Paid beyond that. |
| WhatsApp / Telegram Groups | Aggressive lossy compression. No original quality. No organization. |
| iCloud / OneDrive | Platform-locked. No cross-platform collaboration. |
| Self-hosted (NAS) | Requires hardware, networking knowledge, and ongoing maintenance. |

**RoutePix eliminates all of these trade-offs.** It repurposes the Telegram Bot API — which offers unlimited, free file storage up to 50 MB per file — as a zero-cost, decentralized cloud backend. There is no other app like this.

---

## ⚡ How It Works

```
┌─────────────────────────────────────────────────────────────────┐
│                        RoutePix App                             │
│                                                                 │
│  User selects 200 photos from gallery                           │
│       │                                                         │
│       ▼                                                         │
│  ┌──────────────────────────────────────────────┐               │
│  │  WorkManager Upload Queue (Room DB)          │               │
│  │  Persistent, survives app kills & reboots    │               │
│  └──────────────┬───────────────────────────────┘               │
│                 │ Sequential, one-by-one                        │
│                 ▼                                               │
│  ┌──────────────────────────────────────────────┐               │
│  │  Dual-Format Upload Engine                   │               │
│  │                                              │               │
│  │  Step 1: sendPhoto API                       │               │
│  │  → Compressed thumbnail for fast browsing    │               │
│  │  → Coil CDN-cached for instant gallery loads │               │
│  │                                              │               │
│  │  Step 2: sendDocument API                    │               │
│  │  → Byte-perfect original (≤50 MB)            │               │
│  │  → Motion Photos with embedded video intact  │               │
│  │  → Files >50MB auto-compressed to fit limit  │               │
│  │                                              │               │
│  │  Step 3: Firestore Metadata Write            │               │
│  │  → photoId, tripId, telegramFileId,          │               │
│  │    telegramDocumentId, md5Hash, EXIF data    │               │
│  └──────────────────────────────────────────────┘               │
│                 │                                               │
│                 ▼                                               │
│        Telegram Bot Chat                                        │
│   (Admin's private bot = free ∞ storage)                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## ✨ Features

### 📸 Collaborative Trip Albums
- Create a trip → get an invite code → share with friends → everyone uploads to the same album.
- All photos are backed up to a single Telegram Bot owned by the trip admin — **zero server costs**.

### 🔐 Security Architecture
- **AES-256-GCM encryption** for Telegram credentials stored in Firestore, with SHA-256 derived keys.
- **AndroidX EncryptedSharedPreferences** for local bot token isolation per trip.
- **Credential cascading**: Non-admin members upload using per-trip cached credentials — the admin's master token never touches their device.

### 🖼️ Premium Gallery Engine
- **Custom Coil ImageLoader** with aggressive Telegram CDN caching: 25% memory pool allocation, disk caching keyed by `telegramFileId`, shimmer loading placeholders.
- **Gesture-driven viewer**: Horizontal swipe between photos, vertical swipe-to-dismiss, pinch-to-zoom, bottom filmstrip for fast navigation, crossfade transitions.
- **Smart album sorting**: Group by Tag, by Uploader, or by Date — all with animated transitions.

### 📤 Bulletproof Upload Pipeline
- **Room database queue** → **WorkManager** → sequential upload with per-photo progress tracking.
- Survives app kills, process death, and device reboots. No photo ever gets lost.
- Foreground notification with real-time `uploaded/total` progress.
- **Automatic retry** with exponential backoff for rate limits (HTTP 429) and timeouts.

### 🔄 In-App Auto-Updates
- Checks GitHub Releases API on every launch for newer versions.
- One-tap APK download link — no Play Store dependency.

---

## 🛠️ Tech Stack

| Layer | Technology | Why This Choice |
|-------|------------|-----------------|
| **UI** | Jetpack Compose + Material 3 | Declarative, reactive, single-activity architecture |
| **Architecture** | MVVM + `StateFlow` | Unidirectional data flow, lifecycle-aware |
| **Auth** | Firebase Auth + Google Sign-In | Zero-friction onboarding |
| **Remote DB** | Cloud Firestore | Real-time sync, security rules per trip |
| **Local DB** | Room (upload queue) | Persistent queue survives process death |
| **Background** | WorkManager | Guaranteed execution, battery-optimized |
| **Network** | Retrofit + OkHttp → Telegram Bot API | Type-safe HTTP, multipart file uploads |
| **Image Loading** | Coil with custom `ImageLoader` | CDN caching, memory/disk strategies, shimmer |
| **Security** | AndroidX Security-Crypto + AES-256-GCM | Encrypted credentials at rest and in transit |
| **Distribution** | GitHub Releases + in-app UpdateChecker | Independent of Play Store approval cycles |

---

## 📦 Release History — 12+ Versioned Releases

| Version | Highlight |
|---------|-----------|
| **v2.0.2** | Premium Coil Image Engine — custom `ImageLoader` with 25% memory pool, CDN caching, and lazy-list stable keys for buttery-smooth scrolling |
| **v2.0.1** | UX polish — immediate loading overlay after photo selection, download filename convention, `DOWNLOAD_WITHOUT_NOTIFICATION` permission fix |
| **v2.0.0** | **Dual-Format Upload Engine** — every photo uploaded as both compressed thumbnail (`sendPhoto`) and byte-perfect original (`sendDocument`). Motion Photo support. |
| **v1.2.1** | Auto-update notifications via GitHub Releases API |
| **v1.2.0** | UX overhaul — global multi-selection, horizontal swipe, vertical swipe-to-dismiss, crossfade transitions, predictive back gesture, hardened Firestore rules |
| **v1.1.8** | Dynamic pinch-to-zoom gallery grid, admin bulk delete, zero-bandwidth cross-tag duplication |
| **v1.1.7** | Admin-only tag renaming, redesigned full-screen viewer |
| **v1.1.6** | Smart image compression engine for Telegram's 10 MB `sendPhoto` limit |
| **v1.1.5** | Background resource management, temp-file lifecycle, Motion Photo edge cases |
| **v1.1.4** | Dedicated upload queue view with pending/failed status tracking |
| **v1.1.3** | Timeline sorting: by Uploader, by Tag, by Date |
| **v1.1.2** | Custom tag input in upload bottom sheet |
| **v1.1.1** | Jetpack Compose NaN scaling crash fix, Retrofit memory leak fix |

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug+ with Kotlin 2.0
- A Firebase project with Auth + Firestore enabled
- A Telegram Bot (created via [@BotFather](https://t.me/BotFather))

### Setup
1. Clone the repo and open in Android Studio
2. Place your `google-services.json` in `app/`
3. Build and run — sign in with Google
4. Go to **Settings** → paste your Telegram Bot Token and Chat ID
5. Create a trip and start uploading!

> **Note:** Since RoutePix is distributed via GitHub Releases (not Play Store), Android may show a Play Protect warning. Tap **More details → Install anyway** to proceed.

---

## 📐 Project Structure

```
RoutePix/
├── app/src/main/java/com/routepix/
│   ├── data/
│   │   ├── local/          # Room DB — QueuedPhoto, DAO, Database
│   │   ├── model/          # PhotoMeta, Trip, User data classes
│   │   ├── remote/         # Retrofit client, TelegramApi interface
│   │   └── repository/     # TripRepository, UserRepository
│   ├── security/           # AES-256-GCM SecurityManager
│   ├── worker/             # PhotoUploadWorker (WorkManager)
│   ├── ui/
│   │   ├── auth/           # Google Sign-In screen + ViewModel
│   │   ├── create/         # Create Trip flow
│   │   ├── join/           # Join Trip via invite code
│   │   ├── home/           # Trip dashboard, saved photos
│   │   ├── timeline/       # Photo grid, TelegramAsyncImage, viewer
│   │   ├── settings/       # Bot token configuration
│   │   ├── components/     # Glass morphism components, loader
│   │   └── theme/          # Material 3 color/theme
│   ├── util/               # Download manager, EXIF, notifications, update checker
│   └── navigation/         # Compose Navigation routes
└── build.gradle.kts
```

---

<p align="center">
  <strong>No cloud bill. No compression. No limits.</strong><br/>
  <em>Just Telegram, a bot, and your memories — forever.</em>
</p>

<p align="center">
  Made with ❤️ by <a href="https://github.com/sksalapur">Shashank Salapur</a>
</p>

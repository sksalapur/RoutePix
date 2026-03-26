# 📸 RoutePix

**Collaborative Trip Photo Sharing & Telegram Backup**

RoutePix is a modern Android app for collaborative trip photo management. It combines a premium gallery experience with automated Telegram backups, ensuring your travel memories are always safe, organized, and under your control.

---

## 🔒 Privacy & Security First

**Your Data, Your Bot, Your Rules.**
Unlike traditional cloud photo services, RoutePix does **not** upload your photos to random third-party servers. 
- **Decentralized Storage:** Every photo uploaded in a trip is sent directly to the **Admin's own Telegram Bot**.
- **Self-Hosted Backup:** You own the bot, you own the storage. The app simply acts as a gorgeous gallery interface over your personal Telegram chat.
- **Credential Isolation:** Non-admin members seamlessly upload to the admin's bot using per-trip cached credentials, completely isolating your primary bot token from their devices.

---

## ✨ Features

- **Collaborative Trips** — Create or join trips via invite codes. All members can upload photos together.
- **Bulk & Folder Uploads** — Select an entire folder or hundreds of photos at once. Photos upload one-by-one safely in the background.
- **Original Quality Preservation** — Every photo is uploaded twice: once as a compressed thumbnail for fast in-app browsing, and once as a full-quality document for downloads. Files under 50MB are preserved byte-for-byte — including Motion Photos with embedded video. Files over 50MB are intelligently compressed to just under the Telegram limit while maintaining maximum visual fidelity.
- **Smart Albums** — Photos are auto-grouped by tag, date, or uploader.
- **Gesture-Driven Gallery** — Premium photo viewer with horizontal swiping, swipe-to-dismiss, pinch-to-zoom, and a bottom filmstrip for seamless navigation.
- **Multi-Select for Everyone** — All trip members can long-press to select multiple photos for bulk download. Deletion remains admin-only.
- **Fluid Navigation** — Smooth crossfade transitions, slide animations, and Android predictive back gesture support.
- **Automatic Update Alerts** — The app checks GitHub for newer releases on startup and notifies you with a direct download link.
- **Download & Organize** — Download individual photos or entire albums in original quality, saved straight to your device.

---

## 🚀 Installation & Setup

### 1. The Play Protect Popup
Since RoutePix is not yet distributed through the Google Play Store, Android's Play Protect may show an **"Unsafe app blocked"** or similar warning when installing the APK.
> **Note:** Just tap **More details** -> **Install anyway** to proceed. RoutePix is completely safe and open-source!

### 2. Creating Your Telegram Bot (Admin Only)
To create a trip, you need your own Telegram Bot. It takes 2 minutes:
1. Open Telegram and search for **[@BotFather](https://t.me/BotFather)**.
2. Send the command `/newbot` and follow the prompts to give it a name and username.
3. BotFather will give you a **Bot Token** (e.g., `123456:ABC-DEF1234...`). Copy this.
4. Next, search for **[@userinfobot](https://t.me/userinfobot)** in Telegram and send it a message. It will reply with your **Chat ID** (a number like `123456789`). Copy this too.

### 3. App Setup
1. Launch RoutePix and Sign In with Google.
2. Go to the **Settings** page (top right corner profile icon).
3. Paste your **Telegram Bot Token** and **Chat ID** from the steps above, and click **Save**.
4. You are now ready to create trips and invite friends!

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| **UI** | Jetpack Compose, Material 3, Coil |
| **Architecture** | MVVM with reactive `StateFlow` |
| **Auth** | Firebase Auth + Google Sign-In |
| **Database** | Cloud Firestore (remote), Room (local queue) |
| **Background** | WorkManager for persistent chunked uploads |
| **Network** | Retrofit & OkHttp (Telegram Bot API) |
| **Security** | AndroidX Security-Crypto |

---

## 📦 Release History (Since 1.1.0)

- **v2.0.1** - **UX & Stability Polish.** Added immediate visual feedback (loading overlay) after photo selection to eliminate the "nothing happens" gap. Updated download filename convention to `RoutePix_<timestamp>.jpg`. Fixed a critical `SecurityException` crash by adding the `DOWNLOAD_WITHOUT_NOTIFICATION` permission.
- **v2.0.0** - **Dual-Format Upload Engine.** Every photo is now uploaded as both a compressed thumbnail (`sendPhoto`) and a full-quality document (`sendDocument`). Downloads always retrieve the original-quality version. Files under 50MB are preserved untouched — including Motion Photos. Removed misleading file size display from detailed view.
- **v1.2.1** - Added automatic app update notifications via the GitHub Releases API. Users are notified when a new version is available with a direct download link.
- **v1.2.0** - UX Overhaul: global multi-selection for all users, horizontal swipe between photos, vertical swipe-to-dismiss, crossfade album transitions, predictive back gesture support, admin-only trip editing, and hardened Firestore security rules.
- **v1.1.8** - Implemented a dynamic native Pinch-to-Zoom Gallery Grid, Admin Multiple Photo Bulk Delete, file size display in Detailed view, and a Zero-Bandwidth Cross-Tag Duplication feature for copying photos to new tags.
- **v1.1.7** - Added Admin-Only Tag Renaming across whole albums. Completely redesigned and uncluttered the full-screen photo viewer.
- **v1.1.6** - Integrated the built-in Smart Image Compression Engine to resolve Telegram API HTTP 400 errors for files exceeding 10MB.
- **v1.1.5** - Improved background resource management, temp-file lifecycle, and established logic bypasses for oversized Motion Photos.
- **v1.1.4** - Created a dedicated "Upload Queue" view so users can track pending or failed background uploads.
- **v1.1.3** - Shipped completely new Timeline sorting modes: Group `By Uploader`, `By Tag`, and `By Date`.
- **v1.1.2** - Added customizable input functionality to the Tag Upload Bottom Sheet UI.
- **v1.1.1** - Crucial hotfixes resolving Jetpack Compose NaN scaling crashes and Retrofit network memory leaks.

---

## 🎧 Support & Contact

Need help, have feature requests, or found a bug? 
Feel free to reach out via email: **[sksalapur@gmail.com](mailto:sksalapur@gmail.com)**

---

## 📄 License

This project is open source under the [MIT License](LICENSE).

---

*Made with ❤️ by the RoutePix Team.*

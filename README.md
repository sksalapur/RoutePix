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
- **Smart Albums** — Photos are auto-grouped by tag, date, or uploader.
- **Gesture-Driven Gallery** — Premium photo viewer with swipe gestures and a bottom filmstrip for seamless navigation.
- **Download & Organize** — Download individual photos or entire albums, saved straight to your device.

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

## 🎧 Support & Contact

Need help, have feature requests, or found a bug? 
Feel free to reach out via email: **[sksalapur@gmail.com](mailto:sksalapur@gmail.com)**

---

## 📄 License

This project is open source under the [MIT License](LICENSE).

---

*Made with ❤️ by the RoutePix Team.*

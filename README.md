# 📸 RoutePix

**Collaborative Trip Photo Sharing & Telegram Backup**

RoutePix is a modern Android app for collaborative trip photo management. It combines a premium gallery experience with automated Telegram backups, ensuring your travel memories are always safe and organized.

---

## ✨ Features

- **Collaborative Trips** — Create or join trips via invite codes. All members can upload photos that are organized together.
- **Gesture-Driven Gallery** — Premium photo viewer with swipe gestures and a bottom filmstrip for seamless navigation.
- **Smart Albums** — Photos are auto-grouped by tag, date, or uploader. Create custom tags per upload.
- **Telegram Backup** — Every photo is automatically backed up to a configured Telegram bot. Non-admin members use the admin's bot credentials seamlessly.
- **Real-Time Upload Progress** — Track bulk uploads with a reactive progress bar powered by `WorkManager`.
- **Download & Organize** — Download individual photos or entire albums, saved into organized folders.
- **Material 3 Design** — Sleek, modern interface following Material You guidelines.

---

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| **UI** | Jetpack Compose, Material 3, Coil |
| **Architecture** | MVVM with reactive `StateFlow` |
| **Auth** | Firebase Auth + Google Sign-In |
| **Database** | Cloud Firestore (remote), Room (local queue) |
| **Background** | WorkManager for persistent uploads |
| **Network** | Retrofit & OkHttp (Telegram Bot API) |
| **Security** | AndroidX Security-Crypto |

---

## 🚀 Setup

1. **Clone** the repository
2. **Firebase** — Create a Firebase project, enable Auth (Google provider) & Firestore. Download `google-services.json` into `app/`.
3. **Firestore Rules** — Deploy the provided `firestore.rules` to your Firebase console.
4. **Telegram Bot** — Create a bot via [@BotFather](https://t.me/BotFather), get the token and chat ID.
5. **Build** — `./gradlew assembleDebug`
6. **Configure** — Launch the app, sign in with Google, go to **Settings** and enter your Telegram Bot Token and Chat ID.

---

## 📱 Usage

1. **Create a Trip** — Tap "New Trip" and share the invite code with friends.
2. **Upload Photos** — Select photos, choose a tag, and they'll be uploaded to Telegram and visible to all trip members in real-time.
3. **Browse & Download** — Switch between Tag/Date/Uploader views. Download photos or full albums.
4. **Manage** — Admins can rename trips, remove members, and delete photos/albums.

---

## 🔒 Security

- **Scoped Access** — Firestore rules ensure users can only access trips they belong to.
- **Credential Isolation** — Telegram bot credentials are cached per-trip, so non-admin members never need direct access to the admin's profile.
- **Encrypted Storage** — Sensitive local data is secured via AndroidX Security-Crypto.

---

## 📄 License

This project is open source under the [MIT License](LICENSE).

---

*Made with ❤️ by the RoutePix Team.*

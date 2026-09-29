# BaltyGram — Android (Native)

A real native Android app (Kotlin + XML), not a WebView wrapper. Talks directly
to the same Supabase backend as the BaltyGram website over plain HTTPS REST
calls — no heavy networking libraries, so it's built to be as AIDE-friendly
as possible (no annotation processors, no Retrofit/Room/Dagger/Glide).

## What's included

- **Splash screen**: "BaltyGram" in mint, "By" (grey) "Xetsu" (blue) at the bottom — exact spec.
- **5-tab bottom navigation**: Home, Discover, Uploads, Downloads, Account
- **Real Supabase Auth**: sign up, log in, session stored locally
- **Profile**: avatar upload (camera or gallery), live username availability check,
  social links, Founder/Admin badges
- **Home**: trending + new releases, matches the web app's 6-approved-product threshold
- **Discover**: search, type filter chips, results grid
- **Upload wizard**: full 6-step flow (name → description → category/platform →
  icon → screenshots → file + submit), real file/image pickers via Android's
  document picker, real uploads to Supabase Storage
- **Software detail**: real download via a signed URL from the `sign-download`
  edge function, handed off to Android's own `DownloadManager` (shows in the
  system download notification, saves to the real Downloads folder) — not a
  fake progress bar
- **Reviews & favorites**: real read/write against the same tables the website uses
- **Notifications**: bell icon with unread badge, tap to open full list, marks read
- **No reCAPTCHA** — matches the current test-build state of the website

## Building this without AIDE

**Easiest path if you don't have a PC**: this project includes a GitHub
Actions workflow (`.github/workflows/build-apk.yml`) that builds the APK in
the cloud, for free, entirely from your phone's browser.

1. Create a GitHub account (free) if you don't have one.
2. Create a new repository and upload this whole project folder to it —
   GitHub's web interface lets you drag and drop files/folders directly,
   no git command line required.
3. Go to the **Actions** tab of your repo. The build should start automatically;
   if not, click **Run workflow**.
4. Once it finishes (a few minutes), open the run, scroll to **Artifacts**,
   and download `baltygram-debug-apk`.
5. Unzip it on your phone and tap the `.apk` file to install. Your phone will
   likely ask you to allow "install unknown apps" for whatever app you used
   to open it — that's normal for any APK not from the Play Store.

**If you ever have access to a PC/laptop**: open this folder in
[Android Studio](https://developer.android.com/studio) (free, official) and
it will build itself, or run `./gradlew assembleDebug` from a terminal if you
have JDK 17 and the Android SDK installed.

## Requirements before this builds in AIDE

Open this folder as a project in AIDE (or AIDE Pro). It needs internet access
on your device during the first build so Gradle can download dependencies
(AndroidX, Material Components) from Maven Central — this only needs to happen
once.

If AIDE asks for `local.properties` and doesn't generate one automatically,
rename `local.properties.example` to `local.properties` and point `sdk.dir`
at wherever AIDE keeps its Android SDK on your device.

## Permissions this app requests, and why

- **Internet / network state** — talking to Supabase
- **Notifications (Android 13+)** — for the in-app bell and download-complete alerts
- **Camera** (optional) — take a photo directly for your avatar or a software icon
- **Storage** (only on Android 9 and below — modern Android handles this automatically) — writing downloaded files

## Known limitations / what's NOT in this build

- **Push notifications**: the in-app bell works (reads the same `notifications`
  table as the site), but there's no Firebase Cloud Messaging wired in, so you
  won't get a phone notification while the app is closed. That needs an FCM
  project + a Supabase trigger to call it — a separate piece of work if you want it.
- **Developer profile screen**: not included yet in this build (the website has
  one at `/developer/:id`) — happy to add an Android equivalent next round.
- **This code has not been compiled** — it was hand-written and cross-checked
  (every resource ID, color, string, and drawable referenced in the Kotlin code
  was verified to exist in the XML resources, and all XML was checked for being
  well-formed), but I don't have an Android SDK/emulator in my own environment
  to actually run a build. If AIDE's compiler surfaces an error, send it to me
  exactly as shown and I'll fix it directly.

## Backend

Uses the same Supabase project as the website (`atovuyugeqrbfhkpyhax`), same
publishable key, same tables, same storage buckets, same edge functions
(`sign-download`). No backend changes were needed for this app to work.

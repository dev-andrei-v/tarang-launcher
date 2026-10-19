<div align="center">

# 🌊 Tarang

### A minimal, tvOS‑inspired launcher for Android TV & Google TV.

Full‑bleed artwork, liquid‑glass chrome, and a dock that gets out of your way — built D‑pad‑first for the living room.

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose%20for%20TV-4285F4?logo=jetpackcompose&logoColor=white)
![Platform](https://img.shields.io/badge/Android%20TV%20%C2%B7%20Google%20TV-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/min%20SDK-28-orange)

<img src="demo/ss/01-home.png" width="100%" alt="Tarang home screen — full-bleed Frame Art wallpaper and a frosted dock" />

</div>

---

## ✨ Highlights

- **A home that disappears.** At rest you see only the wallpaper and a frosted dock pinned to the bottom. Press **down** and your full app grid slides up — Apple‑TV style.
- **Frame Art.** Turn the TV into a framed painting — a folder slideshow or a single photo, full‑bleed with an elegant floating clock. Press **←/→** to flip through pictures; it can start itself after an idle timeout.
- **Living wallpapers.** Hover a favorite and its show/movie poster plays full‑screen as the background.
- **Choose your motion.** Pick how transitions feel — a calm **Default**, fluid **Glide** springs, or **Depth**, where the home recedes into a painting and dives into an app.
- **Real Liquid Glass.** The clock, status pills, and dock can refract the wallpaper through their edges via an AGSL shader (Android 13+) over a frosted blur — an optional toggle, off by default to stay light on weak TV GPUs.
- **Made for the remote.** Every interaction is D‑pad‑first — snappy focus, long‑press menus, no touch required.
- **Actually replaceable.** A guided **Home setup** flow gets Tarang running as your TV's home screen, even on Google TV where the system blocks setting a third‑party launcher.

---

## 📸 A look around

<table>
  <tr>
    <td width="50%"><img src="demo/ss/07-frame-art.png" alt="Frame Art" /><br/><sub><b>Frame Art</b> — the TV as a framed painting, with a floating clock. Press ←/→ to flip photos.</sub></td>
    <td width="50%"><img src="demo/ss/03-grid.png" alt="App grid" /><br/><sub><b>App grid</b> — press down to reveal every installed app.</sub></td>
  </tr>
  <tr>
    <td width="50%"><img src="demo/ss/05-settings.png" alt="Settings — Appearance" /><br/><sub><b>Settings</b> — animation style, theme, wallpapers, grid density.</sub></td>
    <td width="50%"><img src="demo/ss/02b-app-artwork.png" alt="App artwork wallpaper" /><br/><sub><b>App artwork</b> — the focused favorite's poster becomes the wallpaper.</sub></td>
  </tr>
  <tr>
    <td width="50%"><img src="demo/ss/04-context-menu.png" alt="Long-press menu" /><br/><sub><b>Long‑press menu</b> — favorite, hide, app info, uninstall.</sub></td>
    <td width="50%"><img src="demo/ss/06-home-setup.png" alt="Settings — Home setup" /><br/><sub><b>Home setup</b> — live checks + deep‑links to become the default Home.</sub></td>
  </tr>
</table>

---

## 🎛 Features

**Home & navigation**
- Frosted **dock** of favorites — pin, reorder (move mode), and remove your most‑used apps.
- **App grid** of every launchable app (TV apps *and* sideloaded phone apps), sorted alphabetically and refreshed live as you install/uninstall.
- Dock anchored to the bottom with the grid tucked below the fold, so the home screen stays calm.

**Look & feel**
- **Wallpapers** — gradient presets, your own photo, app artwork, or your Frame Art; rendered sharp and static to stay easy on weak TV GPUs.
- **Daily picture** — a wallpaper from the Bing daily image archive. Tarang keeps the last eight pictures on the TV, rotates them on a schedule you pick, and shows the cached pictures when the TV is offline.
- **App‑artwork wallpaper** — plays a favorite's TV poster art full‑screen while it's focused (reads the system TV artwork).
- **Animation styles** — pick how the big transitions feel: **Default** (chrome scales up and flies apart), **Glide** (fluid springs, no blur), or **Depth** (the home recedes into a painting and dives into an app).
- **Liquid Glass** — optional AGSL refraction + frosted blur on the chrome (Android 13+, off by default to save GPU on slower TVs), with a flat‑tint fallback everywhere else.
- **Theme** — Light, Dark, or Automatic (follows time of day).
- **Adjustable density** — 3 to 7 columns.

**Frame Art**
- Turn the TV into a **framed picture** — a folder slideshow, a single photo, or your current wallpaper, shown full‑bleed with no chrome.
- **←/→ to page** through a folder's photos, with slow cross‑fades and an optional "living‑painting" drift.
- An elegant **floating clock** (optional) with configurable position, size, and date.
- **Auto‑start** after a configurable idle time, so it doubles as a screensaver; press any key to come back.

**Daily driver**
- **App management** from a long‑press: add/remove favorite, **Hide app**, **App info**, **Uninstall**.
- **Hidden apps** — tuck apps out of the grid and bring them back from Settings.
- **Reduce motion** — calms Frame Art drift and slideshow, tile‑focus springs, and the app‑launch animation.
- **Home setup** — detects whether Tarang is your default Home and whether the redirect service is on, and deep‑links you to fix each. On Google TV (which won't let an app set itself as Home) an accessibility service quietly returns you to Tarang whenever the stock launcher surfaces.
- **Status bar** — clock & date, Wi‑Fi indicator, and quick shortcuts into Wi‑Fi / Android settings.

---

## 🛠 Built with

- **Kotlin** + **Jetpack Compose for TV** (`androidx.tv:tv-material3`)
- **MVVM** with `StateFlow` and manual DI
- **DataStore (Preferences)** for settings
- **AGSL `RuntimeShader`** for the glass refraction
- Min SDK **28** (Android 9) · Target/Compile SDK **35** · built from the **Gradle CLI** (no Android Studio required)

---

## 🚀 Build & install

```bash
# Build a release APK
./gradlew :app:assembleRelease

# Install to a connected device (or wireless ADB target)
adb install -r app/build/outputs/apk/release/app-release.apk
```

> **Tip:** debug builds run fine on an emulator but are noticeably slow on real TV hardware — use the **release** build on a device.

Then, on the device, open **Settings → Home setup** and follow the steps to make Tarang your home screen.

### Android 10 and 1 GB TVs

Tarang uses transparent panels on Android 10. It does not load `RuntimeShader` or capture a blur backdrop.
TVs with at most 1.5 GiB RAM, or the Android low-RAM flag, use these limits automatically:

- Keep the dock, app grid, focus effects, and short transitions.
- Disable blur, refraction, continuous image drift, and the grid fade buffer.
- Limit the artwork cache to 8 MiB and the tile cache to 4 MiB.
- Decode wallpapers and artwork within 1280 × 720 pixels.
- Load one full-size artwork image at a time. Stream downloaded images through a temporary file.
- Prefetch at most 12 app tiles. Load other tiles when needed.
- Release cached images when Android reports memory pressure.

Release builds use R8 and resource shrinking. Build with JDK 17 or 21 and Android SDK 35.
Use the existing baseline profile. Do not generate profiles on a personal TV because that clears app data.

After a sideload, apply the profile and compile the app:

```bash
adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE \
  -n com.tarang.launcher/androidx.profileinstaller.ProfileInstallReceiver
adb shell cmd package compile -m speed -f com.tarang.launcher
adb shell am force-stop com.tarang.launcher
adb shell am start -n com.tarang.launcher/.home.HomeActivity
```

Android 10 and earlier block notification listeners in system low-memory mode.
Tarang shows Now Playing as unavailable on those devices and does not poll media sessions.

### Connections and app tiles

Open **Settings → Wi-Fi & Bluetooth** to change network or accessory settings.
The passive pill beside the clock shows the Wi-Fi network name and connected Bluetooth device names.
The Wi-Fi and Bluetooth buttons in the right pill open the system settings directly.
Status listeners stop when Tarang is hidden. They do not scan for networks or devices.
Select **Show Wi-Fi name** in Tarang settings to grant the required Android location permission.
Location must also be enabled in Android. Tarang does not request location coordinates.
Android 12 and later require Bluetooth access to show its status. Grant access from the Connections page.

SmartTube Stable and Beta use the YouTube name and play icon inside Tarang.
Their package names and launch targets stay unchanged. App info and uninstall still act on SmartTube.

The local release uses the debug signing key when `keystore.properties` is absent.
Updates must use the same key. See [the signing guide](README-signing.md).


---

<div align="center">
<sub><i>Tarang</i> (तरंग) — "wave." Built for the couch. 🛋️</sub>
</div>

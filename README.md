# Pulsify v3 — milestone 1

Native Android live wallpaper (Kotlin + Jetpack Compose + OpenGL ES 2). No WebView.

## What's in this milestone
- **Wallpaper engine** (`engine/GlLoop.kt`): its own EGL thread paced by Choreographer.
  60 fps while touched / animating / music reacting, 30 fps idle, 15 fps at night or in battery saver,
  0 fps when hidden (frame callback removed, Visualizer released).
  Dynamic resolution: the surface buffer shrinks (0.6×–1.0×) when frame work nears the budget; the compositor upscales.
- **Night Sky world** (`assets/shaders/sky.frag`, `engine/SkyScene.kt`): three variants (Night Sky, Aurora, Nebula),
  parallax with home-screen pages, ripples on tap, shooting stars you can catch, a wish on long-press,
  a meteor shower on a very fast swipe, a green horizon while charging, dimming at night and on low battery,
  a welcome star on unlock. An AMOLED black floor keeps idle pixels fully off.
- **Audio** (`core/AudioEngine.kt`): `Visualizer(0)` FFT → bass / mid / treble, 32-bin spectrum, beat detection
  (0.24 s refractory), BPM from the median beat interval. Opt-in. Without permission, a gentle groove when
  `isMusicActive()`. A Demo groove for testing.
- **Settings sync**: SharedPreferences + a change listener; the running wallpaper updates instantly.
- **App UI**: Home (live hero running the real engine, bond level, discoveries, gallery with filters),
  Studio (full-screen preview, Look / Tricks / Mood / Music dock, drag handle, Hide / Show controls,
  Set as live wallpaper), Settings, theme engine driven by one accent colour, daily spark + streak.

## Build
Push to `v3` (or `main`). GitHub Actions builds `pulsify-v3.apk` and publishes it as a pre-release.
Locally: `gradle :app:assembleDebug` with JDK 17 and the Android SDK.

## Next milestone
Mochi — needs the reference images.

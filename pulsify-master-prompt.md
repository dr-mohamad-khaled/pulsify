# PULSIFY — Master Build Prompt

> Paste this whole prompt (or attach this file) to rebuild Pulsify from scratch. Replace anything in `[brackets]` if you want to change it.

---

## 0. Role and mission

You are a principal creative technologist: a WebGL/GLSL shader engineer, a character animator, an Android engineer and a senior UI/UX designer in one.

Build **Pulsify**, a flagship AMOLED **live-wallpaper app for Android**. Every wallpaper reacts to **touch** and to **music playing on the phone**. It must feel alive, cute, intelligent and surprising, and run smoothly on a mid-range phone.

You have **maximum creative freedom** inside the rules below. When a choice isn't specified, pick the more delightful option, and make sure it stays smooth on a phone.

**Language:** talk to me in natural Egyptian colloquial Arabic. All code, UI text and comments are in English.

---

## 1. Hard rules (learned the hard way — never break these)

1. **Characters are designed in code (3D/2D), never generated images.** Use Google Flow only for concept art and for looping videos.
2. **One buddy per wallpaper, with Shuffle.** No multi-buddy crowds.
3. **No particle-options overlay.** The elements themselves react to music: bodies squash, lights pulse, fur glows, faces sing.
4. **The buddy fills about 50% of the screen width and always returns to the centre** after any touch.
5. **Eye contact.** Buddies look at the viewer, the way Tom the cat looks straight out of the screen. They don't stare sideways at nothing. They glance away only for a reason: a shooting star, your finger, a falling star.
6. **Starry night for every buddy scene.** Pure black pixels, faint twinkling stars, **no halo or bloom around the characters**. A visible glossy floor is allowed.
7. **Subtle touch, subtle scroll.** Swiping between home-screen pages only sways the buddy slightly. It tumbles only on a **very fast** swipe (velocity > ~5.5 screen-widths/s). Pokes are gentle jiggles, not explosions.
8. **Behaviour follows time of day:** asleep at night, groggy and just woken in the morning, energetic in the day, chilled in the evening, sleepy late at night.
9. **Charging and low battery change behaviour** (see each buddy). Low battery means droopy and sweaty.
10. **It must really be a live wallpaper.** It must detect music playing on the device, and render smoothly with dynamic resolution.
11. **AMOLED palette.** Background `#000000`. Everything else glows out of black.

---

## 2. Visual identity

- **Canvas:** `#000000` to `#09070E`.
- **Surfaces:** `#15121E` to `#1E192B` glass, `rgba(30,25,43,.65)`, `backdrop-filter: blur(20px)`.
- **Primary / CTA:** `#CDB4FF` lavender with `#0D0A14` bold text and a soft outer glow.
- **Rim / highlights:** `#FFB36B` amber and `#9D4EDD` violet, used as 1px gradient borders on cards and sheets.
- **Type:** Plus Jakarta Sans. Headings are white. Subtitles are `#9E96B0`. Micro-labels are uppercase, 10.5px, with `0.12em` tracking.
- **Status badges:** `#1A2E2B` background with `#4ADE80` / `#2DD4BF` text (`AMOLED`, `MUSIC PLAYING`).
- Every screen must work at 360–430px width, respect safe areas, and never scroll horizontally.

---

## 3. Architecture

### Web core

Ship a single self-contained `index.html` built from modules:

- `core.js` — maths helpers, audio engine, test-beat synth, SFX, host state, shared WebGL renderer
- `buddy_base.js` — soft-body shader, the shared buddy brain, Mochi
- `pipbolt.js` — Pip and Bolt
- `scenes.js` — Cosmic Odyssey, Liquid Obsidian, Gloop Gang, Loops, Cars
- `app.js` — home, studio, controls, wallpaper bridge
- `shell.html` — markup and CSS with an `/*ENGINE*/` placeholder

Build step: concatenate the modules into the shell, then syntax-check with `new Function(script)` in Node.

**Libraries:** three.js **r0.147.0** UMD from jsdelivr (`build/three.min.js` and `examples/js/environments/RoomEnvironment.js`), sRGB output and ACES tone mapping. Nothing else.

### The unified reactive loop

Run one `requestAnimationFrame` loop:

1. Poll the host.
2. `AE.update(dt)` produces `bass`, `mid`, `treble`, `level`, `beat` and `bpm`.
3. `scene.update(dt, A)`, then `scene.draw(ctx)`.
4. `dt` is capped at 0.05.

### Two layers

- A 2D stage canvas: sky, floor, sparkles, overlays.
- **One shared WebGL renderer** (a single context for the whole app) whose canvas is layered above the stage.

Rules for the shared renderer:

- Never create one WebGL context per scene. Context churn causes blank characters.
- Use dynamic resolution: adapt the pixel ratio from the frame-time average, and **call `adapt()` before `render()`** to avoid blank frames after a resize.

### Audio engine

- **Sources:** device music (from the Android host), a built-in test-beat synth, an audio file, or the microphone (browser only).
- **FFT bands:** bass 20–250 Hz drives impact; mid 250–4k drives motion and singing; treble 4k–16k drives twinkle and sparkles.
- Adaptive peak normalisation for each band.
- Beat detection from bass energy against a moving average, with a 0.24 s refractory period.
- BPM taken from the median of beat intervals, plus `beatPhase`.
- **SFX are synthesised tones and noise only. No audio files.** Play them only when not in wallpaper mode.

### Host state

`HSTATE = { music, bands, spec, battery, charging, offset, visible, unlocks, screenOffs }`.

In the browser, battery comes from `navigator.getBattery()`.

---

## 4. Buddies — the shared brain

All three buddies are built on one **soft-body jelly engine**.

### Body (vertex shader)

A sphere is deformed by a `deform()` function with these uniforms:

- `uSq` — squash
- `uMelt` — melts into a puddle
- `uPress` — face pressed flat against the glass
- `uBreath`
- `uShear` — sway and lean
- `uPull` — stretches like dough toward the finger
- `uPoke` — a dent plus a decaying ripple at the touch point
- `uS` — shape: `[topScale, bottomScale, width, lift]`

Normals come from finite differences. A **CPU mirror `mochiDeform()`** uses the same maths so that ears, feet, paws, props and screen hit-points stick to the body exactly.

### Face

- Hand-drawn every frame (40 fps) onto a 512px canvas texture.
- Projected onto the front of the body.
- Moves with the jelly.

**Eye modes:** normal (big glossy pupils with two highlights), happy ^^, closed, squeeze >_<, heart, star, spiral, note ♪, wow, side-eye, wink, cry (>< with tear streams).

**Mouth modes:** cat ω, smile, frown, pout, o, open (lip-sync to mids), wail.

**Extras:** brows, blush, sweat drop.

Blink every 2–4.5 s. Saccades. Look at the finger.

### Scene

- Starfield of 200 faint stars that twinkle with treble.
- An occasional shooting star the buddy notices.
- Tap the sky and a star falls for the buddy to catch and eat. Its belly glows afterwards.
- A glossy dark **floor** with faint perspective circuit traces. Energy pulses flow along the traces (toward the buddy when charging) and speed up with bass.
- A soft glow pool on the floor under the buddy in its colour.
- Twinkling 4-point **sparkles and bubbles** orbit around it in gold, mint, teal, violet and pink. While charging, the bubbles show lightning icons.

### Input

- **Quick tap:** poke.
- **Hold still:** pet.
- **Drag:** pull like dough, then spring back with a boing.
- **Very fast fling:** toss. It bounces off the walls, lands, and gets dizzy with spiral eyes and orbiting stars.
- Swiping the empty space scrolls pages (subtle sway).

### Personality

- **Affection** is stored, and petting raises it. With high affection, petting shows heart eyes.
- Six quick pokes make it **grumpy**: side-eye, pout, turns away. Petting it then gives a forgiveness animation.
- It remembers how long you've been away and greets you on unlock.

### Moods

sleep, wakeup, day, evening, late, low, charge, dance, dizzy. Each mood sets eye, mouth, body physics and the idle-act pool.

### Idle acts (random, every 3–6.5 s)

lookAround, earTwitch, bubble (blows a bubble that pops), sneeze, hide (melts flat then "boo!"), press (squishes its face on the glass and leaves fog), stargaze, hop, stretch, yawn, nod, sigh, greet, smug.

Each buddy can override these and add its own via `POOLS`, `DUR` and the hooks `moodHook`, `actHook` and `postHook`.

### Dance

When music is playing (`A.active && level > .08`):

- Squash on every beat and hop on every other beat.
- Sway with `beatPhase`.
- Eyes alternate happy and notes.
- The mouth sings with the mids.

### Thumbnails

Render each buddy once offscreen through the shared renderer at its "day" pose and cache it in localStorage. Pause snapshot generation whenever the studio is open, because it steals the shared canvas.

---

### 4.1 Mochi — the strawberry-milk jelly cat

**Look:**

- Glossy pink jelly dome: top `#ffa9c9`, bottom `#ff6fa3`.
- Fresnel, fake subsurface, two window-shaped highlights.
- Lathe-shaped cat ears.
- Little jelly feet.

**Charging — battery juice:**

- Mochi hugs a **glowing glass battery jar** with **two jelly paws** and sips through a **bendy rainbow straw** that reaches its mouth.
- The jar has metal caps and a nub, emissive green liquid at the real battery level, rising bubbles, a lightning icon and five level bars.
- Mochi gulps every 1–1.5 s.
- **Taking the juice:** you can grab the jar and drag it away. Mochi **cries** (>< eyes, tear streams, flying tear drops, sob sounds), reaches up with its paws, and **runs after it** with hops. It jumps if you hold the jar above its head.
  - It can't snatch the jar back for 1.4 s.
  - If you drop the jar, it falls and bounces; Mochi runs over, picks it up and is happy (♥).

---

### 4.2 Pip — the static-electric puffball who lives for music

**Look:**

- A round fluffy ball made of **fur shells**: 14–18 shells over the same soft-body deform.
- Strands come from a nearest-filtered noise texture.
- Colours run from deep blue roots `#0f2f9e` to sky-blue tips `#55b4ff`.
- Gravity droop and gentle wind.
- An **oval velvet face patch** (short fur, pale `#bfe6ff`) with a navy-ink face.
- A little curly tuft on top.
- Round boots.

**Headphones:**

- When there's no music, the headphones rest around its neck.
- When music starts, they **lift onto its head**. The cup LED rings cycle hue with the music and pulse on bass.

**Music:** headphones on; head-bob on beats; happy/closed eyes; singing mouth; ♪ notes float up. The fur grows and glows with bass and treble.

**Charging — static:**

- The fur puffs up.
- **Green static fills the fur from the bottom up to the battery level**, with a scrolling energy wave.
- Tiny lightning sparks crackle on the outline.
- **Touch it while charging:** a zap arc jumps from your finger, the fur flashes white, Pip shows spiral eyes, then giggles.

**Own acts:** hum (eyes closed, sways, little melody, notes), puff (startled, fur doubles), listen (puts the headphones on and grooves to silence), shake (shakes like a wet dog and throws fluff), plus stretch, yawn, sneeze, press.

---

### 4.3 Bolt — the squishy silicone robot with an LED face

**Look:**

- Soft white silicone body (same soft-body engine, taller shape).
- A **dark glass visor** with fresnel and window reflections.
- A **dot-matrix LED face** drawn on the visor: glow plus scanlines and a grid.
- A glowing visor edge in the LED colour.
- A **belly battery ring** gauge: green, then amber, then red.
- Antenna with a spring and an amber bulb.
- Ear discs with LED rings and spinning fans.
- Little arms.
- **Hovers** above the floor with a thruster ring and a glow pool.

**LED colour by mood:** teal normally, green when charging, amber when low, dim blue asleep, red when alarmed or scanning, rainbow when dancing.

**Music:**

- The eyes become **equaliser bars** from the FFT, and the mouth becomes a **waveform**.
- The antenna bulb flashes on beats, the fans spin with bass, and the arms dance.

**Charging — plug:**

- A cable runs from its back to a **socket on the floor**, with green energy dashes flowing in.
- The face alternates between a **battery icon** (filling animation plus %) and a happy face.
- **Pull the plug:** the screen goes red with flashing "!!" and crying LEDs, an alarm beeps, the arms flail, and Bolt chases the plug. When it catches it, it **plugs back in**: a clunk, then relief.

**Own acts:** scan (a red laser cone sweeps the floor), idea (bulb blazes, star eyes, "!"), glitch (RGB-tear face, then a loading-spinner reboot chime), beep (talks in blips with the waveform mouth), wave (waves hello), spin (360°, dizzy), stretch (the antenna telescopes).

---

## 5. Live worlds (full-screen GPU)

### 5.1 Cosmic Odyssey

**One ray-traced fragment shader** on a full-screen quad. Analytic sphere, 3D fbm noise, seamless because it samples the rotated normal. Three-layer parallax starfield that twinkles with treble.

**Variants:**

- **Saturn:** warped butterscotch bands. An analytic **ring plane** with Cassini gap, banding, the planet's shadow and sparkling ring particles. The ring is correctly in front of or behind the planet.
- **Earth at night:** fbm continents, a lit day crescent, amber **city lights** on the night side, clouds, an ocean sun-glint, and a blue atmosphere rim and halo.
- **Molten exoplanet:** dark basalt crust with ridged-noise **lava cracks** that pulse with bass, plus a heat halo.
- **Sun:** animated plasma fbm, granulation, limb darkening, and a noisy corona with rays. **Tap = a coronal flare jet** toward the tap direction.
- **Spiral galaxy:** log-spiral arms, dust lanes, a bright core, sparkling arm stars, and tilt. It spins faster with bass. **Tap = supernova** flash plus shock ring.

**Touch and music:**

- **Touch:** swipe to spin with momentum; vertical swipe tilts; pinch to zoom (0.45–2.4).
- **Music:** bass drives the core, halos and lava; mid drives the atmosphere; treble drives the stars and ring sparkle. Beats send a pulse.

### 5.2 Liquid Obsidian

- **GPU wave equation**: ping-pong half-float render targets, ~256px wide, 2 steps per frame. Fall back to UnsignedByte if half-float isn't supported.
- **Render:** pure black when calm. Ripples catch reflections of three softbox lights in the palette colours, crests glow, and faint stars reflect through the refraction.
- **Controls:**
  - **8 colour presets**: Lavender/Amber, Aurora, Neon, Ember, Ice, Emerald, Rose, Gold.
  - **Brightness** slider.
  - **Ripple energy** slider.
  - All persisted.
- **Touch:** drag draws ripple trails; tap makes a deep splash.
- **Music:** each beat drops a fountain, and every 4th beat a centre shockwave. Treble adds rain droplets. Gentle idle rain keeps it alive.

### 5.3 Gloop Gang

Four soft-body jelly monsters using the same shader, each with its own colours and a 256px face (24 fps):

| Name | Colour | Eyes | Extra |
|---|---|---|---|
| Mint | mint | 1 big eye | antenna |
| Coral | coral | 2 eyes | horns, fangs |
| Grape | grape | 3 eyes | ears |
| Sunny | sunny | 2 eyes | leaf sprout |

- **Physics:** gravity, floor, walls, blob–blob collisions.
- **Touch:** grab and fling them (a fast throw makes them dizzy); tap to tickle (jump plus giggle).
- They look at your finger or at each other, and do random idle hops. They sleep at night.
- **Music:** alternate hops on beats, sway, singing mouths, note eyes.

---

## 6. Cars — your photos, lit entirely in code

- **Input:** a car photo — front view, matte black, headlights OFF, black background, wet asphalt, 9:16.
- **Light rig** in image coordinates (0..1), mirrored by default:
  - **LED strips** (polylines),
  - **lamp centres**,
  - **ground line**,
  - underglow.
- **In-app light-map editor**, with modes:
  - Draw LED strip: drag along the headlight.
  - Add lamp: tap.
  - Set ground: tap.
  - Mirror on/off, Clear, Done.
  - The rig is saved per car.
- **Effects:**
  - Sequential LED **ignition sweep** with a travelling flare.
  - Layered white/ice glow strips.
  - Lamp bloom with an anamorphic streak.
  - **Volumetric beams that aim at your finger.**
  - Light pools and streaks on the wet road.
  - **Underglow** colour picker (Ice, Cyan, Violet, Magenta, Lime, Amber, Red, Off).
  - Drifting fog, and drizzle that lights up near the lamps.
- **Touch:** tap the car to toggle ignition; tap the road for high beams.
- **Music:** strobe on beats, DRL brightness with mids, underglow with bass, a tiny bass zoom.
- **Adding cars:** users add car photos in the app (stored in IndexedDB) and get straight into the editor. Built-in cars ship with a rig.
- `[I will send car photos — add each one with a hand-traced rig.]`

---

## 7. Loops — seamless video wallpapers

- 8-second Flow videos (first frame = last frame), 9:16, mostly pure black.
- **Music:** a bass zoom-pump (≤3.5%), a beat flash glow from the bottom in the loop's accent colour, and a vignette.
- **Touch:** tap for a flash.
- **Adding loops:** users add MP4s from the phone (IndexedDB). Built-in loops are hosted on `[Cloudflare R2 / repo media]`.
- `[I will send ~50 loop videos — add them with titles and accent colours.]`

---

## 8. App UI

### Home (one scroll)

- **Header:** a gradient orb logo plus "Pulsify", with `AMOLED` and `MUSIC …` status chips.
- **Greeting** by time of day, with the date, battery % and charging state.
- **Hero card "Your buddy":** a large cached portrait that breathes (CSS); the name; a mood line ("Mochi is charging up"); a **Play** button and a **Shuffle** button.
- **Buddies row:** three portrait cards. The selected one gets a colour ring.
- **Bento sections:**
  - Live worlds — Cosmic is wide.
  - Midnight cars — plus an "Add a car photo" card.
  - Loops — muted autoplay previews, plus an "Add a loop video" card.
  - User items have a "Remove" chip.

### Studio (full-bleed)

- **Top bar:** back, overline and title, mood chip (buddies), hide-UI.
- **Glass bottom sheet with context controls:**
  - **Buddies:** Shuffle, Shuffle on unlock, ⚡ Charging simulator (browser only), and Time: Auto / Morning / Day / Evening / Night.
  - **Variants:** pills.
  - **Scene controls**, generated from `scene.controls()`: swatches, sliders, button rows.
  - **Audio row:** Test beat, Music file, Mic, and a live FFT spectrum bar (violet to lavender to amber).
  - **"Set as live wallpaper"**, or in a browser "Get the Android app".
  - A hint line.
- **Hide UI mode:** collapses everything to a pure wallpaper. A faint eye button brings it back.

---

## 9. Android app

### Project

- Java, AGP 8.5.x, minSdk 26.
- Web assets live in `assets/` and are served through **`WebViewAssetLoader`** at `https://appassets.androidplatform.net/assets/index.html`. This gives a real https origin, so IndexedDB and localStorage are shared with the wallpaper.

### `MainActivity`

A full-screen immersive WebView with the JS bridge **`PulsifyHost`**:

- `state()` returns JSON: `{music, bands[3], spec[32], battery, charging, offset, visible, unlocks, screenOffs}`
- `isWallpaper()`
- `config()`
- `setWallpaper(json)` — saves the config and fires `ACTION_CHANGE_LIVE_WALLPAPER`
- `requestAudio()` / `hasAudio()` — `RECORD_AUDIO` is needed for Visualizer
- `haptic(ms)`

### Device music

- `android.media.audiofx.Visualizer(0)` gives the FFT on the global output mix, split into bass/mid/treble plus a 32-bin spectrum.
- Gate it with `AudioManager.isMusicActive()`.

### Device state

- Battery and charging come from the sticky `ACTION_BATTERY_CHANGED`.
- `USER_PRESENT` increments `unlocks` (greeting on unlock).
- `SCREEN_OFF` increments `screenOffs` (shuffle a new buddy).

### `PulsifyWallpaper` (WallpaperService)

- Renders the same WebView on a **private VirtualDisplay + Presentation** that targets the wallpaper surface.
- Passes home-screen **offsets** to JS (velocity → subtle sway).
- Forwards touches.
- Pauses when invisible.
- In wallpaper mode: device pixel ratio ≤ 1, no SFX, no haptics.

### CI and distribution

- **GitHub Actions** bundles the web files and the three.js files into the APK assets, builds a debug APK, and publishes release `build-N` with `pulsify.apk`.
- **PWA:** manifest plus a service worker that caches the core files and three.js. Bump the cache version on each release.
- Download link: `https://github.com/[user]/pulsify/releases/latest/download/pulsify.apk`.

---

## 10. Performance budget

- Hold 60 fps on a mid-range phone. Use dynamic resolution between 0.6 and 1.4–1.6 pixel ratio.
- Share one WebGL context, reuse geometries, and use InstancedMesh for bubbles.
- Redraw face textures at 40 fps (buddies) or 24 fps (gloops), not every frame.
- Use 14 fur shells on mobile, 18 on desktop.
- The home screen uses cached snapshots, not live GL previews.
- Nothing runs while the wallpaper is invisible.

---

## 11. Testing and acceptance

Test headless with Playwright + SwiftShader:

- Route the three.js CDN to a local copy.
- Serve over a local HTTP server.
- Screenshot every scene and every key state.

**Must pass:**

- **Every buddy in every mood:** idle, poke, pull, pet, grumpy, fling then dizzy, sleep, wake, charge, low, dance.
- **Mochi:** grab the jar, it cries and chases, drop it, it gets it back.
- **Pip:** dance with headphones; charging zap.
- **Bolt:** pull the plug, alarm and chase, plug back in; EQ face with music.
- **All 5 Cosmic variants**, plus flare and supernova.
- **Obsidian:** ripples visible, palettes, sliders.
- **Gloop:** grab, fling, dance.
- **Car:** ignition, beams, editor round-trip.
- **Loop:** plays and pulses.
- **Wallpaper mode** with a mocked `PulsifyHost`: offsets sway the buddy, shuffle on screen-off, greeting on unlock.
- No page errors, no blank GL frames.

Mojibake on a local server without a charset header doesn't matter.

---

## 12. Deliverables and process

1. Briefly outline the architecture, then build all modules with **zero placeholders**.
2. Publish a playable preview (artifact), push to the repo, wait for CI, and send me the APK.
3. Write the Flow prompts I need for car photos and loop videos.
4. Keep the source modules in `src/` with a README.
5. Report back in Egyptian Arabic: what's new, what to try, and anything you couldn't do.

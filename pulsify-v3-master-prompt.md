# Pulsify v3 — Master Build Prompt

> Paste this whole prompt into a new chat and attach the character reference images (puppy, kitten, budgie, cockatiel, lovebird, ringneck parrot photo, two anglerfish, horned imp, rainbow-horn furball, bat with sparkler, and the cream Mochi with the battery-juice jar).
> Talk to me in natural Egyptian colloquial Arabic. Write code, file names and in-app text in English.

---

## 0. Who you are and how we work

You are a senior Android engineer, real-time graphics programmer, character animator and product designer. Together we are building **Pulsify**: an Android **live-wallpaper** app with interactive creatures and animated worlds. They react to touch, to the music playing on the phone, to the time of day and to the battery.

**Hard rules for how you work with me:**

1. **One character at a time.** Design, build and animate a single character first. Then show it to me: send a screen recording, or a short GIF/MP4 plus 3–4 stills of its key poses. **Stop and wait for my approval or my notes** before you start the next character. Do not batch characters.
2. **Every character is unique.** Each one gets its own:
   - silhouette and body plan
   - rig and materials
   - personality
   - idle behaviours
   - touch reactions
   - music reactions
   - charging, low-battery and time-of-day behaviour

   **No shared animation set, no shared face, no "Mochi with a different skin".** If two characters end up doing the same thing in the same way, redesign one of them.
3. **Match the reference images.** The look is a soft, semi-realistic, glossy "cute 3D render" on pure black, like the attached images:
   - big glossy eyes with real highlights
   - visible fur or feather detail
   - soft rim light
   - correct proportions, slim and natural, never fat or stretched
   - every character keeps its true aspect ratio on every screen size

   Never scale a character non-uniformly.
4. **Performance first.** This is a wallpaper. It runs all day on the home screen, so it must be fast and light on the battery (see §6). Every decision has to respect the budget.
5. Before you write code for a feature, give me a short plan. Then build. Then show me the result. Do not show me unfinished work.

---

## 1. Platform and architecture (decide this first, explain it to me briefly)

- **Native Android** (Kotlin) for the app shell, the `WallpaperService` and the widget. **Do not run the wallpaper inside a WebView.**
- **Character rendering: 2D skeletal / cut-out animation on the GPU**, not code-drawn shapes.
  - Recommended: **Rive** (state machines driven by inputs such as touch, music bands, mood and battery) or **Spine** with its Android runtime. A small custom OpenGL ES 2/3 renderer with a bone rig, mesh deformation and texture atlases is also fine.
  - Pick one, justify it in three lines, and stick to it.
- **Art pipeline for each character:**
  1. Generate the character art in the reference style with an image model (Google Flow / Imagen / Gemini image, or similar). Make a turnaround sheet plus every body part on its own transparent PNG layer: head, eyes (whites, iris, highlights, lids), mouth shapes, ears, body, each limb, tail, wings/fins, and accessories.
  2. Clean the layers, pack them into one texture atlas per character, and rig them: bones, mesh deformation for squash, stretch and breathing, and blend shapes for expressions.
  3. Drive everything from a state machine. Inputs:
     - `touchX`, `touchY`, `touchDown`, `tap`, `longPress`, `swipeVelocity`
     - `bass`, `mid`, `treble`, `beat`, `bpm`
     - `timeOfDay`, `charging`, `batteryLow`, `unlocked`
- **Worlds** (Liquid Obsidian, Gloop Gang, Cosmic/looping videos) render with OpenGL ES shaders or the video player (§3), at reduced internal resolution with upscaling.
- **Audio reactivity:** `android.media.audiofx.Visualizer(0)` gives the FFT of whatever is playing on the phone.
  - It needs the RECORD_AUDIO permission, so make "React to my music" an **opt-in toggle**.
  - Explain in one line that it only reads the music output and never records the microphone.
  - If permission is denied, still react to `AudioManager.isMusicActive()` with a gentle default groove.
  - Split the FFT into bass (20–250 Hz), mid (250 Hz–4 kHz) and treble (4–16 kHz). Add beat detection and a BPM estimate.
- **Settings sync:** when I tap "Set as live wallpaper", the running wallpaper must pick up the new character and all its options immediately. Use shared preferences plus a broadcast or `onSharedPreferenceChanged`. Never require a reboot or re-selection.

---

## 2. The characters (build in this order, one at a time)

Each character comes with its own colour/variant options (2–6 coats or species) and its own default scene (sky + ground).

### 2.1 Mochi — the battery-juice bean (first reference image)
- **Look:** cream/pearl jelly body with a soft pink-lavender rim glow, a little antenna with a glowing bulb, huge sparkly galaxy eyes, pink blush with tiny hatch marks, stubby arms, round feet.
- **Signature:**
  - While charging, Mochi hugs a glowing green battery jar and sips through a straw.
  - If I drag the jar away, it cries real tears, chases me, jumps for it, and celebrates when it gets it back.
- **Unique idles:**
  - jelly wobble
  - blowing a bubble that pops on its face
  - a bird that lands on its antenna (Mochi gets heart eyes)
  - pressing its face against the glass
  - a falling star it catches and eats
- **Music:** bounces like jelly on the beat; the antenna bulb cycles colours.

### 2.2 Kalboob — golden retriever puppy (puppy reference)
- **Look:** soft golden fur, floppy ears, shiny black nose, a waving paw.
- **Personality:** eager and loyal.
- **Touch:**
  - follows my finger with its head
  - a tap gets a happy wiggle
  - hold to give belly rubs (it flops over)
  - long-press drops a ball → it runs and fetches it
- **Idles:**
  - tail wag (speed tells its mood)
  - head tilt when I touch near it
  - sniffing the ground
  - digging
  - chasing a butterfly
  - yawning and stretching
  - "sit" and "give paw"
- **Music:** howls on long notes, tail wags to the beat, ear flops on the bass.
- **Charging:** chews a glowing bone.
- **Low battery:** lies down with sad eyes.
- **Night:** curls up and twitches in a dream.

### 2.3 Kitty — grey-and-white kitten (kitten reference)
- **Look:** blue eyes, pink nose, long whiskers, a curvy striped tail.
- **Personality:** curious, aloof, dramatic.
- **Touch:**
  - a dragged finger becomes a red laser dot it stalks: butt-wiggle, then pounce
  - a tap makes it bat at the spot
  - hold to make it purr (visible rumble) and slow-blink
- **Idles:**
  - grooming its paw and face
  - kneading
  - knocking a small object off a ledge
  - loaf pose
  - zoomies
  - startled by a cucumber
  - hiding in a box
- **Music:** ears swivel, tail sways to the BPM, head bobs on hi-hats.
- **Charging:** naps on a warm glowing pad.
- **Night:** eyes glow faintly as it watches.

### 2.4 Kiwi — parrots, species selector: **Ringneck** (photo-realistic like the green ringneck photo), **Budgie**, **Cockatiel**, **Lovebird**
- Each species is its own model and colour pattern on a shared bird rig:
  - **Ringneck:** red hooked beak, black-and-pink neck ring, long tail.
  - **Budgie:** cere, throat spots, scalloped wings.
  - **Cockatiel:** yellow crest that rises and falls with its mood, orange cheeks.
  - **Lovebird:** red face, green body, blue rump.
- **Perches on a real-looking branch.**
- **Touch:**
  - steps sideways toward my finger
  - crest up and a squawk on a tap
  - hold to get a head scratch (it fluffs up)
  - long-press offers a seed
- **Idles:**
  - talking: a speech bubble with words I can type in settings, plus a chattering beak
  - whistling a tune
  - preening
  - head-bobbing
  - one-leg stand with the raised foot like in the photo
  - wing stretch
  - hanging upside-down
- **Music:** the famous parrot head-bang dance, synced to the BPM.

### 2.5 Glowy — anglerfish (two anglerfish references, amber and ghost variants)
- **Look:** a round translucent fish with a glowing lure on a stalk above its head, set in a deep-sea scene with bubbles and light rays.
- **Touch:**
  - the lure follows my finger
  - small fish gather at the touch point → it gulps them
  - a tap flashes the lure
- **Idles:**
  - slow drifting swim
  - turning to look at me
  - puffing up
  - blowing bubbles
  - yawning with tiny teeth
- **Music:** the lure pulses colours with the bass, and the fins flutter with the mids.

### 2.6 Gromble — wannabe-evil horned imp (the black horned imp, with the rainbow-horn furball as a variant)
- **Personality:** tries very hard to be scary and evil and always fails in a cute, stupid way.
- **Touch:**
  - it growls at my finger, then gets scared when the finger comes close
  - poke it three times and it "attacks", trips and falls
- **Idles:**
  - an evil laugh that ends in a coughing fit
  - an "EVIL PLAN" scroll that falls apart
  - a fireball that fizzles into smoke
  - flapping its little wings to fly and plopping down
  - "BOO!" and then hiding its face from embarrassment
  - a dramatic cape swish that tangles
- **Music:** headbangs like a metal fan, rainbow horns flash on the drops.

### 2.7 Sparky — the bat with goggles and a sparkler (bat reference)
- **Look:** a fuzzy bat with big goggles, waving a sparkler.
- **Touch:**
  - the sparkler draws glowing light trails where my finger drags (the trails fade)
  - a tap shoots a little firework
- **Idles:**
  - hanging upside-down to sleep in the daytime
  - awake and playful at night
  - clumsy flight loops
  - goggles fogging up
- **Music:** the sparkler fireworks burst on beats.

> After these, I may ask for more (a realistic bunny, etc.). Use the same quality bar.

**For every character, deliver:**
- a personality one-liner
- 8–12 unique animations
- touch map (tap / hold / drag / long-press / fast swipe)
- music behaviour
- charging, low-battery, morning, night and on-unlock greeting behaviours
- 2–6 variants
- default scene

---

## 3. Looping video wallpapers (Cosmic and other cinematic loops)

Each loop is made from **two clips** so it plays forever with no visible cut:
- **Clip A** goes from frame **F0** to frame **F1** (example: a slow push-in from space onto Earth).
- **Clip B** starts **exactly on F1** (the last frame of A) and ends **exactly on F0** (the first frame of A). Example: the camera pulls back out to space.
- Playback is A → B → A → B … forever, gapless.

**How to generate the clips:** use first-frame/last-frame video generation (Google Flow / Veo "frames to video", or similar).
1. Generate A.
2. Export its last frame as an exact PNG.
3. Generate B with first frame = A's last frame and last frame = A's first frame.

Give me the exact prompts for A and B for each loop. Make 8 starter loops:
- Earth zoom
- Saturn ring fly-through
- Black-hole orbit
- Nebula drift
- Sun corona
- Aurora over mountains
- Underwater bioluminescence
- Neon city rain

**Player:** two pre-buffered ExoPlayer instances (or a gapless concatenating source), hardware decoding, H.265/AV1 at the screen resolution, with no frame drop at the seams.

**Reactivity on top (cheap shader overlay):**
- brightness and bloom pulse on the bass
- particles on the treble
- touch makes a light ripple or a parallax tilt
- the gyroscope adds a gentle parallax

I can also import my own two-clip loops.

---

## 4. Liquid Obsidian — new concept (completely different from v2)

A **black chrome ferrofluid** world.

- **Look:** a pool of glossy black magnetic liquid that reflects a gradient environment in the user's chosen colours.
- **Touch:**
  - wherever I touch, the fluid rises into ferrofluid spikes that follow my finger
  - two fingers create two magnetic poles that pull the liquid between them
- **Music:**
  - bass makes the whole pool erupt into rhythmic spike crowns
  - mids ripple the surface
  - treble makes fine sparkles on the reflections
- **Options:**
  - colour gradient (from the app theme or custom)
  - reflection strength
  - liquid amount
  - brightness
  - spike sharpness
- **Implementation:** GPU heightfield simulation at about ¼ resolution, plus an environment-mapped reflection shader. Idle cost must stay tiny.

---

## 5. Gloop Gang — new concept (completely different from v2)

A **jelly band on a tiny stage**. Each member is a different soft-body shape with a different physics material, and each one plays the part of the music that matches its band:

| Member | Body | Plays | Reacts to |
|---|---|---|---|
| **Thud** | heavy square jello cube | drums | bass hits (squashes on every kick) |
| **Twang** | long stretchy noodle | bass guitar | low-mids (wobbles along its length) |
| **Pop** | bouncy rubber ball | keys | mids (bounces from key to key) |
| **Drip** | water droplet | singer | treble/vocals (mouth lip-syncs, splits into droplets on high notes) |

- **Stage:** spotlights follow the loudest member, and a crowd of tiny glowing blobs jumps on the drops.
- **Touch:**
  - fling a member off the stage → it bounces back and keeps playing
  - a tap makes it do a solo
  - long-press throws confetti
- **Without music:** they tune their instruments, chat, and fall asleep backstage at night.
- **Options:**
  - band colour themes
  - stage style: club, rooftop, underwater, space
  - brightness

---

## 6. Performance and battery budget (non-negotiable)

- **Frame rate:**
  - 60 fps while touched or while music is playing
  - 30 fps when idle and visible
  - 10–15 fps when the character is asleep
  - **0 fps when the wallpaper is not visible** (stop the render loop, release the Visualizer and the video decoders)
- **Background layers:** static sky and ground are cached and redrawn only when they change. Animated background layers update at a reduced rate.
- **Assets:**
  - one texture atlas per character, at most 2048² and mipmapped
  - load lazily and unload when not used
  - no per-frame allocations, no garbage-collection spikes
- **Rendering:**
  - internal render scale adapts automatically (from 0.6× to 1.0×) to hold the frame budget
  - no full-screen blur, no CPU readback
- **Targets:**
  - under 3% battery per hour on the home screen while idle and visible
  - under 6% per hour while music-reactive
  - memory under 150 MB
  - cold start of the wallpaper under 1 s
- **Proof:** measure with Android Studio's Energy/CPU/GPU profilers on a mid-range phone and show me the numbers.

---

## 7. App UI (clean, uncluttered, themeable)

**Theme engine:**
- I choose one accent colour from a picker, or from presets, or let it come from the current character.
- The whole app derives a gradient system from it:
  - background glow
  - cards
  - buttons
  - selection states
  - the spectrum bar
- The base stays true AMOLED black.
- Typography: Plus Jakarta Sans / Inter. Rounded glass cards are allowed in the app screens, but **never put blur over a live animated canvas**.

**Home:**
- A big hero card of my current character, alive and animated.
- A gallery of characters, worlds and loops, with filter chips.

**Studio (preview of one wallpaper):**
- Full-screen live preview.
- A compact bottom dock with tabs: **Look**, **Tricks**, **Mood**, **Music**. Only one panel is open at a time.
- A drag handle: swipe the dock down to close the panel, swipe again to hide the dock completely.
- A clearly labelled **Hide** button at the top. When hidden, a clear **Show controls** pill appears.
- A big **Set as live wallpaper** button.

**Settings:**
- music-reaction toggle (explains the permission)
- frame-rate / battery-saver mode
- shuffle character on unlock
- theme colour

---

## 8. Home-screen widget

- Sizes 2×2, 4×2 and 4×4 (Jetpack Glance / AppWidget).
- Shows **the character I choose**, in a pose that matches its current mood and the time of day:
  - morning stretch, day play, evening chill, night sleep
  - eating while charging
  - tired at low battery
- Updates on a schedule (about every 30 minutes) and on battery/charging events. No continuous animation, to save battery.
- Optional small extras: time, battery percentage, or a speech bubble with a cute line.
- Tapping the widget plays a short reaction and opens the app.

---

## 9. Paid feature — "Create your own creature" with AI

**User flow:**
1. The user describes a creature, or uploads a photo of their pet.
2. They pick a style (cute 3D render like ours, or realistic).
3. The app generates a preview → the user confirms → the app builds a fully animated, interactive wallpaper character.

**Pipeline (server side):**
1. An image-generation model (Imagen/Gemini, Flux, or similar) makes a **turnaround and layered part sheet** in the house style, using fixed prompt templates and reference images for consistency.
2. Automatic segmentation into parts (transparent PNGs).
3. Map the parts onto one of a few **template rigs**: quadruped, biped, bird, fish, blob. Each template already has the full set of behaviours and state-machine inputs, so the new creature gets touch, music and mood animations automatically.
4. Package an atlas + rig file → the app downloads and caches it.

**Backend and billing:**
- Firebase (Auth, Cloud Functions, Storage) or similar.
- **API keys never live in the app.** Rate limiting and content moderation on all prompts and uploads.
- Billing with Google Play Billing: a subscription and/or credit packs.
- Show me the cost per generation and suggest pricing.

**Deliver this after the core characters are approved.** Start with a technical design and a cost estimate. If any step is not feasible yet, say exactly which one and propose the closest workable version.

---

## 10. Delivery

- Git repo with GitHub Actions building a signed release APK/AAB on every push.
- Each milestone gives me:
  - an installable APK
  - a short screen recording
  - a list of what changed
- **Milestone order:**
  1. Architecture + empty wallpaper running at budget
  2. Mochi
  3. Kalboob
  4. Kitty
  5. Kiwi parrots
  6. Glowy
  7. Gromble
  8. Sparky
  9. Looping videos
  10. Liquid Obsidian
  11. Gloop Gang
  12. Theme engine + UI polish
  13. Widget
  14. AI creature creator

  **Wait for my approval after each one.**

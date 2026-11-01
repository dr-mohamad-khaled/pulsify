# Pulsify 2 source

`index.html` at the repo root is built from these files:

```
python3 -c "s=open('shell.html').read();e=''.join(open(f).read()+'\n' for f in ['core.js','buddy_base.js','pipbolt.js','scenes.js','app.js']);open('build.html','w').write(s.replace('/*ENGINE*/',e))"
```

then wrapped with the root head (manifest, service worker). Modules:

- `core.js` — maths, audio engine (FFT bands, beat, BPM), test-beat synth, SFX, host state, shared WebGL renderer
- `buddy_base.js` — soft-body jelly shader, the shared buddy brain (moods, time of day, charging, touch, music) and Mochi
- `pipbolt.js` — Pip (fur shells, headphones, static charging) and Bolt (silicone robot, LED face, plug-in charging)
- `scenes.js` — Cosmic Odyssey (ray-traced planets/sun/galaxy), Liquid Obsidian (GPU wave sim), Gloop Gang, Loops, Cars (code-lit with a light-map editor)
- `app.js` — home, studio, controls, live-wallpaper bridge

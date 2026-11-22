# Particle Universe — SimpMusic integration staging

Prototype source staged on a separate feature branch. This is **not yet wired into Now Playing or Media3 playback**, and no APK has been built.

Defaults: particles mode; intensity 0.65; speed 0.65; album-derived palette; album artwork hidden.

## Integration steps
1. Move ParticleUniverse.kt into the appropriate Compose UI source set, updating its package.
2. Add a Particle Universe mode to the Now Playing visualizer selector.
3. Supply album-derived colours from the existing artwork/palette system (artwork remains hidden).
4. Wire real normalized FFT band data from the Media3 playback audio pipeline. Avoid fake music-reactive claims.
5. Suspend animation when the player is not visible, and test playback effects, Bluetooth, performance and battery usage.
6. Build and test Android APK; do not merge until verified.

The renderer is an isolated initial component, not a completed integration.

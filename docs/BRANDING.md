# Pocket Host branding integration

**Canonical source:** `Assets/Pocket Host 7.svg`

## Single source of truth

The supplied SVG is the canonical artwork. It is not replaced by a separately drawn logo or a text placeholder.

- The Compose owner header and navigation sidebar use the generated `ic_launcher_artwork` resource.
- SVG rasterization happens at build time; the large vector is not parsed on the Android main thread and is not duplicated into the APK assets directory.
- The app manifest uses the Pocket Host label and references the launcher icon resources.

## Launcher asset generation

`buildSrc` contains `GeneratePocketHostLauncherIcons`, a Gradle task using Apache Batik. It rasterizes the canonical SVG at 288×288 once, then scales it with high-quality bicubic filtering into density-qualified resources:

| Density | Legacy icon | Adaptive foreground artwork |
|---|---:|---:|
| mdpi | 48×48 px | 72×72 px |
| hdpi | 72×72 px | 108×108 px |
| xhdpi | 96×96 px | 144×144 px |
| xxhdpi | 144×144 px | 216×216 px |
| xxxhdpi | 192×192 px | 288×288 px |

The adaptive icon places the 72dp artwork inside an 18dp inset on the 108dp adaptive viewport, keeping the supplied art inside the platform safe zone. The legacy resource points at the same generated artwork. The generator runs as a `preBuild` dependency; generated PNGs are build outputs and are not hand-maintained.

## Verification boundary

- Source integration is committed on `Alpha`.
- Android CI must confirm buildSrc compilation, Batik dependency resolution, SVG rasterization, Android resource merging and Kotlin compilation.
- Physical launcher appearance, adaptive-mask cropping and runtime SVG rendering still require device verification.
- This integration does not assemble an APK and does not change the v0.1.0 Alpha release status.

# FastBoot 1.1.1 — resource indexing candidate

FastBoot is a client-side Fabric mod that reduces repeated resource-pack filesystem work during Minecraft startup. It keeps Minecraft's real loading pipeline and normal title screen intact.

**No custom menu, no fake readiness, no skipped resource reloads, and no removed resources.**

## What this version changes

### ZIP-backed resource listing cache

Minecraft's `FilePackResources` implementation scans ZIP entries when listing namespaces and resources. Startup resource reloads can request listings repeatedly. FastBoot builds one immutable index per open ZIP-backed pack instance, grouped by pack type and namespace, then uses binary search to visit only the requested resource-path prefix.

- The index is created lazily, on the first namespace/resource-listing request for that pack.
- It covers both `assets/` and `data/`, plus overlay prefixes supplied by Minecraft.
- It preserves the archive's resource names and `ZipEntry` objects; opening resource streams still goes through the original `ZipFile`.
- The index is cleared when the resource pack closes.
- Resource results are emitted in ZIP central-directory order, matching vanilla's iteration order.
- If indexing throws a runtime exception, FastBoot remembers the failure and leaves that pack on Minecraft's vanilla implementation rather than retrying the index on every query.
- Resource identifiers are still validated with Minecraft's version-specific `ResourceLocation.tryBuild` / `Identifier.tryBuild` API.

These optimizations target repeated ZIP scans and repeated folder walks. Index construction itself costs time and memory, so the benefit depends on pack size and how often Minecraft asks each pack for resource listings. They are not guaranteed to speed up every installation. The synthetic ZIP microbenchmark and its limitations are documented in `BUILD_STATUS.md`.


### Directory-backed resource listing cache

`PathPackResources` can repeatedly walk a namespace folder when Minecraft asks for resource listings. FastBoot now builds a lazy index only when a namespace is first listed, then answers later directory queries in that namespace from sorted relative paths. It does not scan unrelated namespaces during `getNamespaces()`, and it stores path strings rather than a second `Path` object for every file. Paths are reconstructed only for matches. Symbolic links are not followed. If indexing fails, FastBoot lets vanilla handle that namespace. The index is cleared when the pack closes.

This applies to Minecraft `PathPackResources` folder packs. It does **not** yet optimize Fabric's separate bundled-mod resource-pack implementation. Older Resource Loader versions use `ModNioResourcePack`; 1.21.11 uses the newer Resource Loader API. That needs its own version-specific implementation and tests.

### Title-screen fade

The existing tiny title-screen mixin disables the fade on the real Minecraft title screen. This is visual only; it does not count as a loading-time optimization.

### Deliberately not changed

FastBoot does not force thread counts, skip model baking, skip texture uploads, bypass mod initialization, disable reload listeners, or skip DataFixer conversions. Those changes can cause compatibility problems or merely move work to later. They are not included without measurements and version-specific tests.

## Supported Minecraft versions

FastBoot is configured to build one version-specific JAR for each target below. Install only the JAR matching your exact Minecraft version.

| Minecraft | JAR |
|---|---|
| 1.21 | `fastboot-1.1.1+mc1.21.jar` |
| 1.21.1 | `fastboot-1.1.1+mc1.21.1.jar` |
| 1.21.2 | `fastboot-1.1.1+mc1.21.2.jar` |
| 1.21.3 | `fastboot-1.1.1+mc1.21.3.jar` |
| 1.21.4 | `fastboot-1.1.1+mc1.21.4.jar` |
| 1.21.5 | `fastboot-1.1.1+mc1.21.5.jar` |
| 1.21.6 | `fastboot-1.1.1+mc1.21.6.jar` |
| 1.21.7 | `fastboot-1.1.1+mc1.21.7.jar` |
| 1.21.8 | `fastboot-1.1.1+mc1.21.8.jar` |
| 1.21.9 | `fastboot-1.1.1+mc1.21.9.jar` |
| 1.21.10 | `fastboot-1.1.1+mc1.21.10.jar` |
| 1.21.11 | `fastboot-1.1.1+mc1.21.11.jar` |

Minecraft 1.21.11 has a separate source adapter because Mojang renamed `ResourceLocation` to `Identifier`. Minecraft 26.x is outside this project's target matrix.

## Requirements

- Minecraft Java 1.21 through 1.21.11 (matching JAR required)
- Fabric Loader 0.18.1 or newer
- Java 21 or newer
- Build: Gradle 9.2.1 and Java 21

## Installation

1. Select the JAR for the exact Minecraft version.
2. Put that JAR in the relevant Fabric instance's `mods` folder.
3. Do not install multiple FastBoot version JARs in the same instance.
4. Launch Minecraft normally.

## Build one version

```sh
gradle --no-daemon clean validateAccessWidener test build verifyFastBootArtifact -Pminecraft_version=1.21.1
```

Replace `1.21.1` with one of the twelve supported versions. The default local target is 1.21.11. Install the remapped JAR from `build/libs/`; do not install `-dev` or `-sources` JARs.

CI builds all twelve targets separately, runs the unit tests, validates the access widener, and verifies the remapped artifacts. A successful build is necessary but does not replace an actual launch test with that Minecraft version.

## Benchmarking

See [BENCHMARKING.md](BENCHMARKING.md) for cold/warm startup comparisons, JFR capture, and resource-pack test guidance. Do not claim a two-second launch unless repeated measurements on the target machine demonstrate it.

## Current verification status

See [BUILD_STATUS.md](BUILD_STATUS.md) and [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md). This package has not been compiled against the Minecraft dependencies or launched in-game in this environment. It is a source candidate, not a verified binary release. Its resource-pack listing mixins overlap in purpose with ModernFix resource-pack caching; using both has not been compatibility-tested and should not be assumed to provide additive gains.

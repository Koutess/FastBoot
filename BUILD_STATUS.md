# FastBoot 1.1.1 build status

## Implemented in the source candidate

- Added `ZipPackIndex`, a one-pass index of ZIP entries under each configured pack-type directory and namespace.
- Added `DirectoryPackIndex`, a lazy per-namespace index for folder-backed resources under `assets/` and `data/`; it does not scan unrelated namespaces during cheap vanilla namespace discovery.
- `FilePackResourcesMixin` lazily builds a ZIP index per resource-pack object and uses it to answer `getNamespaces` and `listResources` calls.
- `PathPackResourcesMixin` lazily builds an index only for a namespace when its first resource listing is requested; `getNamespaces` remains vanilla to avoid eagerly walking entire folders.
- Supports the `prefix` used by Minecraft for overlays.
- Uses a volatile immutable index after construction; synchronization occurs only during the first build.
- If index creation throws a runtime exception, that pack falls back to vanilla listing and the failed state is remembered to avoid retrying on each later query.
- Clears each index when its pack object's `close()` runs.
- Uses separate `ResourceLocation` (1.21–1.21.10) and `Identifier` (1.21.11) adapters.
- Kept the original title-screen fade change only as a visual preference; it is not a performance optimization.
- Added unit tests for ZIP namespaces, path-prefix matching, overlays, missing paths, and original ZIP entry ordering.
- Added unit tests for lazy namespace indexing, assets/data separation, missing-namespace fallback, empty-directory queries, and path-prefix matching.
- Added access-widener validation and updated artifact checks.

## Checks performed locally

- Standalone Java 21 smoke tests of both `ZipPackIndex` and `DirectoryPackIndex` against synthetic ZIP archives and folder trees.
- JSON and YAML syntax checks.
- All twelve Minecraft version strings checked in the Gradle allowlist and CI matrices.
- ZIP integrity and SHA-256 verification for the source package.

## Standalone synthetic ZIP benchmark

The standalone Java 21 benchmark creates a synthetic ZIP with 60,000 resource entries and runs 60 namespace/path listing queries. It compares a full ZIP entry scan for every query with one index build followed by the same indexed queries. Each result below is the median of seven measured trials; timings vary with JIT warm-up and system load.

| Run | Repeated full scans | Index build + queries | Speedup |
|---:|---:|---:|---:|
| 1 | 319.2 ms | 66.5 ms | 4.80x |
| 2 | 307.3 ms | 38.1 ms | 8.06x |
| 3 | 208.7 ms | 38.0 ms | 5.49x |
| 4 | 197.4 ms | 33.9 ms | 5.82x |
| 5 | 218.2 ms | 39.5 ms | 5.52x |
| 6 | 272.3 ms | 62.0 ms | 4.39x |
| 7 | 280.5 ms | 48.7 ms | 5.76x |

Across these runs the measured speedup ranged from 4.39x to 8.06x. The latest run was 5.76x; timing variation is substantial enough that this should be treated as a local algorithm check, not a performance promise. This is a microbenchmark of ZIP resource listing only, **not** a Minecraft boot benchmark. It excludes directory indexing, Fabric's bundled-mod resource packs, model parsing/baking, texture processing/upload, mod initialization, graphics initialization, and real-world resource-pack variation.

The benchmark source is at `benchmarks/ZipPackIndexBenchmark.java`. Run it with:

```sh
mkdir -p build/benchmark-classes
javac --release 21 -d build/benchmark-classes src/main/java/dev/fastboot/resources/ZipPackIndex.java benchmarks/ZipPackIndexBenchmark.java
java -cp build/benchmark-classes ZipPackIndexBenchmark
```

## Current coverage limits

- ZIP-backed packs: indexed through `FilePackResources`. Namespace discovery also preserves vanilla behavior for an entry named exactly `assets/<namespace>` with no trailing slash.
- Folder-backed packs: lazily indexed per namespace through `PathPackResources`; vanilla still performs cheap namespace discovery, while resource enumeration does not follow symbolic links.
- Fabric bundled mod resources: not yet indexed. Older Resource Loader releases use a separate `ModNioResourcePack` implementation, while 1.21.11 uses the newer Resource Loader API. This requires a dedicated version-specific mixin and more testing; it is intentionally not guessed at here.
- Model baking, texture stitching/upload, registries, and third-party mod initialization are not modified in this candidate.

## Not verified here

Gradle is not installed in this environment, and external dependency repositories are not reachable from this session. Therefore the Minecraft compile, Mixin annotation processing, `validateAccessWidener`, remapped artifact verification, JUnit suite, and actual game launches have **not** been run here. The two indexing algorithms compile and pass standalone smoke tests, but their Minecraft integration still needs all twelve CI builds and runtime checks.

This is a source candidate, not a verified compiled release. Do not claim that it fixes startup time on every installation until A/B benchmarks establish the result.

## CI failure analyzed (2026-10-31)

The supplied Minecraft 1.21.11 GitHub Actions log shows:

- `validateAccessWidener`, `compileJava`, `jar`, `sourcesJar`, `compileTestJava`, and `testClasses` completed.
- JUnit ran 8 tests; 7 passed and `FastBootMixinConfigTest.onlyFunctionalMixinsAreEnabledAndMixinConfigurationFailsOpen()` failed at line 36.
- The Mixin JSON correctly contains `FilePackResourcesMixin`, `PathPackResourcesMixin`, and `TitleScreenFadeMixin`, but the test's expected set omitted `PathPackResourcesMixin`.
- 1.1.1 updates the expected set and still asserts exactly three mixins. It does not suppress or skip the test.

The supplied log verifies compilation for Minecraft 1.21.11, but it does not verify the corrected test, artifact verification task, the other eleven version builds, or launching the game. Those remain CI/runtime release gates.

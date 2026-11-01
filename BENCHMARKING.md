# FastBoot benchmarking

The objective is to reduce real process-start-to-first-frame-of-the-normal-title-screen time. FastBoot must not draw a replacement menu or pretend loading has completed.

## A/B protocol

For each target Minecraft version, compare the same Fabric instance with FastBoot absent and present. Keep the Java runtime, JVM arguments, memory allocation, graphics settings, mods, resource packs, launcher, and machine state the same.

Record separately:

1. Process creation to first normal title-screen frame.
2. JVM startup to Fabric Loader initialization.
3. Fabric/mod initialization to the beginning of resource reload.
4. Resource reload start to completion.
5. Cold launch and warm launch.

Run at least five trials per configuration and compare medians. Do not compare a cold vanilla launch with a warm FastBoot launch. Note whether OS file cache is warm.

## Java Flight Recorder

For diagnostic runs, capture JFR from process start, for example by adding a Java Flight Recorder start option to the launcher:

```text
-XX:StartFlightRecording=filename=fastboot-startup.jfr,settings=profile,dumponexit=true
```

Use JDK Mission Control or another JFR viewer to inspect CPU samples, file I/O, class loading, monitor contention, thread parking, and garbage collection. Profile vanilla and FastBoot with identical settings.

## ZIP index microbenchmark

A standalone synthetic microbenchmark is available at `benchmarks/ZipPackIndexBenchmark.java`. It compares repeated full archive scans with one index build plus indexed queries. Run it using the commands in `BUILD_STATUS.md`. This measures only the ZIP-listing algorithm; do not treat it as a full Minecraft startup measurement.

## Resource index test focus

Test at least these resource-pack cases:

- Vanilla client and server resources in ordinary ZIP packs.
- Large mod resource JARs with many thousands of entries.
- ZIPs with and without explicit directory entries.
- Resource-pack overlays.
- Folder packs with nested resources, missing namespaces, missing directories, and repeated listings within the same namespace.
- Folder packs containing symbolic links (these are intentionally not followed by FastBoot's directory index).
- Resource reload after changing packs, and closing/reopening packs.
- Duplicate or unusual ZIP entries where supported by the pack creator.
- Fabric bundled mod resources, which are not yet indexed by this candidate and need a separate version-specific adapter.

Check that namespace sets and resource IDs are identical to vanilla. ZIP result order is kept in central-directory order; folder results are sorted by relative path (resource IDs within a folder pack cannot be duplicated). Measure the cost of the first index build as well as all later listings; the cache is useful only when it reduces total critical-path time.

## Correctness requirements

- Normal title screen, buttons, and resource contents must be unchanged except for the fade.
- Resource reloads must complete normally.
- Datapacks, mod assets, language files, models, textures, and sounds must remain available.
- Pack changes and reloads must not reuse stale indexes.
- No data conversion or resource-loading stage may be skipped.

Do not claim a two-second boot unless repeated measurements on the target machine demonstrate it. Task durations can overlap across threads; use critical-path/elapsed-time measurements, not a sum of per-thread CPU time.

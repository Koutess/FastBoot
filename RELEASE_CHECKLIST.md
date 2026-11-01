# FastBoot 1.1.1 release checklist

## Automated build matrix

- [ ] Minecraft 1.21 build, access-widener validation, tests, and artifact verification pass.
- [ ] Minecraft 1.21.1 build, access-widener validation, tests, and artifact verification pass.
- [ ] Minecraft 1.21.2 build, access-widener validation, tests, and artifact verification pass.
- [ ] Minecraft 1.21.3 build, access-widener validation, tests, and artifact verification pass.
- [ ] Minecraft 1.21.4 build, access-widener validation, tests, and artifact verification pass.
- [ ] Minecraft 1.21.5 build, access-widener validation, tests, and artifact verification pass.
- [ ] Minecraft 1.21.6 build, access-widener validation, tests, and artifact verification pass.
- [ ] Minecraft 1.21.7 build, access-widener validation, tests, and artifact verification pass.
- [ ] Minecraft 1.21.8 build, access-widener validation, tests, and artifact verification pass.
- [ ] Minecraft 1.21.9 build, access-widener validation, tests, and artifact verification pass.
- [ ] Minecraft 1.21.10 build, access-widener validation, tests, and artifact verification pass.
- [ ] Minecraft 1.21.11 build, access-widener validation, tests, and artifact verification pass.
- [ ] Each remapped JAR contains matching metadata, mixin config, generated refmap, access widener, and index classes.

## Runtime checks

For every target version:

- [ ] Launch using the matching Fabric Loader and JAR.
- [ ] Reach the real Minecraft title screen without a Mixin transformation error.
- [ ] Confirm the normal title screen and buttons work.
- [ ] Compare vanilla and FastBoot namespace/resource listings for representative ZIP packs.
- [ ] Test overlays, large ZIPs, resource-pack changes, and reloads.
- [ ] Open a test world and verify datapacks and resources load normally.
- [ ] Compare at least five cold and warm startup trials against vanilla Fabric.

Do not call the release build-verified until all matrix jobs pass, or runtime-verified until matching game launches pass. A passing compile is not a game-launch test.

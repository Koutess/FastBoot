# Changelog

## 1.1.1 — CI test correction

- Fixed `FastBootMixinConfigTest` to expect all three configured client mixins, including `PathPackResourcesMixin`.
- Kept the test enabled and strict; no tests are skipped or disabled.
- Updated the version-specific artifact names to `1.1.1`.
- No runtime optimization behavior changed from 1.1.0.

## 1.1.0 — Resource indexing candidate

- Added a per-`FilePackResources` ZIP index for `assets/` and `data/` listings.
- Added a lazy per-namespace `PathPackResources` directory index; unrelated namespaces are not scanned during pack namespace discovery.
- Indexes resource names once per pack instance; subsequent namespace/resource-listing calls use immutable indexed data rather than enumerating the archive again.
- Supports Minecraft overlay prefixes, preserves ZIP entry order, and clears the cache when the pack closes.
- Added a one-time fail-open state: if index construction fails, that pack continues to use vanilla listing behavior without retrying on every query.
- Split the resource identifier adapter for Minecraft 1.21.11 (`Identifier`) and 1.21–1.21.10 (`ResourceLocation`).
- Added ZIP-index tests for namespaces, directories, overlays, missing paths, resource ordering, and prefix-boundary matching.
- Added directory-index tests for namespaces (including empty namespace folders), assets/data separation, missing paths, and directory-boundary matching.
- Added access-widener validation to CI and artifact verification.
- Kept the existing normal title-screen fade removal; it is explicitly not counted as a performance optimization.

## Verification disclaimer

The directory index does not yet cover Fabric’s separate mod-bundled resource pack implementation; that needs a separate version-specific adapter. This is an optimization candidate. Static checks and standalone ZIP- and directory-index smoke tests are run locally, but the Gradle/Minecraft build and actual game launches have not been executed in this environment. Do not publish as a verified release until all twelve matrix builds and version-specific launch tests pass.

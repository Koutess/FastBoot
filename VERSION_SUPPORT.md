# Minecraft version support

FastBoot 1.1.1 is configured for twelve separately compiled Minecraft Java versions:

`1.21`, `1.21.1`, `1.21.2`, `1.21.3`, `1.21.4`, `1.21.5`, `1.21.6`, `1.21.7`, `1.21.8`, `1.21.9`, `1.21.10`, and `1.21.11`.

Each build embeds the exact Minecraft dependency in `fabric.mod.json`; do not rename a JAR to use it on another version.

Mojang's 1.21.11 names `net.minecraft.resources.Identifier` where earlier 1.21.x versions use `net.minecraft.resources.ResourceLocation`. Gradle selects a matching source adapter for this API difference. The ZIP index itself has no Minecraft dependencies and is shared by both adapters.

The matrix compiles every minor version independently. This project has not yet passed that matrix in this environment; CI results and per-version launch tests are required before claiming support as verified. Minecraft 26.x is outside the matrix and needs a separate port.

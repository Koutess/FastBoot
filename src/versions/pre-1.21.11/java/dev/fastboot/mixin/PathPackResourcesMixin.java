package dev.fastboot.mixin;

import dev.fastboot.resources.DirectoryPackIndex;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;

/** Lazily caches repeated listings for directory-backed resource packs. */
@Mixin(PathPackResources.class)
public abstract class PathPackResourcesMixin {
    @Shadow @Final private Path root;

    @Unique
    private volatile DirectoryPackIndex fastboot$directoryIndex;

    @Unique
    private DirectoryPackIndex fastboot$getOrCreateIndex() {
        DirectoryPackIndex index = fastboot$directoryIndex;
        if (index != null) {
            return index;
        }

        synchronized (this) {
            index = fastboot$directoryIndex;
            if (index == null) {
                index = new DirectoryPackIndex(root);
                fastboot$directoryIndex = index;
            }
        }
        return index;
    }

    @Inject(method = "listResources", at = @At("HEAD"), cancellable = true, require = 0)
    private void fastboot$listResources(
            PackType type,
            String namespace,
            String path,
            PackResources.ResourceOutput output,
            CallbackInfo ci
    ) {
        DirectoryPackIndex index = fastboot$getOrCreateIndex();
        boolean indexed = index.forEachResource(type.getDirectory(), namespace, path, (resourcePath, file) -> {
            ResourceLocation identifier = ResourceLocation.tryBuild(namespace, resourcePath);
            if (identifier != null) {
                output.accept(identifier, () -> Files.newInputStream(file));
            }
        });
        if (indexed) {
            ci.cancel();
        }
    }

    @Inject(method = "close", at = @At("HEAD"), require = 0)
    private void fastboot$clearIndex(CallbackInfo ci) {
        fastboot$directoryIndex = null;
    }
}

package dev.fastboot.mixin;

import dev.fastboot.resources.ZipPackIndex;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Caches ZIP resource listings once per FilePackResources instance. */
@Mixin(FilePackResources.class)
public abstract class FilePackResourcesMixin {
    @Shadow @Final private FilePackResources.SharedZipFileAccess zipFileAccess;
    @Shadow @Final private String prefix;

    @Unique
    private volatile ZipPackIndex fastboot$zipIndex;

    @Unique
    private volatile boolean fastboot$indexUnavailable;

    @Unique
    private ZipPackIndex fastboot$getOrCreateIndex(ZipFile zipFile) {
        ZipPackIndex index = fastboot$zipIndex;
        if (index != null || fastboot$indexUnavailable) {
            return index;
        }

        synchronized (this) {
            index = fastboot$zipIndex;
            if (index == null && !fastboot$indexUnavailable) {
                try {
                    var directories = Arrays.stream(PackType.values())
                            .map(PackType::getDirectory)
                            .toList();
                    index = new ZipPackIndex(zipFile, prefix, directories);
                    fastboot$zipIndex = index;
                } catch (RuntimeException exception) {
                    // A broken or unusual archive should use the vanilla path. Remember
                    // the failure so every later resource query does not retry the scan.
                    fastboot$indexUnavailable = true;
                    return null;
                }
            }
        }
        return index;
    }

    @Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true, require = 0)
    private void fastboot$getNamespaces(PackType type, CallbackInfoReturnable<Set<String>> cir) {
        ZipFile zipFile = zipFileAccess.getOrCreateZipFile();
        if (zipFile == null) {
            return;
        }

        ZipPackIndex index = fastboot$getOrCreateIndex(zipFile);
        if (index != null) {
            cir.setReturnValue(index.getNamespaces(type.getDirectory()));
        }
    }

    @Inject(method = "listResources", at = @At("HEAD"), cancellable = true, require = 0)
    private void fastboot$listResources(
            PackType type,
            String namespace,
            String path,
            PackResources.ResourceOutput output,
            CallbackInfo ci
    ) {
        ZipFile zipFile = zipFileAccess.getOrCreateZipFile();
        if (zipFile == null) {
            return;
        }

        ZipPackIndex index = fastboot$getOrCreateIndex(zipFile);
        if (index == null) {
            return;
        }

        index.forEachResource(type.getDirectory(), namespace, path, (resourcePath, entry) -> {
            Identifier identifier = Identifier.tryBuild(namespace, resourcePath);
            if (identifier != null) {
                output.accept(identifier, () -> zipFile.getInputStream(entry));
            }
        });
        ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"), require = 0)
    private void fastboot$clearIndex(CallbackInfo ci) {
        fastboot$zipIndex = null;
        fastboot$indexUnavailable = false;
    }
}

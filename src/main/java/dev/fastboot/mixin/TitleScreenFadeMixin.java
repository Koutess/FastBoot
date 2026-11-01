package dev.fastboot.mixin;

import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disables the vanilla title-screen fade after the real screen initializes. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenFadeMixin {
    @Shadow
    private boolean fading;

    @Inject(method = "init()V", at = @At("RETURN"), require = 0)
    private void fastboot$disableFade(CallbackInfo ci) {
        fading = false;
    }
}

package com.cookiecraftmods.roadbuilder.mixin.client;

import com.cookiecraftmods.roadbuilder.client.RoadBuilderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.moulberry.axiom.hooks.ScreenRenderHook", remap = false)
abstract class AxiomHotbarMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true, require = 0)
    private static void roadbuilder$hideBuilderToolSlot(CallbackInfo callback) {
        if (RoadBuilderClient.modeController().isActive()) {
            callback.cancel();
        }
    }
}

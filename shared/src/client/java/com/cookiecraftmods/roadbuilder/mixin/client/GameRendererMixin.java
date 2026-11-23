package com.cookiecraftmods.roadbuilder.mixin.client;

import com.cookiecraftmods.roadbuilder.client.RoadBuilderClient;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void roadbuilder$hideFirstPersonHand(CallbackInfo callback) {
        if (RoadBuilderClient.modeController().isActive()) {
            callback.cancel();
        }
    }
}

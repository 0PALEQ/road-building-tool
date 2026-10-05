package com.cookiecraftmods.roadbuilder.mixin.client;

import com.cookiecraftmods.roadbuilder.client.RoadBuilderClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true)
    private void roadbuilder$suppressGameplayKeybinds(CallbackInfo callback) {
        if (RoadBuilderClient.modeController().isActive()) {
            callback.cancel();
        }
    }
}

package com.cookiecraftmods.roadbuilder.mixin.client;

import com.cookiecraftmods.roadbuilder.client.RoadBuilderClient;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiMixin {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void roadbuilder$hideCrosshair(CallbackInfo callback) {
        roadbuilder$cancelInMode(callback);
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void roadbuilder$hideHotbar(CallbackInfo callback) {
        roadbuilder$cancelInMode(callback);
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void roadbuilder$hidePlayerHealth(CallbackInfo callback) {
        roadbuilder$cancelInMode(callback);
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void roadbuilder$hideEffects(CallbackInfo callback) {
        roadbuilder$cancelInMode(callback);
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void roadbuilder$hideExperience(CallbackInfo callback) {
        roadbuilder$cancelInMode(callback);
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void roadbuilder$hideSelectedItem(CallbackInfo callback) {
        roadbuilder$cancelInMode(callback);
    }

    private static void roadbuilder$cancelInMode(CallbackInfo callback) {
        if (RoadBuilderClient.modeController().isActive()) {
            callback.cancel();
        }
    }
}

package com.cookiecraftmods.roadbuilder.mixin.client;

import com.cookiecraftmods.roadbuilder.client.RoadBuilderClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void roadbuilder$handleKeyboard(
            long window,
            int key,
            int scanCode,
            int action,
            int modifiers,
            CallbackInfo callback
    ) {
        if (RoadBuilderClient.modeController().handleKeyboard(
                Minecraft.getInstance(),
                window,
                key,
                action,
                modifiers
        )) {
            callback.cancel();
        }
    }
}

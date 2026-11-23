package com.cookiecraftmods.roadbuilder.mixin.client;

import com.cookiecraftmods.roadbuilder.client.RoadBuilderClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void roadbuilder$handleMouseButton(
            long window,
            int button,
            int action,
            int modifiers,
            CallbackInfo callback
    ) {
        Minecraft client = Minecraft.getInstance();
        if (RoadBuilderClient.modeController().handleMouseButton(
                client,
                window,
                button,
                action
        )) {
            callback.cancel();
        }
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    private void roadbuilder$handleCursorPosition(
            long window,
            double x,
            double y,
            CallbackInfo callback
    ) {
        RoadBuilderClient.modeController().handleCursorPosition(
                Minecraft.getInstance(),
                window,
                x,
                y
        );
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void roadbuilder$handleMouseScroll(
            long window,
            double horizontalOffset,
            double verticalOffset,
            CallbackInfo callback
    ) {
        if (RoadBuilderClient.modeController().handleMouseScroll(
                Minecraft.getInstance(),
                window,
                verticalOffset
        )) {
            callback.cancel();
        }
    }
}

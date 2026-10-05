package com.cookiecraftmods.roadbuilder.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker("getFov")
    double roadbuilder$getFov(
            Camera camera,
            float partialTick,
            boolean useFovSetting
    );
}

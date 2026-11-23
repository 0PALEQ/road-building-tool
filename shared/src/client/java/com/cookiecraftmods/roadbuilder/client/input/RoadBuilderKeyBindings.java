package com.cookiecraftmods.roadbuilder.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class RoadBuilderKeyBindings {
    private static KeyMapping toggleMode;

    private RoadBuilderKeyBindings() {
    }

    public static void register() {
        if (toggleMode != null) {
            throw new IllegalStateException("Road Builder key bindings are already registered");
        }

        toggleMode = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.roadbuilder.toggle_mode",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "key.categories.roadbuilder"
        ));
    }

    public static boolean consumeToggleMode() {
        return toggleMode != null && toggleMode.consumeClick();
    }
}

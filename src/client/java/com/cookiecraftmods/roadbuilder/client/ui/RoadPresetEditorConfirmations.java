package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

final class RoadPresetEditorConfirmations {
    private ResourceLocation pendingSwitch;
    private boolean deleteArmed;
    private boolean closeArmed;

    boolean armDelete() {
        if (deleteArmed) {
            return false;
        }
        deleteArmed = true;
        closeArmed = false;
        pendingSwitch = null;
        return true;
    }

    boolean deleteArmed() {
        return deleteArmed;
    }

    boolean confirmSwitch(ResourceLocation target) {
        Objects.requireNonNull(target, "target");
        if (target.equals(pendingSwitch)) {
            return true;
        }
        pendingSwitch = target;
        deleteArmed = false;
        closeArmed = false;
        return false;
    }

    boolean confirmClose() {
        if (closeArmed) {
            return true;
        }
        closeArmed = true;
        deleteArmed = false;
        pendingSwitch = null;
        return false;
    }

    void clear() {
        pendingSwitch = null;
        deleteArmed = false;
        closeArmed = false;
    }
}

package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.client.networking.RoadBuilderClientNetworking;
import com.cookiecraftmods.roadbuilder.networking.PresetContentS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetDeleteIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveResultS2CPacket;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalog;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

interface RoadPresetEditorGateway {
    RoadPresetCatalog catalog();

    boolean selectPreset(ResourceLocation id);

    boolean requestContent(UUID requestId, ResourceLocation id);

    void cancelContentRequest(UUID requestId);

    Optional<PresetContentS2CPacket> takeContent(UUID requestId);

    boolean sendSave(PresetSaveIntentC2SPacket packet);

    boolean sendDelete(PresetDeleteIntentC2SPacket packet);

    Optional<PresetSaveResultS2CPacket> takeSaveResult(UUID requestId);

    static RoadPresetEditorGateway production() {
        return Production.INSTANCE;
    }

    enum Production implements RoadPresetEditorGateway {
        INSTANCE;

        @Override
        public RoadPresetCatalog catalog() {
            return RoadBuilderClientNetworking.presetCatalog();
        }

        @Override
        public boolean selectPreset(ResourceLocation id) {
            return RoadBuilderClientNetworking.selectPreset(id);
        }

        @Override
        public boolean requestContent(UUID requestId, ResourceLocation id) {
            return RoadBuilderClientNetworking.requestPresetContent(
                    requestId,
                    id
            );
        }

        @Override
        public void cancelContentRequest(UUID requestId) {
            RoadBuilderClientNetworking.cancelPresetContentRequest(requestId);
        }

        @Override
        public Optional<PresetContentS2CPacket> takeContent(UUID requestId) {
            return RoadBuilderClientNetworking.takePresetContent(requestId);
        }

        @Override
        public boolean sendSave(PresetSaveIntentC2SPacket packet) {
            return RoadBuilderClientNetworking.sendPresetSave(packet);
        }

        @Override
        public boolean sendDelete(PresetDeleteIntentC2SPacket packet) {
            return RoadBuilderClientNetworking.sendPresetDelete(packet);
        }

        @Override
        public Optional<PresetSaveResultS2CPacket> takeSaveResult(
                UUID requestId
        ) {
            return RoadBuilderClientNetworking.takePresetSaveResult(requestId);
        }
    }
}

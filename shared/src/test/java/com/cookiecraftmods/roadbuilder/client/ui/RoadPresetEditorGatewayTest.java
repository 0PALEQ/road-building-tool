package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.networking.PresetContentS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetDeleteIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.PresetSaveResultS2CPacket;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalog;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetTemplates;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoadPresetEditorGatewayTest {
    @Test
    void contentRequestIsConsumedThroughTheInjectedGateway() {
        FakeGateway gateway = new FakeGateway();
        ResourceLocation id = MinecraftIds.id("test", "road");
        RoadPresetEditorScreen screen = new RoadPresetEditorScreen(id, gateway);

        screen.requestContent();
        assertNotNull(gateway.contentRequestId);
        gateway.contentResult = PresetContentS2CPacket.current(
                gateway.contentRequestId,
                id,
                RoadPresetTemplates.blank()
        );

        screen.pollRequests();
        screen.pollRequests();

        assertEquals(1, gateway.contentTakeCount);
    }

    @Test
    void pendingSaveBlocksCloseUntilTheGatewayReturnsAResult() {
        FakeGateway gateway = new FakeGateway();
        ResourceLocation id = MinecraftIds.id("roadbuilder_world", "road");
        RoadPresetEditorScreen screen = RoadPresetEditorScreen.createNew(
                id,
                gateway
        );

        screen.saveDraft();
        assertNotNull(gateway.savePacket);
        assertDoesNotThrow(screen::onClose);
        assertNull(gateway.selectedId);

        gateway.saveResult = PresetSaveResultS2CPacket.accepted(
                gateway.savePacket.requestId(),
                1,
                1,
                ""
        );
        screen.pollRequests();

        assertEquals(id, gateway.selectedId);
        assertEquals(1, gateway.saveTakeCount);
    }

    private static final class FakeGateway implements RoadPresetEditorGateway {
        private final RoadPresetCatalog catalog = new RoadPresetCatalog(
                0,
                List.of()
        );
        private UUID contentRequestId;
        private PresetContentS2CPacket contentResult;
        private int contentTakeCount;
        private PresetSaveIntentC2SPacket savePacket;
        private PresetSaveResultS2CPacket saveResult;
        private int saveTakeCount;
        private ResourceLocation selectedId;

        @Override
        public RoadPresetCatalog catalog() {
            return catalog;
        }

        @Override
        public boolean selectPreset(ResourceLocation id) {
            selectedId = id;
            return true;
        }

        @Override
        public boolean requestContent(UUID requestId, ResourceLocation id) {
            contentRequestId = requestId;
            return true;
        }

        @Override
        public void cancelContentRequest(UUID requestId) {
            if (requestId.equals(contentRequestId)) {
                contentRequestId = null;
            }
        }

        @Override
        public Optional<PresetContentS2CPacket> takeContent(UUID requestId) {
            contentTakeCount++;
            if (!requestId.equals(contentRequestId)) {
                return Optional.empty();
            }
            PresetContentS2CPacket result = contentResult;
            contentResult = null;
            return Optional.ofNullable(result);
        }

        @Override
        public boolean sendSave(PresetSaveIntentC2SPacket packet) {
            savePacket = packet;
            return true;
        }

        @Override
        public boolean sendDelete(PresetDeleteIntentC2SPacket packet) {
            return true;
        }

        @Override
        public Optional<PresetSaveResultS2CPacket> takeSaveResult(
                UUID requestId
        ) {
            saveTakeCount++;
            if (savePacket == null || !requestId.equals(savePacket.requestId())) {
                return Optional.empty();
            }
            PresetSaveResultS2CPacket result = saveResult;
            saveResult = null;
            return Optional.ofNullable(result);
        }
    }
}

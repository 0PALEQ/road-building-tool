package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.persistence.RoadPresetSavedData;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetEditorService;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetResources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

final class RoadPresetNetworking {
    private RoadPresetNetworking() {
    }

    static void receiveContentRequest(
            PresetContentRequestC2SPacket packet,
            ServerPlayer player
    ) {
        if (!RoadServerTransport.canSend(player, PresetContentS2CPacket.TYPE)) {
            return;
        }
        RoadPresetResources.repository().find(packet.presetId())
                .ifPresent(preset -> RoadServerTransport.send(
                        player,
                        PresetContentS2CPacket.current(
                                packet.requestId(),
                                packet.presetId(),
                                preset
                        )
                ));
    }

    static void receiveSaveIntent(
            PresetSaveIntentC2SPacket packet,
            ServerPlayer player
    ) {
        if (!RoadServerTransport.canSend(player, PresetSaveResultS2CPacket.TYPE)) {
            return;
        }
        RoadPresetEditorService service = new RoadPresetEditorService(
                RoadPresetResources.repository(),
                actor -> actor.equals(player.getUUID()) && player.isCreative(),
                id -> BuiltInRegistries.BLOCK.containsKey(id)
                        && !BuiltInRegistries.BLOCK.get(id).defaultBlockState().isAir()
        );
        RoadPresetEditorService.EditResult editResult = service.save(
                player.getUUID(),
                packet.presetId(),
                packet.expectedRevision(),
                packet.candidate()
        );
        PresetSaveResultS2CPacket result;
        if (editResult instanceof
                RoadPresetEditorService.EditResult.Accepted accepted) {
            persistRepository(player);
            result = PresetSaveResultS2CPacket.accepted(
                    packet.requestId(),
                    accepted.catalog().revision(),
                    packet.candidate().revision(),
                    accepted.fingerprint()
            );
            broadcastCatalog(player.server);
        } else {
            RoadPresetEditorService.EditFailure failure =
                    ((RoadPresetEditorService.EditResult.Rejected) editResult).failure();
            result = PresetSaveResultS2CPacket.rejected(
                    packet.requestId(),
                    resultCode(failure)
            );
        }
        RoadServerTransport.send(player, result);
    }

    static void receiveDeleteIntent(
            PresetDeleteIntentC2SPacket packet,
            ServerPlayer player
    ) {
        if (!RoadServerTransport.canSend(player, PresetSaveResultS2CPacket.TYPE)) {
            return;
        }
        RoadPresetEditorService service = new RoadPresetEditorService(
                RoadPresetResources.repository(),
                actor -> actor.equals(player.getUUID()) && player.isCreative()
        );
        RoadPresetEditorService.EditResult editResult = service.delete(
                player.getUUID(),
                packet.presetId(),
                packet.expectedRevision()
        );
        PresetSaveResultS2CPacket result;
        if (editResult instanceof
                RoadPresetEditorService.EditResult.Accepted accepted) {
            persistRepository(player);
            result = PresetSaveResultS2CPacket.accepted(
                    packet.requestId(),
                    accepted.catalog().revision(),
                    0,
                    ""
            );
            broadcastCatalog(player.server);
        } else {
            RoadPresetEditorService.EditFailure failure =
                    ((RoadPresetEditorService.EditResult.Rejected) editResult).failure();
            result = PresetSaveResultS2CPacket.rejected(
                    packet.requestId(),
                    failure == RoadPresetEditorService.EditFailure.STALE_REVISION
                            ? PresetSaveResultCode.STALE_REVISION
                            : PresetSaveResultCode.NOT_AUTHORIZED
            );
        }
        RoadServerTransport.send(player, result);
    }

    static void sendCatalog(ServerPlayer player) {
        if (RoadServerTransport.canSend(player, PresetCatalogS2CPacket.TYPE)) {
            RoadServerTransport.send(
                    player,
                    PresetCatalogS2CPacket.current(
                            RoadPresetResources.repository().catalog()
                    )
            );
        }
    }

    static void broadcastCatalog(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        server.getPlayerList().getPlayers().forEach(RoadPresetNetworking::sendCatalog);
    }

    private static void persistRepository(ServerPlayer player) {
        RoadPresetSavedData.get(player.server.overworld())
                .replaceFrom(RoadPresetResources.repository());
    }

    private static PresetSaveResultCode resultCode(
            RoadPresetEditorService.EditFailure failure
    ) {
        return switch (failure) {
            case NOT_AUTHORIZED -> PresetSaveResultCode.NOT_AUTHORIZED;
            case STALE_REVISION, INVALID_REVISION ->
                    PresetSaveResultCode.STALE_REVISION;
            case UNKNOWN_BLOCK -> PresetSaveResultCode.UNKNOWN_BLOCK;
            case PAYLOAD_LIMIT -> PresetSaveResultCode.PAYLOAD_LIMIT;
            case INVALID_PRESET -> PresetSaveResultCode.INVALID_PRESET;
        };
    }
}

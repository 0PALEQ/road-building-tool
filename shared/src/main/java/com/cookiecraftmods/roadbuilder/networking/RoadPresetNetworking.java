package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.ServerOperatorPermissions;
import com.cookiecraftmods.roadbuilder.networking.RoadPackImportResultS2CPacket.Code;
import com.cookiecraftmods.roadbuilder.persistence.RoadPresetSavedData;
import com.cookiecraftmods.roadbuilder.preset.RoadPackArchive;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetEditorService;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetResources;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

final class RoadPresetNetworking {
    private static final int IMPORT_OPERATOR_LEVEL = 2;

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
                RoadPresetNetworking::knownBlock
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
            broadcastCatalog(player.serverLevel().getServer());
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

    static void receiveRoadPackImport(RoadPackImportC2SPacket packet, ServerPlayer player) {
        if (RoadServerTransport.canSend(player, RoadPackImportResultS2CPacket.TYPE)) {
            RoadServerTransport.send(player, importRoadPack(packet, player));
        }
    }

    private static RoadPackImportResultS2CPacket importRoadPack(
            RoadPackImportC2SPacket packet,
            ServerPlayer player
    ) {
        if (!ServerOperatorPermissions.hasLevel(player, IMPORT_OPERATOR_LEVEL)) {
            return RoadPackImportResultS2CPacket.status(packet.requestId(), Code.NOT_AUTHORIZED);
        }
        if (packet.checksPermission()) {
            return RoadPackImportResultS2CPacket.status(packet.requestId(), Code.AVAILABLE);
        }
        Map<ResourceLocation, RoadPreset> presets;
        try {
            presets = RoadPackArchive.read(packet.archive());
        } catch (IOException | RuntimeException invalid) {
            return RoadPackImportResultS2CPacket.status(packet.requestId(), Code.INVALID_PACK);
        }

        RoadPresetEditorService service = new RoadPresetEditorService(
                RoadPresetResources.repository(),
                actor -> actor.equals(player.getUUID())
                        && ServerOperatorPermissions.hasLevel(player, IMPORT_OPERATOR_LEVEL),
                RoadPresetNetworking::knownBlock
        );
        var result = service.importPack(player.getUUID(), presets, RoadPresetPacketCodecs.MAXIMUM_CATALOG_ENTRIES);
        if (result instanceof RoadPresetEditorService.ImportResult.Accepted accepted) {
            if (accepted.imported() > 0) {
                persistRepository(player);
                broadcastCatalog(player.serverLevel().getServer());
            }
            return RoadPackImportResultS2CPacket.imported(packet.requestId(), accepted.imported(), accepted.skipped());
        }
        Code code = switch (((RoadPresetEditorService.ImportResult.Rejected) result).failure()) {
            case NOT_AUTHORIZED -> Code.NOT_AUTHORIZED;
            case UNKNOWN_BLOCK -> Code.UNKNOWN_BLOCK;
            case PAYLOAD_LIMIT -> Code.CATALOG_FULL;
            default -> Code.INVALID_PACK;
        };
        return RoadPackImportResultS2CPacket.status(packet.requestId(), code);
    }

    private static boolean knownBlock(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.containsKey(id)
                && !BuiltInRegistries.BLOCK.get(id).defaultBlockState().isAir();
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
            broadcastCatalog(player.serverLevel().getServer());
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
        RoadPresetSavedData.get(player.serverLevel().getServer().overworld())
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

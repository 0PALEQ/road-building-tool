package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record RemovalIntentC2SPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation dimensionId,
        UUID operationId,
        RemovalRequestAction action,
        RemovalMode mode
) implements RoadPacket {
    public static final int CURRENT_PROTOCOL_VERSION = PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_ID_CHARACTERS = PacketValidation.MAX_RESOURCE_ID_CHARACTERS;
    public static final int MAX_PAYLOAD_BYTES = 192;

    public static final RoadPacketType<RemovalIntentC2SPacket> TYPE = RoadPacketType.create(
            MinecraftIds.id("roadbuilder", "removal_intent_v1"),
            RemovalIntentC2SPacket::new
    );

    public RemovalIntentC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        PacketValidation.requireBoundedId(dimensionId, "dimensionId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(mode, "mode");
    }

    public RemovalIntentC2SPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static RemovalIntentC2SPacket current(
            UUID requestId,
            ResourceLocation dimensionId,
            UUID operationId,
            RemovalRequestAction action,
            RemovalMode mode
    ) {
        return new RemovalIntentC2SPacket(
                CURRENT_PROTOCOL_VERSION,
                requestId,
                dimensionId,
                operationId,
                action,
                mode
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        PacketValidation.writeBoundedId(buffer, dimensionId);
        buffer.writeUUID(operationId);
        buffer.writeVarInt(action.wireId());
        buffer.writeVarInt(RemovalPacketCodecs.modeWireId(mode));
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }

    private RemovalIntentC2SPacket(WireFields fields) {
        this(
                fields.protocolVersion,
                fields.requestId,
                fields.dimensionId,
                fields.operationId,
                fields.action,
                fields.mode
        );
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(buffer, MAX_PAYLOAD_BYTES, "removal intent");
        int protocolVersion = buffer.readVarInt();
        UUID requestId = buffer.readUUID();
        ResourceLocation dimensionId = PacketValidation.readBoundedId(buffer, "dimensionId");
        UUID operationId = buffer.readUUID();
        int actionWireId = buffer.readVarInt();
        RemovalRequestAction action = RemovalRequestAction.fromWireId(actionWireId)
                .orElseThrow(() -> new IllegalArgumentException("unknown removal action " + actionWireId));
        RemovalMode mode = RemovalPacketCodecs.modeFromWireId(buffer.readVarInt());
        PacketValidation.requireFullyRead(buffer, "removal intent");
        return new WireFields(protocolVersion, requestId, dimensionId, operationId, action, mode);
    }

    private record WireFields(
            int protocolVersion,
            UUID requestId,
            ResourceLocation dimensionId,
            UUID operationId,
            RemovalRequestAction action,
            RemovalMode mode
    ) {
    }
}

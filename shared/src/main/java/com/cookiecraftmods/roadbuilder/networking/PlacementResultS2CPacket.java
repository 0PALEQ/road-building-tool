package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PlacementResultS2CPacket(
        int protocolVersion,
        UUID requestId,
        PlacementResultCode resultCode,
        Optional<AcceptedPlacement> acceptedPlacement
) implements RoadPacket {
    public static final int CURRENT_PROTOCOL_VERSION = PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 64;

    public static final RoadPacketType<PlacementResultS2CPacket> TYPE = RoadPacketType.create(
            MinecraftIds.id("roadbuilder", "placement_result_v1"),
            PlacementResultS2CPacket::new
    );

    public PlacementResultS2CPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(resultCode, "resultCode");
        Objects.requireNonNull(acceptedPlacement, "acceptedPlacement");

        boolean accepted = resultCode == PlacementResultCode.ACCEPTED;
        if (accepted != acceptedPlacement.isPresent()) {
            throw new IllegalArgumentException(
                    "acceptedPlacement must be present exactly when resultCode is ACCEPTED"
            );
        }
    }

    public PlacementResultS2CPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static PlacementResultS2CPacket accepted(
            UUID requestId,
            UUID operationId,
            int affectedBlockCount
    ) {
        return new PlacementResultS2CPacket(
                CURRENT_PROTOCOL_VERSION,
                requestId,
                PlacementResultCode.ACCEPTED,
                Optional.of(new AcceptedPlacement(operationId, affectedBlockCount))
        );
    }

    public static PlacementResultS2CPacket queued(
            UUID requestId,
            UUID jobId,
            int affectedBlockCount
    ) {
        return new PlacementResultS2CPacket(
                CURRENT_PROTOCOL_VERSION,
                requestId,
                PlacementResultCode.ACCEPTED,
                Optional.of(new AcceptedPlacement(
                        jobId,
                        affectedBlockCount,
                        true
                ))
        );
    }

    public static PlacementResultS2CPacket rejected(UUID requestId, PlacementResultCode resultCode) {
        Objects.requireNonNull(resultCode, "resultCode");
        if (resultCode == PlacementResultCode.ACCEPTED) {
            throw new IllegalArgumentException("use accepted(...) for an accepted result");
        }
        return new PlacementResultS2CPacket(
                CURRENT_PROTOCOL_VERSION,
                requestId,
                resultCode,
                Optional.empty()
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        buffer.writeVarInt(resultCode.wireId());
        acceptedPlacement.ifPresent(accepted -> {
            buffer.writeUUID(accepted.operationId());
            buffer.writeVarInt(accepted.affectedBlockCount());
            buffer.writeBoolean(accepted.queued());
        });
    }

    @Override
    public RoadPacketType<?> packetType() {
        return TYPE;
    }

    private PlacementResultS2CPacket(WireFields fields) {
        this(fields.protocolVersion, fields.requestId, fields.resultCode, fields.acceptedPlacement);
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(buffer, MAX_PAYLOAD_BYTES, "placement result");
        int protocolVersion = buffer.readVarInt();
        UUID requestId = buffer.readUUID();
        int resultWireId = buffer.readVarInt();
        PlacementResultCode resultCode = PlacementResultCode.fromWireId(resultWireId)
                .orElseThrow(() -> new IllegalArgumentException("unknown placement result code " + resultWireId));

        Optional<AcceptedPlacement> acceptedPlacement = Optional.empty();
        if (resultCode == PlacementResultCode.ACCEPTED) {
            acceptedPlacement = Optional.of(new AcceptedPlacement(
                    buffer.readUUID(),
                    buffer.readVarInt(),
                    buffer.readBoolean()
            ));
        }

        PacketValidation.requireFullyRead(buffer, "placement result");
        return new WireFields(protocolVersion, requestId, resultCode, acceptedPlacement);
    }

    private record WireFields(
            int protocolVersion,
            UUID requestId,
            PlacementResultCode resultCode,
            Optional<AcceptedPlacement> acceptedPlacement
    ) {
    }
}

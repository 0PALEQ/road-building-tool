package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record ConstructionJobCancelC2SPacket(
        int protocolVersion,
        UUID requestId,
        ResourceLocation dimensionId,
        UUID jobId
) implements FabricPacket {
    public static final int CURRENT_PROTOCOL_VERSION = PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 160;

    public static final PacketType<ConstructionJobCancelC2SPacket> TYPE =
            PacketType.create(
                    new ResourceLocation("roadbuilder", "construction_job_cancel_v1"),
                    ConstructionJobCancelC2SPacket::new
            );

    public ConstructionJobCancelC2SPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        PacketValidation.requireBoundedId(dimensionId, "dimensionId");
        Objects.requireNonNull(jobId, "jobId");
    }

    public ConstructionJobCancelC2SPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static ConstructionJobCancelC2SPacket current(
            UUID requestId,
            ResourceLocation dimensionId,
            UUID jobId
    ) {
        return new ConstructionJobCancelC2SPacket(
                CURRENT_PROTOCOL_VERSION,
                requestId,
                dimensionId,
                jobId
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        PacketValidation.writeBoundedId(buffer, dimensionId);
        buffer.writeUUID(jobId);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    private ConstructionJobCancelC2SPacket(WireFields fields) {
        this(
                fields.protocolVersion,
                fields.requestId,
                fields.dimensionId,
                fields.jobId
        );
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(
                buffer,
                MAX_PAYLOAD_BYTES,
                "construction job cancellation"
        );
        WireFields fields = new WireFields(
                buffer.readVarInt(),
                buffer.readUUID(),
                PacketValidation.readBoundedId(buffer, "dimensionId"),
                buffer.readUUID()
        );
        PacketValidation.requireFullyRead(buffer, "construction job cancellation");
        return fields;
    }

    private record WireFields(
            int protocolVersion,
            UUID requestId,
            ResourceLocation dimensionId,
            UUID jobId
    ) {
    }
}

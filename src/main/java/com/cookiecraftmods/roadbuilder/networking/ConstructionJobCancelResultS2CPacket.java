package com.cookiecraftmods.roadbuilder.networking;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record ConstructionJobCancelResultS2CPacket(
        int protocolVersion,
        UUID requestId,
        UUID jobId,
        ConstructionJobCancelResultCode resultCode
) implements FabricPacket {
    public static final int CURRENT_PROTOCOL_VERSION = PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 64;

    public static final PacketType<ConstructionJobCancelResultS2CPacket> TYPE =
            PacketType.create(
                    new ResourceLocation(
                            "roadbuilder",
                            "construction_job_cancel_result_v1"
                    ),
                    ConstructionJobCancelResultS2CPacket::new
            );

    public ConstructionJobCancelResultS2CPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(resultCode, "resultCode");
    }

    public ConstructionJobCancelResultS2CPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static ConstructionJobCancelResultS2CPacket current(
            ConstructionJobCancelC2SPacket request,
            ConstructionJobCancelResultCode resultCode
    ) {
        Objects.requireNonNull(request, "request");
        return new ConstructionJobCancelResultS2CPacket(
                CURRENT_PROTOCOL_VERSION,
                request.requestId(),
                request.jobId(),
                resultCode
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        buffer.writeUUID(jobId);
        buffer.writeVarInt(resultCode.wireId());
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    private ConstructionJobCancelResultS2CPacket(WireFields fields) {
        this(
                fields.protocolVersion,
                fields.requestId,
                fields.jobId,
                fields.resultCode
        );
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(
                buffer,
                MAX_PAYLOAD_BYTES,
                "construction job cancellation result"
        );
        int protocolVersion = buffer.readVarInt();
        UUID requestId = buffer.readUUID();
        UUID jobId = buffer.readUUID();
        int resultWireId = buffer.readVarInt();
        ConstructionJobCancelResultCode resultCode =
                ConstructionJobCancelResultCode.fromWireId(resultWireId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "unknown construction job cancel result " + resultWireId
                        ));
        PacketValidation.requireFullyRead(
                buffer,
                "construction job cancellation result"
        );
        return new WireFields(protocolVersion, requestId, jobId, resultCode);
    }

    private record WireFields(
            int protocolVersion,
            UUID requestId,
            UUID jobId,
            ConstructionJobCancelResultCode resultCode
    ) {
    }
}

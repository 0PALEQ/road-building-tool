package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import com.cookiecraftmods.roadbuilder.placement.RemovalSummary;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RemovalResultS2CPacket(
        int protocolVersion,
        UUID requestId,
        UUID operationId,
        RemovalRequestAction action,
        RemovalMode mode,
        RemovalResultCode resultCode,
        Optional<RemovalResultSummary> summary
) implements FabricPacket {
    public static final int CURRENT_PROTOCOL_VERSION = PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 256;

    public static final PacketType<RemovalResultS2CPacket> TYPE = PacketType.create(
            new ResourceLocation("roadbuilder", "removal_result_v1"),
            RemovalResultS2CPacket::new
    );

    public RemovalResultS2CPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(resultCode, "resultCode");
        Objects.requireNonNull(summary, "summary");
        if (hasSummary(resultCode) != summary.isPresent()) {
            throw new IllegalArgumentException(
                    "summary must be present exactly for successful or queued removal"
            );
        }
    }

    public RemovalResultS2CPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static RemovalResultS2CPacket success(
            RemovalIntentC2SPacket request,
            RemovalSummary summary
    ) {
        Objects.requireNonNull(request, "request");
        return new RemovalResultS2CPacket(
                CURRENT_PROTOCOL_VERSION,
                request.requestId(),
                request.operationId(),
                request.action(),
                request.mode(),
                RemovalResultCode.SUCCESS,
                Optional.of(RemovalResultSummary.from(summary))
        );
    }

    public static RemovalResultS2CPacket queued(
            RemovalIntentC2SPacket request,
            RemovalSummary summary
    ) {
        Objects.requireNonNull(request, "request");
        return new RemovalResultS2CPacket(
                CURRENT_PROTOCOL_VERSION,
                request.requestId(),
                request.operationId(),
                request.action(),
                request.mode(),
                RemovalResultCode.QUEUED,
                Optional.of(RemovalResultSummary.from(summary))
        );
    }

    public static RemovalResultS2CPacket rejected(
            RemovalIntentC2SPacket request,
            RemovalResultCode resultCode
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(resultCode, "resultCode");
        if (resultCode == RemovalResultCode.SUCCESS) {
            throw new IllegalArgumentException("use success(...) for a successful result");
        }
        return new RemovalResultS2CPacket(
                CURRENT_PROTOCOL_VERSION,
                request.requestId(),
                request.operationId(),
                request.action(),
                request.mode(),
                resultCode,
                Optional.empty()
        );
    }

    public static RemovalResultS2CPacket rejected(
            SegmentRemovalIntentC2SPacket request,
            RemovalResultCode resultCode
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(resultCode, "resultCode");
        if (resultCode == RemovalResultCode.SUCCESS) {
            throw new IllegalArgumentException("use success(...) for a successful result");
        }
        return new RemovalResultS2CPacket(
                CURRENT_PROTOCOL_VERSION,
                request.requestId(),
                request.segmentId(),
                request.action(),
                request.mode(),
                resultCode,
                Optional.empty()
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(protocolVersion);
        buffer.writeUUID(requestId);
        buffer.writeUUID(operationId);
        buffer.writeVarInt(action.wireId());
        buffer.writeVarInt(RemovalPacketCodecs.modeWireId(mode));
        buffer.writeVarInt(resultCode.wireId());
        summary.ifPresent(value -> value.write(buffer));
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    private RemovalResultS2CPacket(WireFields fields) {
        this(
                fields.protocolVersion,
                fields.requestId,
                fields.operationId,
                fields.action,
                fields.mode,
                fields.resultCode,
                fields.summary
        );
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(buffer, MAX_PAYLOAD_BYTES, "removal result");
        int protocolVersion = buffer.readVarInt();
        UUID requestId = buffer.readUUID();
        UUID operationId = buffer.readUUID();
        int actionWireId = buffer.readVarInt();
        RemovalRequestAction action = RemovalRequestAction.fromWireId(actionWireId)
                .orElseThrow(() -> new IllegalArgumentException("unknown removal action " + actionWireId));
        RemovalMode mode = RemovalPacketCodecs.modeFromWireId(buffer.readVarInt());
        int resultWireId = buffer.readVarInt();
        RemovalResultCode resultCode = RemovalResultCode.fromWireId(resultWireId)
                .orElseThrow(() -> new IllegalArgumentException("unknown removal result code " + resultWireId));
        Optional<RemovalResultSummary> summary = hasSummary(resultCode)
                ? Optional.of(RemovalResultSummary.read(buffer))
                : Optional.empty();
        PacketValidation.requireFullyRead(buffer, "removal result");
        return new WireFields(
                protocolVersion,
                requestId,
                operationId,
                action,
                mode,
                resultCode,
                summary
        );
    }

    private record WireFields(
            int protocolVersion,
            UUID requestId,
            UUID operationId,
            RemovalRequestAction action,
            RemovalMode mode,
            RemovalResultCode resultCode,
            Optional<RemovalResultSummary> summary
    ) {
    }

    private static boolean hasSummary(RemovalResultCode resultCode) {
        return resultCode == RemovalResultCode.SUCCESS
                || resultCode == RemovalResultCode.QUEUED;
    }
}

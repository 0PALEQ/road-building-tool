package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJob;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobAction;
import com.cookiecraftmods.roadbuilder.placement.ConstructionJobPhase;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ConstructionJobProgressS2CPacket(
        int protocolVersion,
        ResourceLocation dimensionId,
        UUID jobId,
        ConstructionJobAction action,
        ConstructionJobPhase phase,
        int completedTransitions,
        int totalTransitions,
        Optional<BlockPos> conflictPosition
) implements FabricPacket {
    public static final int CURRENT_PROTOCOL_VERSION = PacketValidation.PROTOCOL_VERSION;
    public static final int MAX_PAYLOAD_BYTES = 128;

    public static final PacketType<ConstructionJobProgressS2CPacket> TYPE =
            PacketType.create(
                    new ResourceLocation("roadbuilder", "construction_job_progress_v1"),
                    ConstructionJobProgressS2CPacket::new
            );

    public ConstructionJobProgressS2CPacket {
        PacketValidation.requireProtocolVersion(protocolVersion);
        PacketValidation.requireBoundedId(dimensionId, "dimensionId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(phase, "phase");
        if (totalTransitions < 1
                || totalTransitions
                > RoadBuilderConstructionJobSavedData.MAXIMUM_TRANSITIONS_PER_JOB) {
            throw new IllegalArgumentException("totalTransitions is outside job limits");
        }
        if (completedTransitions < 0 || completedTransitions > totalTransitions) {
            throw new IllegalArgumentException(
                    "completedTransitions must be between zero and totalTransitions"
            );
        }
        conflictPosition = Objects.requireNonNull(
                conflictPosition,
                "conflictPosition"
        ).map(BlockPos::immutable);
        if (phase != ConstructionJobPhase.QUARANTINED
                && conflictPosition.isPresent()) {
            throw new IllegalArgumentException(
                    "only quarantined jobs may include a conflict position"
            );
        }
    }

    public ConstructionJobProgressS2CPacket(FriendlyByteBuf buffer) {
        this(decode(buffer));
    }

    public static ConstructionJobProgressS2CPacket current(
            ResourceLocation dimensionId,
            ConstructionJob job
    ) {
        Objects.requireNonNull(job, "job");
        return new ConstructionJobProgressS2CPacket(
                CURRENT_PROTOCOL_VERSION,
                dimensionId,
                job.id(),
                job.action(),
                job.phase(),
                job.cursor(),
                job.transitions().size(),
                job.conflictPosition()
        );
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        Objects.requireNonNull(buffer, "buffer");
        buffer.writeVarInt(protocolVersion);
        PacketValidation.writeBoundedId(buffer, dimensionId);
        buffer.writeUUID(jobId);
        buffer.writeVarInt(ConstructionJobPacketCodecs.actionWireId(action));
        buffer.writeVarInt(ConstructionJobPacketCodecs.phaseWireId(phase));
        buffer.writeVarInt(completedTransitions);
        buffer.writeVarInt(totalTransitions);
        buffer.writeBoolean(conflictPosition.isPresent());
        conflictPosition.ifPresent(buffer::writeBlockPos);
    }

    @Override
    public PacketType<?> getType() {
        return TYPE;
    }

    private ConstructionJobProgressS2CPacket(WireFields fields) {
        this(
                fields.protocolVersion,
                fields.dimensionId,
                fields.jobId,
                fields.action,
                fields.phase,
                fields.completedTransitions,
                fields.totalTransitions,
                fields.conflictPosition
        );
    }

    private static WireFields decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(
                buffer,
                MAX_PAYLOAD_BYTES,
                "construction job progress"
        );
        int protocolVersion = buffer.readVarInt();
        ResourceLocation dimensionId = PacketValidation.readBoundedId(
                buffer,
                "dimensionId"
        );
        UUID jobId = buffer.readUUID();
        ConstructionJobAction action =
                ConstructionJobPacketCodecs.actionFromWireId(buffer.readVarInt());
        ConstructionJobPhase phase =
                ConstructionJobPacketCodecs.phaseFromWireId(buffer.readVarInt());
        int completedTransitions = buffer.readVarInt();
        int totalTransitions = buffer.readVarInt();
        Optional<BlockPos> conflictPosition = buffer.readBoolean()
                ? Optional.of(buffer.readBlockPos())
                : Optional.empty();
        PacketValidation.requireFullyRead(buffer, "construction job progress");
        return new WireFields(
                protocolVersion,
                dimensionId,
                jobId,
                action,
                phase,
                completedTransitions,
                totalTransitions,
                conflictPosition
        );
    }

    private record WireFields(
            int protocolVersion,
            ResourceLocation dimensionId,
            UUID jobId,
            ConstructionJobAction action,
            ConstructionJobPhase phase,
            int completedTransitions,
            int totalTransitions,
            Optional<BlockPos> conflictPosition
    ) {
    }
}

package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.placement.RemovalSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record RemovalResultSummary(
        int positionCount,
        int restoredCount,
        int unchangedCount,
        int shadowedCount,
        int conflictCount,
        int forcedConflictCount,
        List<BlockPos> conflictSamples
) {
    public static final int MAX_POSITION_COUNT = AcceptedPlacement.MAX_AFFECTED_BLOCK_COUNT;
    public static final int MAX_CONFLICT_SAMPLES = 16;

    public RemovalResultSummary {
        if (positionCount <= 0 || positionCount > MAX_POSITION_COUNT) {
            throw new IllegalArgumentException(
                    "positionCount must be between 1 and " + MAX_POSITION_COUNT
            );
        }
        if (restoredCount < 0 || unchangedCount < 0 || shadowedCount < 0
                || conflictCount < 0 || forcedConflictCount < 0) {
            throw new IllegalArgumentException("removal counts must be non-negative");
        }
        if (restoredCount > MAX_POSITION_COUNT || unchangedCount > MAX_POSITION_COUNT
                || shadowedCount > MAX_POSITION_COUNT || conflictCount > MAX_POSITION_COUNT
                || forcedConflictCount > MAX_POSITION_COUNT) {
            throw new IllegalArgumentException("removal counts exceed the position-count limit");
        }
        long accountedPositions = (long) restoredCount + unchangedCount + shadowedCount + conflictCount;
        if (positionCount != accountedPositions) {
            throw new IllegalArgumentException("removal counts must cover the operation footprint");
        }
        if (forcedConflictCount > (long) restoredCount + unchangedCount) {
            throw new IllegalArgumentException("forced conflicts must be visible removal outcomes");
        }

        List<BlockPos> immutableSamples = new ArrayList<>(
                Objects.requireNonNull(conflictSamples, "conflictSamples").size()
        );
        Set<BlockPos> uniqueSamples = new HashSet<>();
        for (BlockPos suppliedPosition : conflictSamples) {
            BlockPos position = Objects.requireNonNull(
                    suppliedPosition,
                    "conflictSamples contains null"
            ).immutable();
            if (!uniqueSamples.add(position)) {
                throw new IllegalArgumentException("conflictSamples contains a duplicate position");
            }
            immutableSamples.add(position);
        }
        conflictSamples = List.copyOf(immutableSamples);
        if (conflictSamples.size() > MAX_CONFLICT_SAMPLES || conflictSamples.size() > conflictCount) {
            throw new IllegalArgumentException("conflictSamples exceeds its bounded conflict count");
        }
    }

    public static RemovalResultSummary from(RemovalSummary summary) {
        Objects.requireNonNull(summary, "summary");
        List<BlockPos> samples = summary.conflictPositions().stream()
                .limit(MAX_CONFLICT_SAMPLES)
                .toList();
        return new RemovalResultSummary(
                summary.positionCount(),
                summary.restoredCount(),
                summary.unchangedCount(),
                summary.shadowedCount(),
                summary.conflictCount(),
                summary.forcedConflictCount(),
                samples
        );
    }

    public boolean conflictSamplesTruncated() {
        return conflictSamples.size() < conflictCount;
    }

    void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(positionCount);
        buffer.writeVarInt(restoredCount);
        buffer.writeVarInt(unchangedCount);
        buffer.writeVarInt(shadowedCount);
        buffer.writeVarInt(conflictCount);
        buffer.writeVarInt(forcedConflictCount);
        buffer.writeVarInt(conflictSamples.size());
        conflictSamples.forEach(position -> buffer.writeLong(position.asLong()));
    }

    static RemovalResultSummary read(FriendlyByteBuf buffer) {
        int positionCount = buffer.readVarInt();
        int restoredCount = buffer.readVarInt();
        int unchangedCount = buffer.readVarInt();
        int shadowedCount = buffer.readVarInt();
        int conflictCount = buffer.readVarInt();
        int forcedConflictCount = buffer.readVarInt();
        int sampleCount = buffer.readVarInt();
        if (sampleCount < 0 || sampleCount > MAX_CONFLICT_SAMPLES) {
            throw new IllegalArgumentException(
                    "conflict sample count must be between 0 and " + MAX_CONFLICT_SAMPLES
            );
        }
        List<BlockPos> conflictSamples = new ArrayList<>(sampleCount);
        for (int index = 0; index < sampleCount; index++) {
            conflictSamples.add(BlockPos.of(buffer.readLong()));
        }
        return new RemovalResultSummary(
                positionCount,
                restoredCount,
                unchangedCount,
                shadowedCount,
                conflictCount,
                forcedConflictCount,
                conflictSamples
        );
    }
}

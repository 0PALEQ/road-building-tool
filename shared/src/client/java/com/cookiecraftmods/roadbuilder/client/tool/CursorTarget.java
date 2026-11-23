package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import net.minecraft.core.BlockPos;

import java.util.Objects;

public record CursorTarget(BlockPos blockPosition, RoadPoint roadPoint) {
    public CursorTarget {
        blockPosition = Objects.requireNonNull(blockPosition, "blockPosition").immutable();
        Objects.requireNonNull(roadPoint, "roadPoint");
    }

    public static CursorTarget at(BlockPos blockPosition) {
        BlockPos immutablePosition = Objects.requireNonNull(blockPosition, "blockPosition").immutable();
        return new CursorTarget(
                immutablePosition,
                new RoadPoint(
                        immutablePosition.getX() + 0.5,
                        immutablePosition.getY(),
                        immutablePosition.getZ() + 0.5
                )
        );
    }
}

package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.Objects;

public record ConstructionBounds(BlockPos minimum, BlockPos maximum) {
    public ConstructionBounds {
        minimum = Objects.requireNonNull(minimum, "minimum").immutable();
        maximum = Objects.requireNonNull(maximum, "maximum").immutable();

        if (minimum.getX() > maximum.getX()
                || minimum.getY() > maximum.getY()
                || minimum.getZ() > maximum.getZ()) {
            throw new IllegalArgumentException("minimum must not exceed maximum on any axis");
        }
    }

    public static ConstructionBounds at(BlockPos position) {
        BlockPos immutablePosition = Objects.requireNonNull(position, "position").immutable();
        return new ConstructionBounds(immutablePosition, immutablePosition);
    }

        public AABB asBox() {
        return new AABB(
                minimum.getX(),
                minimum.getY(),
                minimum.getZ(),
                maximum.getX() + 1.0,
                maximum.getY() + 1.0,
                maximum.getZ() + 1.0
        );
    }

    public int minimumChunkX() {
        return Math.floorDiv(minimum.getX(), 16);
    }

    public int maximumChunkX() {
        return Math.floorDiv(maximum.getX(), 16);
    }

    public int minimumChunkZ() {
        return Math.floorDiv(minimum.getZ(), 16);
    }

    public int maximumChunkZ() {
        return Math.floorDiv(maximum.getZ(), 16);
    }

    public long coveredChunkCount() {
        long width = (long) maximumChunkX() - minimumChunkX() + 1L;
        long depth = (long) maximumChunkZ() - minimumChunkZ() + 1L;
        return width * depth;
    }

        public double maximumSquaredDistanceFrom(double x, double y, double z) {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");

        double dx = farthestAxisDistance(x, minimum.getX() + 0.5, maximum.getX() + 0.5);
        double dy = farthestAxisDistance(y, minimum.getY() + 0.5, maximum.getY() + 0.5);
        double dz = farthestAxisDistance(z, minimum.getZ() + 0.5, maximum.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double farthestAxisDistance(double origin, double minimum, double maximum) {
        return Math.max(Math.abs(origin - minimum), Math.abs(origin - maximum));
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}

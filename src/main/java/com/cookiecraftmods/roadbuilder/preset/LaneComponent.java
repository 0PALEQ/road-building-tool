package com.cookiecraftmods.roadbuilder.preset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

public record LaneComponent(
        LaneType type,
        double width,
        double lateralOffset,
        LaneTravelDirection direction
) {
    private static final Codec<Serialized> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            LaneType.CODEC.fieldOf("type").forGetter(Serialized::type),
            Codec.DOUBLE.fieldOf("width").forGetter(Serialized::width),
            Codec.DOUBLE.fieldOf("offset").forGetter(Serialized::lateralOffset),
            LaneTravelDirection.CODEC.fieldOf("direction").forGetter(Serialized::direction)
    ).apply(instance, Serialized::new));

    public static final Codec<LaneComponent> CODEC = SERIALIZED_CODEC.comapFlatMap(
            LaneComponent::decode,
            lane -> new Serialized(lane.type, lane.width, lane.lateralOffset, lane.direction)
    );

    public LaneComponent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(direction, "direction");
        if (!Double.isFinite(width) || width <= 0.0) {
            throw new IllegalArgumentException("lane width must be finite and greater than zero");
        }
        if (!Double.isFinite(lateralOffset)) {
            throw new IllegalArgumentException("lane lateral offset must be finite");
        }
        lateralOffset = canonicalizeZero(lateralOffset);
    }

        public LaneComponent forTrafficHandedness(TrafficHandedness handedness) {
        Objects.requireNonNull(handedness, "handedness");
        double resolvedOffset = handedness.resolveOffset(lateralOffset);
        if (resolvedOffset == lateralOffset) {
            return this;
        }
        return new LaneComponent(type, width, resolvedOffset, direction);
    }

    public double leftEdge() {
        return lateralOffset + width / 2.0;
    }

    public double rightEdge() {
        return lateralOffset - width / 2.0;
    }

    private static DataResult<LaneComponent> decode(Serialized serialized) {
        try {
            return DataResult.success(new LaneComponent(
                    serialized.type,
                    serialized.width,
                    serialized.lateralOffset,
                    serialized.direction
            ));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static double canonicalizeZero(double value) {
        return value == 0.0 ? 0.0 : value;
    }

    private record Serialized(
            LaneType type,
            double width,
            double lateralOffset,
            LaneTravelDirection direction
    ) {
    }
}

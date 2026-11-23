package com.cookiecraftmods.roadbuilder.client.camera;

public final class CityBuilderCameraMath {
    public static final double MIN_DISTANCE = 4.0;
    public static final double MAX_DISTANCE = 96.0;
    public static final float MIN_PITCH = 30.0F;
    public static final float MAX_PITCH = 80.0F;

    private CityBuilderCameraMath() {
    }

    public static double horizontalMovementAxis(
            boolean rightKeyDown,
            boolean leftKeyDown,
            boolean inverted
    ) {
        double axis = (rightKeyDown ? 1.0 : 0.0) - (leftKeyDown ? 1.0 : 0.0);
        return inverted ? -axis : axis;
    }

    public static HorizontalDelta horizontalDelta(
            float yawDegrees,
            double forwardAxis,
            double rightAxis,
            double blocksPerTick
    ) {
        if (!Double.isFinite(forwardAxis)
                || !Double.isFinite(rightAxis)
                || !Double.isFinite(blocksPerTick)
                || blocksPerTick < 0.0) {
            throw new IllegalArgumentException("Camera movement inputs must be finite");
        }

        double length = Math.hypot(forwardAxis, rightAxis);
        if (length <= 1.0E-9 || blocksPerTick == 0.0) {
            return HorizontalDelta.ZERO;
        }
        double normalizedForward = forwardAxis / Math.max(1.0, length);
        double normalizedRight = rightAxis / Math.max(1.0, length);
        double yawRadians = Math.toRadians(yawDegrees);
        double sinYaw = Math.sin(yawRadians);
        double cosYaw = Math.cos(yawRadians);
        return new HorizontalDelta(
                (-sinYaw * normalizedForward + cosYaw * normalizedRight) * blocksPerTick,
                (cosYaw * normalizedForward + sinYaw * normalizedRight) * blocksPerTick
        );
    }

    public static double panSpeed(double cameraDistance, boolean fast) {
        double clampedDistance = clamp(cameraDistance, MIN_DISTANCE, MAX_DISTANCE);
        double normalSpeed = 0.10 + clampedDistance * 0.01;
        return normalSpeed * (fast ? 3.0 : 1.0);
    }

    public static double zoomedDistance(double currentDistance, double scrollSteps) {
        if (!Double.isFinite(currentDistance) || !Double.isFinite(scrollSteps)) {
            throw new IllegalArgumentException("Camera zoom inputs must be finite");
        }
        return clamp(
                currentDistance * Math.exp(-scrollSteps * 0.12),
                MIN_DISTANCE,
                MAX_DISTANCE
        );
    }

    public static float rotatedYaw(
            float currentYaw,
            double cursorDeltaX,
            double degreesPerPixel,
            boolean inverted
    ) {
        double direction = inverted ? 1.0 : -1.0;
        return wrapDegrees((float) (
                currentYaw + cursorDeltaX * degreesPerPixel * direction
        ));
    }

    public static float rotatedPitch(
            float currentPitch,
            double cursorDeltaY,
            double degreesPerPixel,
            boolean inverted
    ) {
        double direction = inverted ? -1.0 : 1.0;
        return (float) clamp(
                currentPitch + cursorDeltaY * degreesPerPixel * direction,
                MIN_PITCH,
                MAX_PITCH
        );
    }

    public static double rotationDegreesPerPixel(double minecraftSensitivity) {
        double clampedSensitivity = clamp(minecraftSensitivity, 0.0, 1.0);
        double scaled = clampedSensitivity * 0.6 + 0.2;
        return scaled * scaled * scaled * 0.6;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public record HorizontalDelta(double x, double z) {
        public static final HorizontalDelta ZERO = new HorizontalDelta(0.0, 0.0);

        public HorizontalDelta {
            if (!Double.isFinite(x) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Camera delta must be finite");
            }
        }
    }
}

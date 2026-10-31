package com.cookiecraftmods.roadbuilder.client.render;

import com.cookiecraftmods.roadbuilder.client.networking.RoadBuilderClientNetworking;
import com.cookiecraftmods.roadbuilder.client.tool.RoadSnapSettings;
import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.OffsetGeometry;
import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.MultiCurveGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoundaboutGeometry;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.network.LaneConnector;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorGeometryPlanner;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorId;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorMovement;
import com.cookiecraftmods.roadbuilder.network.RoadLane;
import com.cookiecraftmods.roadbuilder.network.RoadLaneGraph;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RoadLaneDirectionRenderer {
    private static final double SAMPLE_SPACING = 0.75;
    private static final double ARROW_SPACING = 8.0;
    private static final double HALF_PATH_WIDTH = 0.46;
    private static final double CONNECTION_POINT_RADIUS = 0.48;
    private static final int CONNECTION_POINT_SEGMENTS = 24;
    private final double maximumRenderDistanceSquared;
    private RoadLaneGraph cachedGraph;
    private final Map<ReferenceGeometry, AABB> pathBounds = new IdentityHashMap<>();

    public RoadLaneDirectionRenderer() {
        this(RoadSnapSettings.DEFAULT_GUIDELINE_RANGE);
    }

    public RoadLaneDirectionRenderer(double maximumRenderDistance) {
        if (!Double.isFinite(maximumRenderDistance)
                || maximumRenderDistance < 1.0
                || maximumRenderDistance > 4_096.0) {
            throw new IllegalArgumentException(
                    "maximum render distance must be between 1 and 4096"
            );
        }
        maximumRenderDistanceSquared = maximumRenderDistance
                * maximumRenderDistance;
    }

    public void render(WorldRenderContext context, Minecraft client) {
        render(
                context,
                client,
                Optional.empty(),
                Optional.empty(),
                false
        );
    }

    public void render(
            WorldRenderContext context,
            Minecraft client,
            Optional<RoadLaneGraph.ConnectionPoint> selectedPoint,
            Optional<RoadLaneGraph.ConnectionPoint> hoveredPoint
    ) {
        render(context, client, selectedPoint, hoveredPoint, true);
    }

    private void render(
            WorldRenderContext context,
            Minecraft client,
            Optional<RoadLaneGraph.ConnectionPoint> selectedPoint,
            Optional<RoadLaneGraph.ConnectionPoint> hoveredPoint,
            boolean renderConnectionPoints
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(selectedPoint, "selectedPoint");
        Objects.requireNonNull(hoveredPoint, "hoveredPoint");
        if (client.level == null || context.consumers() == null) {
            return;
        }
        var snapshot = RoadBuilderClientNetworking
                .roadGraph(client.level.dimension())
                .orElse(null);
        if (snapshot == null || snapshot.lanes().isEmpty()) {
            cachedGraph = null;
            pathBounds.clear();
            return;
        }
        if (cachedGraph == null || cachedGraph.snapshot() != snapshot) {
            cachedGraph = RoadLaneGraph.from(snapshot);
            pathBounds.clear();
            cachedGraph.lanePaths().values().forEach(path -> pathBounds.put(
                    path.lane().geometry(), geometryBounds(path.lane().geometry())));
            cachedGraph.connectorPaths().values().forEach(path -> path.geometry().ifPresent(
                    geometry -> pathBounds.put(geometry, geometryBounds(geometry))));
        }

        PoseStack matrices = context.matrixStack();
        MultiBufferSource consumers = context.consumers();
        Vec3 camera = context.camera().getPosition();
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        try {
            for (RoadLaneGraph.LanePath lane :
                    cachedGraph.lanePaths().values()) {
                renderPath(matrices, consumers, camera, lane);
            }
            cachedGraph.connectorPaths().values().forEach(path ->
                    path.geometry().ifPresent(geometry -> renderPath(
                            matrices,
                            consumers,
                            camera,
                            geometry,
                            0.18F,
                            0.95F,
                            0.45F,
                            0.28F
                    )));
            VertexConsumer lines = consumers.getBuffer(RenderType.lines());
            for (RoadLaneGraph.LanePath lane :
                    cachedGraph.lanePaths().values()) {
                renderArrows(matrices, lines, camera, lane);
            }
            cachedGraph.connectorPaths().values().forEach(path ->
                    path.geometry().ifPresent(geometry -> renderArrows(
                            matrices,
                            lines,
                            camera,
                            geometry,
                            LaneTravelDirection.FORWARD
                    )));
            if (renderConnectionPoints) {
                renderConnectionPoints(
                        matrices,
                        lines,
                        camera,
                        selectedPoint.orElse(null),
                        hoveredPoint.orElse(null)
                );
                renderConnectorPreview(
                        matrices,
                        consumers,
                        lines,
                        camera,
                        selectedPoint.orElse(null),
                        hoveredPoint.orElse(null)
                );
            }
        } finally {
            matrices.popPose();
        }
    }

    private void renderPath(
            PoseStack matrices,
            MultiBufferSource consumers,
            Vec3 camera,
            RoadLaneGraph.LanePath path
    ) {
        RoadLane lane = path.lane();
        boolean bus = lane.component().type() == LaneType.BUS;
        renderPath(
                matrices,
                consumers,
                camera,
                lane.geometry(),
                path.startDistance(),
                path.endDistance(),
                bus ? 1.0F : 0.12F,
                bus ? 0.58F : 0.78F,
                bus ? 0.12F : 1.0F,
                0.20F
        );
    }

    private void renderPath(
            PoseStack matrices,
            MultiBufferSource consumers,
            Vec3 camera,
            ReferenceGeometry geometry,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        renderPath(
                matrices,
                consumers,
                camera,
                geometry,
                0.0,
                geometry.length(),
                red,
                green,
                blue,
                alpha
        );
    }

    private void renderPath(
            PoseStack matrices,
            MultiBufferSource consumers,
            Vec3 camera,
            ReferenceGeometry geometry,
            double startDistance,
            double endDistance,
            float red,
            float green,
            float blue,
            float alpha
    ) {
        AABB bounds = pathBounds.get(geometry);
        if (bounds != null && !bounds.inflate(Math.sqrt(maximumRenderDistanceSquared)).contains(camera)) {
            return;
        }
        double length = endDistance - startDistance;
        if (length <= 1.0e-9) {
            return;
        }
        int samples = Math.min(
                4_096,
                Math.max(1, (int) Math.ceil(length / SAMPLE_SPACING))
        );
        for (int index = 0; index <= samples; index++) {
            double distance = pathSampleDistance(
                    startDistance,
                    endDistance,
                    index,
                    samples
            );
            var point = geometry.sample(distance).position();
            if (distanceSquared(camera, point.x(), point.y(), point.z())
                    > maximumRenderDistanceSquared) {
                continue;
            }
            DebugRenderer.renderFilledBox(
                    matrices,
                    consumers,
                    new AABB(
                            point.x() - HALF_PATH_WIDTH,
                            point.y() + 1.025,
                            point.z() - HALF_PATH_WIDTH,
                            point.x() + HALF_PATH_WIDTH,
                            point.y() + 1.065,
                            point.z() + HALF_PATH_WIDTH
                    ),
                    red,
                    green,
                    blue,
                    alpha
            );
        }
    }

    static AABB geometryBounds(ReferenceGeometry geometry) {
        if (geometry instanceof OffsetGeometry offset) {
            return geometryBounds(offset.reference()).inflate(Math.abs(offset.lateralOffset()));
        }
        List<RoadPoint> points;
        if (geometry instanceof QuadraticBezierGeometry curve) {
            points = curve.controlPoints();
        } else if (geometry instanceof CubicBezierGeometry curve) {
            points = List.of(curve.start(), curve.startControl(), curve.endControl(), curve.end());
        } else if (geometry instanceof MultiCurveGeometry curve) {
            return curve.spans().stream().map(RoadLaneDirectionRenderer::geometryBounds)
                    .reduce(AABB::minmax).orElseThrow();
        } else if (geometry instanceof RoundaboutGeometry ring) {
            var center = ring.center();
            return new AABB(center.x(), center.y(), center.z(), center.x(), center.y(), center.z())
                    .inflate(ring.radius() + 1.0);
        } else {
            points = List.of(geometry.start(), geometry.end());
        }
        var first = points.get(0);
        AABB result = new AABB(first.x(), first.y(), first.z(), first.x(), first.y(), first.z());
        for (var point : points) {
            result = result.minmax(new AABB(point.x(), point.y(), point.z(), point.x(), point.y(), point.z()));
        }
        // Unknown/freehand geometry may bow outside its endpoint box.
        return result.inflate(geometry instanceof StraightGeometry
                || points.size() > 2 ? 1.0 : geometry.length() + 1.0);
    }

    static double pathSampleDistance(
            double length,
            int index,
            int sampleCount
    ) {
        return pathSampleDistance(0.0, length, index, sampleCount);
    }

    static double pathSampleDistance(
            double startDistance,
            double endDistance,
            int index,
            int sampleCount
    ) {
        if (index == sampleCount) {
            return endDistance;
        }
        return Math.min(
                endDistance,
                startDistance
                        + (endDistance - startDistance)
                        * index / sampleCount
        );
    }

    private void renderArrows(
            PoseStack matrices,
            VertexConsumer lines,
            Vec3 camera,
            RoadLaneGraph.LanePath path
    ) {
        renderArrows(
                matrices,
                lines,
                camera,
                path.lane().geometry(),
                path.startDistance(),
                path.endDistance(),
                LaneTravelDirection.FORWARD
        );
    }

    private void renderArrows(
            PoseStack matrices,
            VertexConsumer lines,
            Vec3 camera,
            ReferenceGeometry geometry,
            LaneTravelDirection travelDirection
    ) {
        renderArrows(
                matrices,
                lines,
                camera,
                geometry,
                0.0,
                geometry.length(),
                travelDirection
        );
    }

    private void renderArrows(
            PoseStack matrices,
            VertexConsumer lines,
            Vec3 camera,
            ReferenceGeometry geometry,
            double startDistance,
            double endDistance,
            LaneTravelDirection travelDirection
    ) {
        AABB bounds = pathBounds.get(geometry);
        if (bounds != null && !bounds.inflate(Math.sqrt(maximumRenderDistanceSquared)).contains(camera)) {
            return;
        }
        double length = endDistance - startDistance;
        if (length <= 1.0e-9) {
            return;
        }
        int arrowCount = Math.min(1_024, Math.max(
                1,
                (int) Math.ceil(length / ARROW_SPACING)
        ));
        for (int arrowIndex = 0; arrowIndex < arrowCount; arrowIndex++) {
            double distance = Math.min(
                    endDistance,
                    startDistance + Math.min(4.0, length / 2.0)
                            + arrowIndex * length / arrowCount
            );
            PathSample sample = geometry.sample(distance);
            var point = sample.position();
            if (distanceSquared(camera, point.x(), point.y(), point.z())
                    > maximumRenderDistanceSquared) {
                continue;
            }
            Direction direction = horizontalDirection(
                    sample,
                    travelDirection
            );
            if (direction == null) {
                continue;
            }
            double forwardX = direction.x();
            double forwardZ = direction.z();
            double leftX = -forwardZ;
            double leftZ = forwardX;
            double y = point.y() + 1.10;
            double tipX = point.x() + forwardX * 0.70;
            double tipZ = point.z() + forwardZ * 0.70;
            double tailX = point.x() - forwardX * 0.55;
            double tailZ = point.z() - forwardZ * 0.55;
            line(matrices, lines, tailX, y, tailZ, tipX, y, tipZ);
            line(
                    matrices, lines,
                    tailX + leftX * 0.42, y, tailZ + leftZ * 0.42,
                    tipX, y, tipZ
            );
            line(
                    matrices, lines,
                    tailX - leftX * 0.42, y, tailZ - leftZ * 0.42,
                    tipX, y, tipZ
            );
        }
    }

    private void renderConnectionPoints(
            PoseStack matrices,
            VertexConsumer lines,
            Vec3 camera,
            RoadLaneGraph.ConnectionPoint selected,
            RoadLaneGraph.ConnectionPoint hovered
    ) {
        for (RoadLaneGraph.ConnectionPoint point
                : cachedGraph.connectionPoints()) {
            boolean isSelected = point.equals(selected);
            boolean isHovered = point.equals(hovered);
            float red = isSelected ? 1.0F : isHovered ? 1.0F : 0.94F;
            float green = isSelected ? 0.35F : isHovered ? 0.85F : 1.0F;
            float blue = isSelected ? 0.05F : isHovered ? 0.20F : 1.0F;
            renderCircle(
                    matrices,
                    lines,
                    camera,
                    point.position(),
                    red,
                    green,
                    blue
            );
        }
    }

    private void renderConnectorPreview(
            PoseStack matrices,
            MultiBufferSource consumers,
            VertexConsumer lines,
            Vec3 camera,
            RoadLaneGraph.ConnectionPoint selected,
            RoadLaneGraph.ConnectionPoint hovered
    ) {
        if (!cachedGraph.canConnect(selected, hovered)) {
            return;
        }
        try {
            ReferenceGeometry geometry = new LaneConnectorGeometryPlanner()
                    .plan(
                            new LaneConnector(
                                    new LaneConnectorId(new UUID(0L, 0L)),
                                    selected.laneId(),
                                    hovered.laneId(),
                                    LaneConnectorMovement.AUTOMATIC
                            ),
                            cachedGraph.snapshot()
                    );
            renderPath(
                    matrices,
                    consumers,
                    camera,
                    geometry,
                    0.12F,
                    1.0F,
                    0.20F,
                    0.72F
            );
            renderArrows(
                    matrices,
                    lines,
                    camera,
                    geometry,
                    LaneTravelDirection.FORWARD
            );
        } catch (IllegalArgumentException ignored) {
            // The server performs the authoritative topology validation.
        }
    }

    private void renderCircle(
            PoseStack matrices,
            VertexConsumer lines,
            Vec3 camera,
            RoadPoint point,
            float red,
            float green,
            float blue
    ) {
        if (distanceSquared(camera, point.x(), point.y(), point.z())
                > maximumRenderDistanceSquared) {
            return;
        }
        double y = point.y() + 1.16;
        for (int index = 0; index < CONNECTION_POINT_SEGMENTS; index++) {
            double startAngle = Math.PI * 2.0 * index
                    / CONNECTION_POINT_SEGMENTS;
            double endAngle = Math.PI * 2.0 * (index + 1)
                    / CONNECTION_POINT_SEGMENTS;
            line(
                    matrices,
                    lines,
                    point.x() + Math.cos(startAngle)
                            * CONNECTION_POINT_RADIUS,
                    y,
                    point.z() + Math.sin(startAngle)
                            * CONNECTION_POINT_RADIUS,
                    point.x() + Math.cos(endAngle)
                            * CONNECTION_POINT_RADIUS,
                    y,
                    point.z() + Math.sin(endAngle)
                            * CONNECTION_POINT_RADIUS,
                    red,
                    green,
                    blue
            );
        }
    }

    private static void line(
            PoseStack matrices,
            VertexConsumer lines,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ
    ) {
        line(
                matrices,
                lines,
                startX,
                startY,
                startZ,
                endX,
                endY,
                endZ,
                0.94F,
                1.0F,
                1.0F
        );
    }

    private static void line(
            PoseStack matrices,
            VertexConsumer lines,
            double startX,
            double startY,
            double startZ,
            double endX,
            double endY,
            double endZ,
            float red,
            float green,
            float blue
    ) {
        lines.vertex(matrices.last().pose(), (float) startX, (float) startY,
                        (float) startZ)
                .color(red, green, blue, 1.0F)
                .normal(matrices.last().normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
        lines.vertex(matrices.last().pose(), (float) endX, (float) endY,
                        (float) endZ)
                .color(red, green, blue, 1.0F)
                .normal(matrices.last().normal(), 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private static double distanceSquared(
            Vec3 camera,
            double x,
            double y,
            double z
    ) {
        double deltaX = camera.x - x;
        double deltaY = camera.y - y;
        double deltaZ = camera.z - z;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    static Direction horizontalDirection(
            PathSample sample,
            LaneTravelDirection travelDirection
    ) {
        Objects.requireNonNull(sample, "sample");
        Objects.requireNonNull(travelDirection, "travelDirection");
        double length = Math.hypot(
                sample.tangent().x(),
                sample.tangent().z()
        );
        if (length < 1.0e-9) {
            return null;
        }
        double sign = travelDirection == LaneTravelDirection.FORWARD
                ? 1.0
                : -1.0;
        return new Direction(
                sample.tangent().x() / length * sign,
                sample.tangent().z() / length * sign
        );
    }

    record Direction(double x, double z) {
    }
}

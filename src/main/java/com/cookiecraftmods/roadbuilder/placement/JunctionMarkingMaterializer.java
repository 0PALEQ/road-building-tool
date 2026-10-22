package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.network.JunctionSolveResult;
import com.cookiecraftmods.roadbuilder.network.JunctionSolver;
import com.cookiecraftmods.roadbuilder.network.SolvedJunctionApproach;
import com.cookiecraftmods.roadbuilder.preset.IntersectionControl;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class JunctionMarkingMaterializer {
    private static final int[] CROSSWALK_DISTANCES = {2, 4};
    private static final int STOP_LINE_DISTANCE = 6;

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            RoadNode node,
            List<RoadSegment> incidentSegments,
            JunctionDefinition definition,
            int maximumAffectedBlocks
    ) {
        JunctionSolveResult solved = new JunctionSolver().solve(
                node,
                incidentSegments,
                definition
        );
        if (solved instanceof JunctionSolveResult.Failure) {
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.INVALID_GEOMETRY
            );
        }
        List<SolvedJunctionApproach> approaches =
                ((JunctionSolveResult.Success) solved).approaches();
        boolean largeIntersection = approaches.size() >= 4
                || approaches.stream().mapToDouble(approach ->
                        approach.drivableHalfWidth() * 2.0).max().orElse(0.0)
                        >= 9.0;
        Map<BlockPos, PlannedBlock> crosswalkClearZones =
                new LinkedHashMap<>();
        Map<BlockPos, PlannedBlock> crosswalks = new LinkedHashMap<>();
        for (SolvedJunctionApproach approach : approaches) {
            if (!approach.crosswalk()) {
                continue;
            }
            int halfWidth = Math.max(
                    0,
                    (int) Math.floor(approach.drivableHalfWidth() - 0.5)
            );
            int maximumDistance = Math.max(
                    1,
                    (int) Math.floor(
                            approach.segment().referenceGeometry().length()
                                    - 1.0
                    )
            );
            int crosswalkDistance = Math.min(
                    maximumDistance,
                    Math.max(1, (int) Math.ceil(approach.trimDistance())) + 1
            );
            int crosswalkRows = largeIntersection ? 3 : 2;
            pavementBlock(approach.segment())
                    .flatMap(BuiltInRegistries.BLOCK::getOptional)
                    .map(RoadBlockStateFactory::solid)
                    .filter(state -> !state.isAir())
                    .ifPresent(state -> {
                        List<PlannedBlock> zone = new ArrayList<>();
                        addCrosswalkZone(
                                zone,
                                approach.boundaryPosition(),
                                approach.outwardTangent(),
                                approach.left(),
                                crosswalkDistance,
                                maximumDistance,
                                crosswalkRows,
                                halfWidth,
                                state
                        );
                        zone.forEach(block -> crosswalkClearZones.put(
                                block.position(),
                                block
                        ));
                    });
            crosswalkBlock(approach.segment())
                    .flatMap(BuiltInRegistries.BLOCK::getOptional)
                    .map(RoadBlockStateFactory::solid)
                    .filter(state -> !state.isAir())
                    .ifPresent(state -> {
                        List<PlannedBlock> line = new ArrayList<>();
                        addLongitudinalCrosswalk(
                                line,
                                approach.boundaryPosition(),
                                approach.outwardTangent(),
                                approach.left(),
                                crosswalkDistance,
                                maximumDistance,
                                crosswalkRows,
                                halfWidth,
                                state
                        );
                        line.forEach(block -> crosswalks.put(
                                block.position(),
                                block
                        ));
                    });
        }
        java.util.Set<BlockPos> crosswalkPositions =
                BlockPositionSets.copyOf(crosswalkClearZones.keySet());
        Map<BlockPos, PlannedBlock> blocks =
                new LinkedHashMap<>(crosswalkClearZones);
        blocks.putAll(crosswalks);
        for (SolvedJunctionApproach approach : approaches) {
            int halfWidth = Math.max(
                    0,
                    (int) Math.floor(approach.drivableHalfWidth() - 0.5)
            );
            int conflictEdge = Math.max(
                    1,
                    (int) Math.ceil(approach.trimDistance())
            );
            int maximumDistance = Math.max(
                    1,
                    (int) Math.floor(
                            approach.segment().referenceGeometry().length()
                                    - 1.0
                    )
            );
            int crosswalkRows = largeIntersection ? 3 : 2;
            int crosswalkDistance = Math.min(
                    maximumDistance,
                    conflictEdge + 1
            );
            if (approach.control() != IntersectionControl.FREE) {
                approach.segment().presetSnapshot().intersectionStyle()
                        .stopLineBlock()
                        .flatMap(BuiltInRegistries.BLOCK::getOptional)
                        .map(RoadBlockStateFactory::solid)
                        .filter(state -> !state.isAir())
                        .ifPresent(state -> {
                            List<PlannedBlock> line = new ArrayList<>();
                            int distance = Math.min(
                                    maximumDistance,
                                    crosswalkDistance
                                            + (approach.crosswalk()
                                                    ? crosswalkRows * 2
                                                    : 1)
                            );
                            if (approach.control() == IntersectionControl.YIELD) {
                                addDashedTransverseLine(
                                        line,
                                        approach.boundaryPosition(),
                                        approach.outwardTangent(),
                                        approach.left(),
                                        distance,
                                        halfWidth,
                                        state
                                );
                            } else {
                                addTransverseLine(
                                        line,
                                        approach.boundaryPosition(),
                                        approach.outwardTangent(),
                                        approach.left(),
                                        distance,
                                        halfWidth,
                                        state
                                );
                            }
                            line.forEach(block -> {
                                if (!crosswalkPositions.contains(
                                        block.position())) {
                                    blocks.put(block.position(), block);
                                }
                            });
                        });
            }
            if (blocks.size() > maximumAffectedBlocks) {
                return new BlockPlanResult.Failure(
                        BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED
                );
            }
        }
        if (blocks.isEmpty()) {
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.INVALID_GEOMETRY
            );
        }
        return new BlockPlanResult.Success(BlockPlacementPlan.create(
                dimension,
                blocks.values()
        ));
    }

    private static java.util.Optional<ResourceLocation> crosswalkBlock(
            RoadSegment segment
    ) {
        var snapshot = segment.presetSnapshot();
        return snapshot.intersectionStyle().crosswalkBlock()
                .or(() -> snapshot.surfaceBands().stream()
                        .filter(band ->
                                band.role() == RoadSurfaceRole.MARKING)
                        .map(band -> band.block())
                        .findFirst())
                .or(() -> snapshot.intersectionStyle().markingBlock())
                .or(() -> java.util.Optional.of(snapshot.surfaceBlock()));
    }

    private static java.util.Optional<ResourceLocation> pavementBlock(
            RoadSegment segment
    ) {
        var snapshot = segment.presetSnapshot();
        return java.util.Optional.of(
                snapshot.intersectionStyle().pavementBlock()
        );
    }

    public BlockPlanResult planTerminalCrosswalk(
            ResourceKey<Level> dimension,
            RoadNode node,
            RoadSegment segment,
            int maximumAffectedBlocks
    ) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(segment, "segment");
        if (maximumAffectedBlocks < 1) {
            throw new IllegalArgumentException(
                    "maximumAffectedBlocks must be positive"
            );
        }
        boolean atStart = segment.startNodeId().equals(node.id());
        if (!atStart && !segment.endNodeId().equals(node.id())) {
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.INVALID_GEOMETRY
            );
        }
        RoadVector tangent = segment.referenceGeometry().sample(
                atStart ? 0.0 : segment.referenceGeometry().length()
        ).tangent();
        RoadVector away = atStart ? tangent : tangent.scale(-1.0);
        double horizontalLength = away.horizontalLength();
        if (horizontalLength == 0.0) {
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.INVALID_GEOMETRY
            );
        }
        RoadVector forward = new RoadVector(
                away.x() / horizontalLength,
                0.0,
                away.z() / horizontalLength
        );
        RoadVector left = forward.horizontalLeftUnit();
        List<RoadSurfaceRole> roles = segment.presetSnapshot()
                .surfaceColumnRoles();
        int negativeSidewalk = 0;
        while (negativeSidewalk < roles.size()
                && roles.get(negativeSidewalk)
                        == RoadSurfaceRole.SIDEWALK) {
            negativeSidewalk++;
        }
        int positiveSidewalk = 0;
        for (int index = roles.size() - 1;
                index >= 0 && roles.get(index)
                        == RoadSurfaceRole.SIDEWALK;
                index--) {
            positiveSidewalk++;
        }
        int drivableWidth = Math.max(
                1,
                roles.size() - negativeSidewalk - positiveSidewalk
        );
        int halfWidth = Math.max(0, (drivableWidth - 1) / 2);
        int maximumDistance = Math.max(
                1,
                (int) Math.floor(
                        segment.referenceGeometry().length() - 1.0
                )
        );
        int startDistance = Math.min(2, maximumDistance);
        int rows = 2;
        Map<BlockPos, PlannedBlock> blocks = new LinkedHashMap<>();
        pavementBlock(segment)
                .flatMap(BuiltInRegistries.BLOCK::getOptional)
                .map(RoadBlockStateFactory::solid)
                .filter(state -> !state.isAir())
                .ifPresent(state -> {
                    List<PlannedBlock> zone = new ArrayList<>();
                    addCrosswalkZone(
                            zone,
                            node.position(),
                            forward,
                            left,
                            startDistance,
                            maximumDistance,
                            rows,
                            halfWidth,
                            state
                    );
                    zone.forEach(block -> blocks.put(
                            block.position(),
                            block
                    ));
                });
        crosswalkBlock(segment)
                .flatMap(BuiltInRegistries.BLOCK::getOptional)
                .map(RoadBlockStateFactory::solid)
                .filter(state -> !state.isAir())
                .ifPresent(state -> {
                    List<PlannedBlock> stripes = new ArrayList<>();
                    addLongitudinalCrosswalk(
                            stripes,
                            node.position(),
                            forward,
                            left,
                            startDistance,
                            maximumDistance,
                            rows,
                            halfWidth,
                            state
                    );
                    stripes.forEach(block -> blocks.put(
                            block.position(),
                            block
                    ));
                });
        if (blocks.isEmpty()) {
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.INVALID_GEOMETRY
            );
        }
        if (blocks.size() > maximumAffectedBlocks) {
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED
            );
        }
        return new BlockPlanResult.Success(BlockPlacementPlan.create(
                dimension,
                blocks.values()
        ));
    }

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            RoadNode node,
            List<RoadSegment> incidentSegments,
            BlockState markingState,
            int maximumAffectedBlocks
    ) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(node, "node");
        incidentSegments = List.copyOf(Objects.requireNonNull(
                incidentSegments,
                "incidentSegments"
        ));
        Objects.requireNonNull(markingState, "markingState");
        if (maximumAffectedBlocks < 1) {
            throw new IllegalArgumentException(
                    "maximumAffectedBlocks must be positive"
            );
        }
        List<PlannedBlock> blocks = new ArrayList<>();
        for (RoadSegment segment : incidentSegments.stream()
                .sorted(Comparator.comparing(RoadSegment::id))
                .toList()) {
            boolean atStart = segment.startNodeId().equals(node.id());
            if (!atStart && !segment.endNodeId().equals(node.id())) {
                continue;
            }
            RoadVector tangent = segment.referenceGeometry().sample(
                    atStart ? 0.0 : segment.referenceGeometry().length()
            ).tangent();
            RoadVector away = atStart ? tangent : tangent.scale(-1.0);
            double horizontalLength = away.horizontalLength();
            if (horizontalLength == 0.0) {
                continue;
            }
            RoadVector forward = new RoadVector(
                    away.x() / horizontalLength,
                    0.0,
                    away.z() / horizontalLength
            );
            RoadVector left = forward.horizontalLeftUnit();
            int surfaceWidth =
                    segment.presetSnapshot().surfaceBlockWidth();
            int edgeInset = Math.max(
                    segment.presetSnapshot().surfaceBands().get(0).role()
                            == RoadSurfaceRole.SIDEWALK
                            ? segment.presetSnapshot().surfaceBands()
                                    .get(0).width()
                            : 0,
                    segment.presetSnapshot().surfaceBands()
                                    .get(segment.presetSnapshot()
                                            .surfaceBands().size() - 1)
                                    .role()
                            == RoadSurfaceRole.SIDEWALK
                            ? segment.presetSnapshot().surfaceBands()
                                    .get(segment.presetSnapshot()
                                            .surfaceBands().size() - 1)
                                    .width()
                            : 0
            );
            int halfWidth = Math.max(
                    0,
                    (surfaceWidth - 1) / 2 - edgeInset
            );
            for (int distance : CROSSWALK_DISTANCES) {
                addTransverseLine(
                        blocks,
                        node.position(),
                        forward,
                        left,
                        distance,
                        halfWidth,
                        markingState
                );
            }
            addTransverseLine(
                    blocks,
                    node.position(),
                    forward,
                    left,
                    STOP_LINE_DISTANCE,
                    halfWidth,
                    markingState
            );
            if (blocks.size() > maximumAffectedBlocks) {
                return new BlockPlanResult.Failure(
                        BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED
                );
            }
        }
        if (blocks.isEmpty()) {
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.INVALID_GEOMETRY
            );
        }
        Map<BlockPos, PlannedBlock> unique = new LinkedHashMap<>();
        blocks.forEach(block -> unique.put(block.position(), block));
        return new BlockPlanResult.Success(
                BlockPlacementPlan.create(dimension, unique.values())
        );
    }

    private static void addTransverseLine(
            List<PlannedBlock> blocks,
            RoadPoint origin,
            RoadVector forward,
            RoadVector left,
            int distance,
            int halfWidth,
            BlockState state
    ) {
        for (int lateral = -halfWidth; lateral <= halfWidth; lateral++) {
            var point = origin
                    .add(forward.scale(distance))
                    .add(left.scale(lateral));
            blocks.add(new PlannedBlock(
                    BlockPos.containing(point.x(), point.y(), point.z()),
                    state
            ));
        }
    }

    private static void addDashedTransverseLine(
            List<PlannedBlock> blocks,
            RoadPoint origin,
            RoadVector forward,
            RoadVector left,
            int distance,
            int halfWidth,
            BlockState state
    ) {
        for (int lateral = -halfWidth; lateral <= halfWidth; lateral++) {
            if (Math.floorMod(lateral + halfWidth, 2) != 0) {
                continue;
            }
            var point = origin
                    .add(forward.scale(distance))
                    .add(left.scale(lateral));
            blocks.add(new PlannedBlock(
                    BlockPos.containing(point.x(), point.y(), point.z()),
                    state
            ));
        }
    }

    private static void addLongitudinalCrosswalk(
            List<PlannedBlock> blocks,
            RoadPoint origin,
            RoadVector forward,
            RoadVector left,
            int startDistance,
            int maximumDistance,
            int depth,
            int halfWidth,
            BlockState state
    ) {
        int finalDistance = Math.min(
                maximumDistance,
                startDistance + depth * 2 - 2
        );
        for (int lateral = -halfWidth; lateral <= halfWidth; lateral += 2) {
            for (int distance = startDistance;
                    distance <= finalDistance;
                    distance++) {
                var point = origin
                        .add(forward.scale(distance))
                        .add(left.scale(lateral));
                blocks.add(new PlannedBlock(
                        BlockPos.containing(
                                point.x(),
                                point.y(),
                                point.z()
                        ),
                        state
                ));
            }
        }
    }

    private static void addCrosswalkZone(
            List<PlannedBlock> blocks,
            RoadPoint origin,
            RoadVector forward,
            RoadVector left,
            int startDistance,
            int maximumDistance,
            int depth,
            int halfWidth,
            BlockState state
    ) {
        int finalDistance = Math.min(
                maximumDistance,
                startDistance + depth * 2 - 2
        );
        for (int lateral = -halfWidth; lateral <= halfWidth; lateral++) {
            for (int distance = startDistance;
                    distance <= finalDistance;
                    distance++) {
                var point = origin
                        .add(forward.scale(distance))
                        .add(left.scale(lateral));
                blocks.add(new PlannedBlock(
                        BlockPos.containing(
                                point.x(),
                                point.y(),
                                point.z()
                        ),
                        state
                ));
            }
        }
    }
}

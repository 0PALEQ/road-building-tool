package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.network.ApproachEnd;
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
                                approach.segment(),
                                approach.key().end() == ApproachEnd.START,
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
                                approach.segment(),
                                approach.key().end() == ApproachEnd.START,
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
                                        approach.segment(),
                                        approach.key().end() == ApproachEnd.START,
                                        distance,
                                        halfWidth,
                                        state
                                );
                            } else {
                                addTransverseLine(
                                        line,
                                        approach.segment(),
                                        approach.key().end() == ApproachEnd.START,
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
        if (segment.presetSnapshot().classification().roadClass()
                == com.cookiecraftmods.roadbuilder.preset.RoadClass.HIGHWAY) {
            return new BlockPlanResult.Failure(BlockPlanFailureCode.INVALID_GEOMETRY);
        }
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
                            segment,
                            atStart,
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
                            segment,
                            atStart,
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
                        segment,
                        atStart,
                        distance,
                        halfWidth,
                        markingState
                );
            }
            addTransverseLine(
                    blocks,
                    segment,
                    atStart,
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
            RoadSegment segment,
            boolean atStart,
            int distance,
            int halfWidth,
            BlockState state
    ) {
        addMarkingPatch(blocks, segment, atStart, distance, distance,
                halfWidth, state, null, false);
    }

    private static void addDashedTransverseLine(
            List<PlannedBlock> blocks,
            RoadSegment segment,
            boolean atStart,
            int distance,
            int halfWidth,
            BlockState state
    ) {
        addMarkingPatch(blocks, segment, atStart, distance, distance,
                halfWidth, state, null, true);
    }

    private static void addLongitudinalCrosswalk(
            List<PlannedBlock> blocks,
            RoadSegment segment,
            boolean atStart,
            int startDistance,
            int maximumDistance,
            int depth,
            int halfWidth,
            BlockState state
    ) {
        BlockState pavement = pavementBlock(segment)
                .flatMap(BuiltInRegistries.BLOCK::getOptional)
                .map(RoadBlockStateFactory::solid).orElse(state);
        addMarkingPatch(blocks, segment, atStart, startDistance,
                Math.min(maximumDistance, startDistance + depth * 2 - 2),
                halfWidth, state, pavement, true);
    }

    private static void addCrosswalkZone(
            List<PlannedBlock> blocks,
            RoadSegment segment,
            boolean atStart,
            int startDistance,
            int maximumDistance,
            int depth,
            int halfWidth,
            BlockState state
    ) {
        addMarkingPatch(blocks, segment, atStart, startDistance,
                Math.min(maximumDistance, startDistance + depth * 2 - 2),
                halfWidth, state, null, false);
    }

    /** Rasterize in the road's moving frame, including its elevation. */
    private static void addMarkingPatch(
            List<PlannedBlock> blocks,
            RoadSegment segment,
            boolean atStart,
            double startDistance,
            double endDistance,
            int halfWidth,
            BlockState marking,
            BlockState pavement,
            boolean striped
    ) {
        ReferenceGeometry geometry = segment.referenceGeometry();
        double from = Math.min(startDistance, geometry.length());
        double to = Math.min(endDistance, geometry.length());
        // Sub-block sampling fills diagonal rows as well as the outside of
        // bends. Resolve overlapping columns by distance to the cell center.
        Map<BlockPos, MarkingCell> cells = new LinkedHashMap<>();
        int steps = Math.max(1, (int) Math.ceil((to - from) / 0.125));
        int lateralSteps = halfWidth * 16;
        for (int step = 0; step <= steps; step++) {
            double distance = from + (to - from) * step / steps;
            PathSample sample = geometry.sample(atStart
                    ? distance : geometry.length() - distance);
            RoadVector left = sample.tangent().horizontalLeftUnit()
                    .scale(atStart ? 1.0 : -1.0);
            for (int lateralStep = 0; lateralStep <= lateralSteps; lateralStep++) {
                double lateral = -halfWidth + lateralStep * 0.125;
                RoadPoint point = sample.position().add(left.scale(lateral));
                BlockPos pos = BlockPos.containing(point.x(),
                        Math.floor(point.y() + 0.5), point.z());
                double dx = pos.getX() + 0.5 - point.x();
                double dz = pos.getZ() + 0.5 - point.z();
                double score = dx * dx + dz * dz;
                MarkingCell previous = cells.get(pos);
                if (previous == null || score < previous.score() - 1.0e-12) {
                    int column = (int) Math.floor(lateral + halfWidth + 0.5);
                    BlockState state = !striped || column % 2 == 0
                            ? marking : pavement;
                    cells.put(pos, new MarkingCell(score, state));
                }
            }
        }
        cells.forEach((pos, cell) -> {
            // Clear zones are merged before stripes, so another approach's
            // pavement must not erase an already selected white stripe.
            if (cell.state() != null
                    && (pavement == null || cell.state().equals(marking))) {
                blocks.add(new PlannedBlock(pos, cell.state()));
            }
        });
    }

    private record MarkingCell(double score, BlockState state) {}
}

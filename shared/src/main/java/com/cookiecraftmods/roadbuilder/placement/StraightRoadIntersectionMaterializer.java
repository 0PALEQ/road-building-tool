package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadForkJunction;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.network.JunctionSolver;
import com.cookiecraftmods.roadbuilder.network.JunctionSolveResult;
import com.cookiecraftmods.roadbuilder.network.JunctionConnectorPlanner;
import com.cookiecraftmods.roadbuilder.network.LaneConnector;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorGeometryPlanner;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorId;
import com.cookiecraftmods.roadbuilder.network.RoadLane;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceColumn;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class StraightRoadIntersectionMaterializer {
    public static boolean requiresJunctionSurface(RoadNode node, List<RoadSegment> incident) {
        return incident.size() >= 3 || (incident.size() == 2
                && incident.stream().anyMatch(segment ->
                        !com.cookiecraftmods.roadbuilder.network.RoadNodePortResolver
                                .isCenteredBoundary(node, segment)));
    }

    private static final double PARALLEL_TOLERANCE = 1.0e-6;
    private static final double MINIMUM_DISTINCT_AXIS_CROSS =
            Math.sin(Math.toRadians(10.0));
    private static final double MAXIMUM_LOCAL_FOOTPRINT_RADIUS = 32.0;
    private static final double MAXIMUM_FORK_FOOTPRINT_RADIUS = 12.0;
    private static final double FOOTPRINT_TOLERANCE = 1.0e-9;
    private static final double BLOCK_CELL_RADIUS = Math.sqrt(0.5);
    private static final double CURVED_APPROACH_SAMPLE_SPACING = 0.25;
    private static final double MINIMUM_FILLET_RADIUS = 4.0;
    private static final double MAXIMUM_FILLET_RADIUS = 12.0;
    private static final double MAXIMUM_FILLET_TANGENT = 12.0;
    private static final double MAXIMUM_CONFLICT_FILLET_RADIUS = 12.0;
    private static final int MINIMUM_CONNECTED_SIDEWALK_CELLS = 3;

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            RoadNode node,
            Collection<RoadSegment> incidentSegments,
            ResourceLocation pavementBlockId,
            ResourceLocation sidewalkBlockId,
            int maximumAffectedBlocks,
            int maximumPlanningCandidates
    ) {
        return plan(dimension, node, incidentSegments, pavementBlockId,
                sidewalkBlockId, maximumAffectedBlocks, maximumPlanningCandidates,
                JunctionDefinition.automatic(node.id()), List.of());
    }

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            RoadNode node,
            Collection<RoadSegment> incidentSegments,
            ResourceLocation pavementBlockId,
            ResourceLocation sidewalkBlockId,
            int maximumAffectedBlocks,
            int maximumPlanningCandidates,
            JunctionDefinition definition
    ) {
        return plan(dimension, node, incidentSegments, pavementBlockId,
                sidewalkBlockId, maximumAffectedBlocks, maximumPlanningCandidates,
                definition, List.of());
    }

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            RoadNode node,
            Collection<RoadSegment> incidentSegments,
            ResourceLocation pavementBlockId,
            ResourceLocation sidewalkBlockId,
            int maximumAffectedBlocks,
            int maximumPlanningCandidates,
            JunctionDefinition definition,
            Collection<LaneConnector> existingConnectors
    ) {
        return planSurface(dimension, node, incidentSegments, List.of(pavementBlockId),
                List.of(sidewalkBlockId), maximumAffectedBlocks, maximumPlanningCandidates,
                definition, existingConnectors, new HashSet<>());
    }

    public BlockPlanResult planWithMarkings(ResourceKey<Level> dimension, RoadNode node,
            List<RoadSegment> incidentSegments, RoadSegment primary, int maximumAffectedBlocks,
            int maximumPlanningCandidates, JunctionDefinition definition) {
        return planWithMarkings(dimension, node, incidentSegments, primary, maximumAffectedBlocks,
                maximumPlanningCandidates, definition, List.of());
    }

    public BlockPlanResult planWithMarkings(ResourceKey<Level> dimension, RoadNode node,
            List<RoadSegment> incidentSegments, RoadSegment primary, int maximumAffectedBlocks,
            int maximumPlanningCandidates, JunctionDefinition definition,
            Collection<LaneConnector> existingConnectors) {
        var preset = primary.presetSnapshot();
        var style = preset.intersectionStyle();
        List<ResourceLocation> pavement = style.usesPavementOverride(preset.surfaceBlock())
                ? List.of(style.pavementBlock()) : surfaceBlocks(incidentSegments, RoadSurfaceRole.PAVEMENT);
        if (pavement.isEmpty()) {
            pavement = List.of(preset.surfaceBlock());
        }
        List<ResourceLocation> sidewalk = surfaceBlocks(incidentSegments, RoadSurfaceRole.SIDEWALK);
        Set<BlockPos> unmarkable = new HashSet<>();
        BlockPlanResult surface = planSurface(dimension, node, incidentSegments, pavement,
                sidewalk.isEmpty() ? pavement : sidewalk, maximumAffectedBlocks, maximumPlanningCandidates,
                definition, existingConnectors, unmarkable);
        if (!(surface instanceof BlockPlanResult.Success base)) {
            return surface;
        }
        BlockPlanResult markings = new JunctionMarkingMaterializer().plan(
                dimension, node, incidentSegments, definition, maximumAffectedBlocks);
        if (!(markings instanceof BlockPlanResult.Success paint)) {
            return surface;
        }
        var clipped = BlockPlacementPlans.excludingPositions(paint.plan(), unmarkable);
        if (clipped.isEmpty()) {
            return surface;
        }
        return new BlockPlanResult.Success(BlockPlacementPlans.mergeLayered(dimension, List.of(
                new LayeredBlockPlan(base.plan(), MaterialLayer.JUNCTION_SURFACE, "node", node.id().toString()),
                new LayeredBlockPlan(clipped.orElseThrow(), MaterialLayer.JUNCTION_MARKING,
                        "node-marking", node.id().toString())
        )));
    }

    private static List<ResourceLocation> surfaceBlocks(Collection<RoadSegment> segments, RoadSurfaceRole role) {
        return segments.stream().sorted(Comparator.comparing(RoadSegment::presetId)
                        .thenComparingInt(RoadSegment::presetRevision))
                .flatMap(segment -> segment.presetSnapshot().surfaceBands().stream())
                .filter(band -> band.role() == role)
                .flatMap(band -> band.blockSet().stream()).distinct().toList();
    }

    private BlockPlanResult planSurface(ResourceKey<Level> dimension, RoadNode node,
            Collection<RoadSegment> incidentSegments, List<ResourceLocation> pavementBlocks,
            List<ResourceLocation> sidewalkBlocks, int maximumAffectedBlocks, int maximumPlanningCandidates,
            JunctionDefinition definition, Collection<LaneConnector> existingConnectors,
            Set<BlockPos> unmarkable) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(incidentSegments, "incidentSegments");
        Objects.requireNonNull(existingConnectors, "existingConnectors");
        if (maximumAffectedBlocks < 1) {
            throw new IllegalArgumentException(
                    "maximumAffectedBlocks must be positive"
            );
        }
        if (maximumPlanningCandidates < 1) {
            throw new IllegalArgumentException(
                    "maximumPlanningCandidates must be positive"
            );
        }

        var resolved = RoadSurfaceResolver.resolve(List.of(
                new RoadSurfaceColumn(pavementBlocks, pavementBlocks, RoadMarkingPattern.CONTINUOUS),
                new RoadSurfaceColumn(sidewalkBlocks, sidewalkBlocks, RoadMarkingPattern.CONTINUOUS)
        ));
        if (resolved instanceof RoadSurfaceResolver.Result.Failure failure) {
            return new BlockPlanResult.Failure(failure.code());
        }
        ResolvedRoadSurface surface = ((RoadSurfaceResolver.Result.Success) resolved).surface();

        List<Approach> approaches = distinctApproaches(
                node,
                incidentSegments
        );
        boolean auxiliary = !com.cookiecraftmods.roadbuilder.network.AuxiliaryLanePorts
                .attachments(node.id(), incidentSegments).isEmpty();
        List<Approach> branchApproaches = auxiliary ? distinctApproaches(node,
                com.cookiecraftmods.roadbuilder.network.AuxiliaryLanePorts
                        .attachments(node.id(), incidentSegments).stream()
                        .map(attachment -> attachment.branch()).toList()) : List.of();
        double maximumFootprintRadius = !auxiliary && RoadForkJunction.isFork(
                node,
                List.copyOf(incidentSegments)
        ) ? MAXIMUM_FORK_FOOTPRINT_RADIUS
                : MAXIMUM_LOCAL_FOOTPRINT_RADIUS;
        List<Axis> axes = distinctAxes(approaches);
        // Tangent continuations and shallow highway forks are valid junctions.
        // Axis separation is useful for corner fillets, not for deciding whether
        // the lane ribbons and overlapping road surfaces can be materialized.
        if (approaches.size() < 2) {
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.INVALID_GEOMETRY
            );
        }
        List<CornerFillet> fillets = auxiliary ? List.of()
                : cornerFillets(approaches, maximumFootprintRadius);

        double maximumWidth = approaches.stream()
                .mapToDouble(Approach::width)
                .max()
                .orElseThrow();
        double approachReach = Math.min(
                maximumFootprintRadius,
                1.0 + BLOCK_CELL_RADIUS
        );
        double boundReach = auxiliary || axes.size() < 2
                ? maximumFootprintRadius : approachReach;
        double bound = approaches.stream()
                .mapToDouble(approach ->
                        Math.hypot(approach.originX(), approach.originZ())
                                + boundReach
                                + approach.halfWidth()
                                + 1.0)
                .max()
                .orElse(approachReach + maximumWidth / 2.0 + 1.0);
        for (CornerFillet fillet : fillets) {
            bound = Math.max(bound, fillet.maximumExtent());
        }
        LanePavement lanePavement = lanePavement(
                node,
                incidentSegments,
                definition,
                maximumPlanningCandidates,
                existingConnectors
        );
        if (lanePavement == null) {
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.RASTERIZATION_WORK_LIMIT_EXCEEDED
            );
        }
        for (BlockPos position : lanePavement.drivable()) {
            bound = Math.max(bound, Math.max(
                    Math.abs(position.getX() + 0.5 - node.position().x()),
                    Math.abs(position.getZ() + 0.5 - node.position().z())) + 1.0);
        }
        for (BlockPos position : lanePavement.disabled()) {
            bound = Math.max(bound, Math.max(
                    Math.abs(position.getX() + 0.5 - node.position().x()),
                    Math.abs(position.getZ() + 0.5 - node.position().z())) + 1.0);
        }
        int minimumX = floorToInt(node.position().x() - bound);
        int maximumX = floorToInt(node.position().x() + bound);
        int minimumZ = floorToInt(node.position().z() - bound);
        int maximumZ = floorToInt(node.position().z() + bound);
        int blockY = floorToInt(node.position().y() + 0.5);
        long candidateCount = ((long) maximumX - minimumX + 1L)
                * ((long) maximumZ - minimumZ + 1L);
        if (candidateCount > maximumPlanningCandidates) {
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.RASTERIZATION_WORK_LIMIT_EXCEEDED
            );
        }

        List<PlannedBlock> blocks = new ArrayList<>();
        Optional<BlockState> marking = resolveMarking(incidentSegments);
        Optional<ResolvedRoadSurface> markingSurface = marking.map(state -> new ResolvedRoadSurface(1, state));
        Set<BlockPos> sidewalkPositions = new HashSet<>();
        var elevations=new JunctionSurfaceElevation(node,incidentSegments);
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                double offsetX = x + 0.5 - node.position().x();
                double offsetZ = z + 0.5 - node.position().z();
                BlockPos position = new BlockPos(x, blockY, z);
                FilletCell cell;
                boolean disabledLane = false;
                if (lanePavement.drivable().contains(position)) {
                    cell = FilletCell.PAVEMENT;
                } else if (lanePavement.disabled().contains(position)
                        && marking.isPresent()) {
                    cell = FilletCell.PAVEMENT;
                    disabledLane = true;
                } else if (auxiliary) {
                    if (branchApproaches.stream().noneMatch(approach ->
                            approach.contains(offsetX, offsetZ, maximumFootprintRadius))) {
                        continue;
                    }
                    List<Approach> overlapping = approaches.stream()
                            .filter(approach -> approach.contains(offsetX, offsetZ,
                                    maximumFootprintRadius))
                            .toList();
                    if (overlapping.size() < 2
                            || overlapping.stream().anyMatch(approach ->
                                    approach.isMedian(offsetX, offsetZ))
                            || overlapping.stream().allMatch(approach ->
                                    approach.isSidewalk(offsetX, offsetZ))) {
                        continue;
                    }
                    cell = FilletCell.PAVEMENT;
                } else {
                    cell = intersectionCell(
                            offsetX,
                            offsetZ,
                            approaches,
                            approachReach,
                            maximumFootprintRadius,
                            fillets
                    );
                }
                if (cell == FilletCell.OUTSIDE) {
                    continue;
                }
                if (blocks.size() >= maximumAffectedBlocks) {
                    return new BlockPlanResult.Failure(
                            BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED
                    );
                }
                boolean sidewalk = cell == FilletCell.SIDEWALK;
                double height = elevations.height(x + 0.5, z + 0.5);
                var placement = disabledLane
                        ? markingSurface.orElseThrow().targetPlacement(0, 0, x, z, height)
                        : surface.targetPlacement(sidewalk ? 1 : 0, 0, x, z, height);
                if (sidewalk) {
                    sidewalkPositions.add(placement.position());
                } else if (disabledLane) {
                    unmarkable.add(placement.position());
                }
                blocks.add(new PlannedBlock(placement.position(), placement.state()));
            }
        }
        if (blocks.isEmpty()) {
            return new BlockPlanResult.Failure(
                    BlockPlanFailureCode.INVALID_GEOMETRY
            );
        }
        // At a three-way merge, a one- or two-cell junction-side sidewalk
        // component can connect directly to the segment layer underneath.
        // Treating only this layer as a complete connectivity graph would
        // incorrectly pave those bridge cells and cut the visible sidewalk.
        if (approaches.size() != 3 && !pavementBlocks.equals(sidewalkBlocks)) {
            blocks = removeSidewalkIslands(blocks, sidewalkPositions, surface, elevations);
        }
        unmarkable.addAll(sidewalkPositions);
        try {
            if(auxiliary) blocks=LaneMergeMarkingPlanner.restore(dimension,node,incidentSegments,blocks,
                    maximumAffectedBlocks,maximumPlanningCandidates);
            blocks=JunctionSurfaceElevation.clearSupersededSurfaces(dimension,blocks,node,incidentSegments,
                    maximumAffectedBlocks,maximumPlanningCandidates);
        } catch(IllegalArgumentException budgetExceeded) {
            return new BlockPlanResult.Failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED);
        }
        blocks.stream().filter(block -> block.targetState().isAir())
                .map(PlannedBlock::position).forEach(unmarkable::add);
        return new BlockPlanResult.Success(
                BlockPlacementPlan.create(dimension, blocks)
        );
    }

    private static LanePavement lanePavement(
            RoadNode node,
            Collection<RoadSegment> incident,
            JunctionDefinition definition,
            int maximumCandidates,
            Collection<LaneConnector> existingConnectors
    ) {
        var result = new JunctionSolver().solve(node, incident, definition);
        if (!(result instanceof JunctionSolveResult.Success solved)) {
            return new LanePavement(Set.of(), Set.of());
        }
        Map<RoadSegmentId, Double> trims = new HashMap<>();
        solved.approaches().forEach(approach ->
                trims.put(approach.segment().id(), approach.trimDistance()));
        Map<RoadLaneId, RoadLane> lanes = new HashMap<>();
        incident.forEach(segment -> segment.lanes().forEach(lane ->
                lanes.put(lane.id(), lane)));
        AtomicLong ids = new AtomicLong();
        var connectors = new JunctionConnectorPlanner().plan(
                node.id(), incident, existingConnectors,
                () -> new LaneConnectorId(new UUID(0L, ids.incrementAndGet()))
        );
        var auxiliaryBranches = com.cookiecraftmods.roadbuilder.network.AuxiliaryLanePorts
                .attachments(node.id(), incident).stream().map(attachment -> attachment.branch().id())
                .collect(java.util.stream.Collectors.toSet());
        Set<BlockPos> drivable = new HashSet<>();
        Set<BlockPos> disabled = new HashSet<>();
        long candidates = 0;
        int y = floorToInt(node.position().y() + 0.5);
        for (var connector : connectors) {
            RoadLane from = lanes.get(connector.fromLaneId());
            RoadLane to = lanes.get(connector.toLaneId());
            if (!auxiliaryBranches.isEmpty() && !auxiliaryBranches.contains(from.segmentId())
                    && !auxiliaryBranches.contains(to.segmentId())) {
                continue;
            }
            var geometry = new LaneConnectorGeometryPlanner().plan(
                    connector, lanes, trims.get(from.segmentId()), trims.get(to.segmentId())
            );
            int steps = Math.max(1, (int) Math.ceil(geometry.length() / 0.75));
            double radius = Math.max(1.0,
                    Math.max(from.component().width(), to.component().width()) / 2.0);
            for (int index = 0; index <= steps; index++) {
                var point = geometry.sample(index == steps ? geometry.length()
                        : geometry.length() * index / steps).position();
                int minX = floorToInt(point.x() - radius);
                int maxX = floorToInt(point.x() + radius);
                int minZ = floorToInt(point.z() - radius);
                int maxZ = floorToInt(point.z() + radius);
                candidates += ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
                if (candidates > maximumCandidates) {
                    return null;
                }
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (Math.hypot(x + 0.5 - point.x(), z + 0.5 - point.z())
                                <= radius) {
                            (connector.movement().allowed()
                                    ? drivable : disabled)
                                    .add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
        return new LanePavement(drivable, disabled);
    }

    private record LanePavement(
            Set<BlockPos> drivable,
            Set<BlockPos> disabled
    ) {
    }

    private static List<PlannedBlock> removeSidewalkIslands(
            List<PlannedBlock> blocks,
            Set<BlockPos> sidewalkPositions,
            ResolvedRoadSurface surface,
            JunctionSurfaceElevation elevations
    ) {
        Set<BlockPos> unvisited = new HashSet<>(sidewalkPositions);
        Set<BlockPos> islands = new HashSet<>();
        while (!unvisited.isEmpty()) {
            BlockPos seed = unvisited.iterator().next();
            List<BlockPos> component = new ArrayList<>();
            ArrayDeque<BlockPos> pending = new ArrayDeque<>();
            pending.add(seed);
            unvisited.remove(seed);
            while (!pending.isEmpty()) {
                BlockPos current = pending.removeFirst();
                component.add(current);
                for (int deltaX = -1; deltaX <= 1; deltaX++) {
                    for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
                        if (deltaX == 0 && deltaZ == 0) {
                            continue;
                        }
                        BlockPos neighbor = current.offset(deltaX, 0, deltaZ);
                        if (unvisited.remove(neighbor)) {
                            pending.addLast(neighbor);
                        }
                    }
                }
            }
            if (component.size() < MINIMUM_CONNECTED_SIDEWALK_CELLS) {
                islands.addAll(component);
            }
        }
        if (islands.isEmpty()) {
            return blocks;
        }
        sidewalkPositions.removeAll(islands);
        return blocks.stream().map(block -> {
            BlockPos pos = block.position();
            if (!islands.contains(pos)) {
                return block;
            }
            var pavement = surface.targetPlacement(0, 0, pos.getX(), pos.getZ(),
                    elevations.height(pos.getX() + 0.5, pos.getZ() + 0.5));
            return new PlannedBlock(pavement.position(), pavement.state());
        }).toList();
    }

    private static Optional<BlockState> resolveMarking(
            Collection<RoadSegment> incidentSegments
    ) {
        return incidentSegments.stream()
                .map(RoadSegment::presetSnapshot)
                .map(snapshot -> snapshot.intersectionStyle().markingBlock()
                        .or(() -> snapshot.surfaceBands().stream()
                                .filter(band ->
                                        band.role() == RoadSurfaceRole.MARKING)
                                .map(RoadSurfaceBand::block)
                                .findFirst()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .flatMap(BuiltInRegistries.BLOCK::getOptional)
                .map(RoadBlockStateFactory::solid);
    }

    private static List<Approach> distinctApproaches(
            RoadNode node,
            Collection<RoadSegment> incidentSegments
    ) {
        List<Approach> approaches = new ArrayList<>();
        incidentSegments.stream()
                .sorted(Comparator.comparing(RoadSegment::id))
                .forEach(segment -> {
                    boolean startsAtNode = segment.startNodeId()
                            .equals(node.id());
                    boolean endsAtNode = segment.endNodeId().equals(node.id());
                    if (!startsAtNode && !endsAtNode) {
                        return;
                    }
                    var tangent = segment.referenceGeometry().sample(
                            startsAtNode
                                    ? 0.0
                                    : segment.referenceGeometry().length()
                    ).tangent();
                    double direction = startsAtNode ? 1.0 : -1.0;
                    double deltaX = tangent.x() * direction;
                    double deltaZ = tangent.z() * direction;
                    double length = Math.hypot(deltaX, deltaZ);
                    if (length <= PARALLEL_TOLERANCE) {
                        return;
                    }
                    List<RoadSurfaceRole> roles = new ArrayList<>(
                            segment.presetSnapshot().surfaceColumnRoles()
                    );
                    if (endsAtNode) {
                        java.util.Collections.reverse(roles);
                    }
                    var endpoint = segment.referenceGeometry().sample(
                            startsAtNode
                                    ? 0.0
                                    : segment.referenceGeometry().length()
                    ).position();
                    int width = segment.presetSnapshot().surfaceBlockWidth();
                    double centerX = endpoint.x();
                    double centerZ = endpoint.z();
                    if (segment.referenceGeometry() instanceof com.cookiecraftmods.roadbuilder.geometry.StraightGeometry) {
                        // The straight planner floors individual columns to block cells.
                        // Use the resulting lateral center for the fillet and its seam with the road.
                        if (Math.abs(deltaX / length) <= FOOTPRINT_TOLERANCE) {
                            centerX = rasterCenter(centerX, width);
                        } else if (Math.abs(deltaZ / length) <= FOOTPRINT_TOLERANCE) {
                            centerZ = rasterCenter(centerZ, width);
                        }
                    }
                    Approach candidate = new Approach(
                            centerX - node.position().x(), centerZ - node.position().z(),
                            deltaX / length, deltaZ / length, width,
                            roles,
                            segment.referenceGeometry().length(),
                            approachSamples(segment, node, startsAtNode)
                    );
                    boolean duplicate = approaches.stream().anyMatch(existing ->
                            existing.dot(candidate) >= 1.0
                                    - PARALLEL_TOLERANCE
                                    && Math.hypot(
                                            existing.originX()
                                                    - candidate.originX(),
                                            existing.originZ()
                                                    - candidate.originZ()
                                    ) <= PARALLEL_TOLERANCE
                                    && existing.width() == candidate.width()
                                    && existing.columnRoles().equals(candidate.columnRoles())
                                    && existing.curvedSamples().equals(candidate.curvedSamples()));
                    if (!duplicate) {
                        approaches.add(candidate);
                    }
                });
        approaches.sort(Comparator.comparingDouble(Approach::angle));
        return List.copyOf(approaches);
    }

    private static double rasterCenter(double coordinate, int width) {
        return Math.floor(coordinate - (width - 1) / 2.0) + width / 2.0;
    }

    private static List<ApproachSample> approachSamples(
            RoadSegment segment,
            RoadNode node,
            boolean startsAtNode
    ) {
        if (segment.referenceGeometry()
                instanceof com.cookiecraftmods.roadbuilder.geometry.StraightGeometry) {
            return List.of();
        }
        double availableLength = segment.referenceGeometry().length();
        double sampledLength = Math.min(
                availableLength,
                MAXIMUM_LOCAL_FOOTPRINT_RADIUS + BLOCK_CELL_RADIUS
        );
        int steps = Math.max(
                1,
                (int) Math.ceil(
                        sampledLength / CURVED_APPROACH_SAMPLE_SPACING
                )
        );
        List<ApproachSample> samples = new ArrayList<>(steps + 1);
        for (int index = 0; index <= steps; index++) {
            double outwardDistance = index == steps
                    ? sampledLength
                    : sampledLength * index / steps;
            double geometryDistance = startsAtNode
                    ? outwardDistance
                    : availableLength - outwardDistance;
            PathSample sample = segment.referenceGeometry().sample(
                    geometryDistance
            );
            double direction = startsAtNode ? 1.0 : -1.0;
            double tangentX = sample.tangent().x() * direction;
            double tangentZ = sample.tangent().z() * direction;
            double horizontalLength = Math.hypot(tangentX, tangentZ);
            samples.add(new ApproachSample(
                    outwardDistance,
                    sample.position().x() - node.position().x(),
                    sample.position().z() - node.position().z(),
                    tangentX / horizontalLength,
                    tangentZ / horizontalLength
            ));
        }
        return List.copyOf(samples);
    }

    private static List<Axis> distinctAxes(List<Approach> approaches) {
        List<Axis> axes = new ArrayList<>();
        for (Approach approach : approaches) {
            Axis candidate = new Axis(
                    approach.originX(),
                    approach.originZ(),
                    approach.unitX(),
                    approach.unitZ(),
                    approach.width()
            );
            int duplicate = -1;
            for (int index = 0; index < axes.size(); index++) {
                if (axes.get(index).sameLine(candidate)) {
                    duplicate = index;
                    break;
                }
            }
            if (duplicate < 0) {
                axes.add(candidate);
            } else if (candidate.width() > axes.get(duplicate).width()) {
                axes.set(duplicate, candidate);
            }
        }
        return List.copyOf(axes);
    }

    private static List<CornerFillet> cornerFillets(
            List<Approach> approaches,
            double maximumFootprintRadius
    ) {
        if (approaches.size() < 2) {
            return List.of();
        }
        List<CornerFillet> fillets = new ArrayList<>();
        for (int index = 0; index < approaches.size(); index++) {
            Approach first = approaches.get(index);
            Approach second = approaches.get((index + 1) % approaches.size());
            double angle = second.angle() - first.angle();
            if (angle <= 0.0) {
                angle += Math.PI * 2.0;
            }
            if (angle >= Math.PI - Math.toRadians(10.0)
                    || Math.abs(Math.sin(angle))
                            < MINIMUM_DISTINCT_AXIS_CROSS) {
                continue;
            }
            CornerFillet.create(first, second, angle, maximumFootprintRadius)
                    .ifPresent(fillets::add);
        }
        return List.copyOf(fillets);
    }

    private static FilletCell intersectionCell(
            double offsetX,
            double offsetZ,
            List<Approach> approaches,
            double approachReach,
            double maximumFootprintRadius,
            List<CornerFillet> fillets
    ) {
        List<Approach> fullContainingApproaches = approaches.stream()
                .filter(approach -> approach.contains(
                        offsetX,
                        offsetZ,
                        maximumFootprintRadius
                ))
                .toList();
        if (fullContainingApproaches.size() > 1) {
            boolean insideEveryOuterSidewalk = fullContainingApproaches.stream()
                    .allMatch(approach -> approach.isSidewalk(
                            offsetX,
                            offsetZ
                    ) && approach.isInOuterSidewalkBand(offsetX, offsetZ));
            // Offset ports in a three-way fork can make two legitimate outer
            // sidewalk bands overlap before the rounded fillet begins.
            if (approaches.size() == 3 && insideEveryOuterSidewalk) {
                return FilletCell.SIDEWALK;
            }
            if (!insideEveryOuterSidewalk) {
                return FilletCell.PAVEMENT;
            }
            boolean roundedCorner = fillets.stream().anyMatch(fillet ->
                    fillet.conflictCell(offsetX, offsetZ)
                            == FilletCell.SIDEWALK);
            if (roundedCorner) {
                return FilletCell.SIDEWALK;
            }
            return FilletCell.PAVEMENT;
        }
        List<Approach> containingApproaches = fullContainingApproaches.stream()
                .filter(approach -> approach.contains(
                        offsetX,
                        offsetZ,
                        approachReach
                ))
                .toList();
        if (!containingApproaches.isEmpty()) {
            Approach approach = containingApproaches.get(0);
            if (approach.isMedian(offsetX, offsetZ)) {
                return FilletCell.OUTSIDE;
            }
            return approach.isSidewalk(offsetX, offsetZ)
                    && approach.isInOuterSidewalkBand(offsetX, offsetZ)
                    ? FilletCell.SIDEWALK
                    : FilletCell.PAVEMENT;
        }
        if (fullContainingApproaches.size() == 1) {
            return FilletCell.OUTSIDE;
        }

        boolean insideFilletSidewalk = false;
        for (CornerFillet fillet : fillets) {
            FilletCell cell = fillet.cell(offsetX, offsetZ);
            if (cell == FilletCell.PAVEMENT) {
                return FilletCell.PAVEMENT;
            }
            insideFilletSidewalk |= cell == FilletCell.SIDEWALK;
        }
        return insideFilletSidewalk ? FilletCell.SIDEWALK : FilletCell.OUTSIDE;
    }

    private static int floorToInt(double value) {
        double floored = Math.floor(value);
        if (!Double.isFinite(floored)
                || floored < Integer.MIN_VALUE
                || floored > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "intersection coordinate is out of range"
            );
        }
        return (int) floored;
    }

    private static double cross(
            double firstX,
            double firstZ,
            double secondX,
            double secondZ
    ) {
        return firstX * secondZ - firstZ * secondX;
    }

    private record Approach(
            double originX,
            double originZ,
            double unitX,
            double unitZ,
            double width,
            List<RoadSurfaceRole> columnRoles,
            double availableLength,
            List<ApproachSample> curvedSamples
    ) {
        Approach {
            columnRoles = List.copyOf(columnRoles);
            curvedSamples = List.copyOf(curvedSamples);
            if (columnRoles.size() != (int) width) {
                throw new IllegalArgumentException(
                        "intersection approach roles must match its block width"
                );
            }
        }

        double angle() {
            double angle = Math.atan2(unitZ, unitX);
            return angle < 0.0 ? angle + Math.PI * 2.0 : angle;
        }

        double halfWidth() {
            return width / 2.0;
        }

        double dot(Approach other) {
            return unitX * other.unitX + unitZ * other.unitZ;
        }

        boolean contains(
                double offsetX,
                double offsetZ,
                double maximumDistance
        ) {
            if (!curvedSamples.isEmpty()) {
                LocalCoordinates local = curvedCoordinates(
                        offsetX,
                        offsetZ,
                        maximumDistance
                );
                double lateralLimit = halfWidth() + FOOTPRINT_TOLERANCE;
                return local != null
                        && local.distanceSquared()
                                <= lateralLimit * lateralLimit;
            }
            double relativeX = offsetX - originX;
            double relativeZ = offsetZ - originZ;
            double longitudinal = relativeX * unitX + relativeZ * unitZ;
            double lateral = Math.abs(
                    relativeX * unitZ - relativeZ * unitX
            );
            return longitudinal >= -BLOCK_CELL_RADIUS
                    && longitudinal <= Math.min(
                            maximumDistance,
                            availableLength + BLOCK_CELL_RADIUS
                    )
                    && lateral <= halfWidth() + FOOTPRINT_TOLERANCE;
        }

        boolean isMedian(double offsetX, double offsetZ) {
            int column = Math.max(0, Math.min(columnRoles.size() - 1,
                    (int) Math.floor(rightCoordinate(offsetX, offsetZ) + width / 2.0)));
            RoadSurfaceRole role = columnRoles.get(column);
            return role == RoadSurfaceRole.GRASS_MEDIAN || role == RoadSurfaceRole.CONCRETE_MEDIAN;
        }

        boolean isSidewalk(double offsetX, double offsetZ) {
            double rightCoordinate = rightCoordinate(offsetX, offsetZ);
            int column = (int) Math.floor(
                    rightCoordinate + width / 2.0
            );
            column = Math.max(
                    0,
                    Math.min(columnRoles.size() - 1, column)
            );
            return columnRoles.get(column) == RoadSurfaceRole.SIDEWALK;
        }

        boolean isInOuterSidewalkBand(double offsetX, double offsetZ) {
            double rightCoordinate = rightCoordinate(offsetX, offsetZ);
            if (rightCoordinate < 0.0) {
                return rightSidewalkWidth() > 0.0
                        && rightCoordinate <= -halfWidth()
                                + rightSidewalkWidth()
                                + FOOTPRINT_TOLERANCE;
            }
            return leftSidewalkWidth() > 0.0
                    && rightCoordinate >= halfWidth()
                            - leftSidewalkWidth()
                            - FOOTPRINT_TOLERANCE;
        }

        private double rightCoordinate(double offsetX, double offsetZ) {
            if (!curvedSamples.isEmpty()) {
                LocalCoordinates local = curvedCoordinates(
                        offsetX,
                        offsetZ,
                        MAXIMUM_LOCAL_FOOTPRINT_RADIUS
                );
                if (local != null) {
                    return local.rightCoordinate();
                }
            }
            double relativeX = offsetX - originX;
            double relativeZ = offsetZ - originZ;
            return -relativeX * unitZ + relativeZ * unitX;
        }

        private LocalCoordinates curvedCoordinates(
                double offsetX,
                double offsetZ,
                double maximumDistance
        ) {
            double rangeEnd = Math.min(
                    maximumDistance,
                    availableLength + BLOCK_CELL_RADIUS
            );
            if (rangeEnd < 0.0) {
                return null;
            }
            ApproachSample start = curvedSamples.get(0);
            double startLongitudinal = (offsetX - start.x())
                    * start.unitX()
                    + (offsetZ - start.z()) * start.unitZ();
            if (startLongitudinal < -BLOCK_CELL_RADIUS) {
                return null;
            }

            double geometryEnd = Math.min(rangeEnd, availableLength);
            double bestSquared = Double.POSITIVE_INFINITY;
            double bestRight = 0.0;
            ApproachSample rangeEndSample = start;
            for (int index = 1; index < curvedSamples.size(); index++) {
                ApproachSample first = curvedSamples.get(index - 1);
                ApproachSample storedSecond = curvedSamples.get(index);
                if (first.distance() >= geometryEnd) {
                    break;
                }
                ApproachSample second = storedSecond.distance() <= geometryEnd
                        ? storedSecond
                        : first.interpolate(
                                storedSecond,
                                (geometryEnd - first.distance())
                                        / (storedSecond.distance()
                                                - first.distance())
                        );
                double chordX = second.x() - first.x();
                double chordZ = second.z() - first.z();
                double chordLengthSquared = chordX * chordX
                        + chordZ * chordZ;
                double amount = chordLengthSquared == 0.0
                        ? 0.0
                        : ((offsetX - first.x()) * chordX
                                + (offsetZ - first.z()) * chordZ)
                                / chordLengthSquared;
                amount = Math.max(0.0, Math.min(1.0, amount));
                double projectedX = first.x() + chordX * amount;
                double projectedZ = first.z() + chordZ * amount;
                double deltaX = offsetX - projectedX;
                double deltaZ = offsetZ - projectedZ;
                double squared = deltaX * deltaX + deltaZ * deltaZ;
                if (squared < bestSquared) {
                    bestSquared = squared;
                    double chordLength = Math.sqrt(chordLengthSquared);
                    double directionX = chordLength == 0.0
                            ? first.unitX()
                            : chordX / chordLength;
                    double directionZ = chordLength == 0.0
                            ? first.unitZ()
                            : chordZ / chordLength;
                    bestRight = -deltaX * directionZ
                            + deltaZ * directionX;
                }
                rangeEndSample = second;
                if (storedSecond.distance() >= geometryEnd) {
                    break;
                }
            }
            if (bestSquared == Double.POSITIVE_INFINITY) {
                double deltaX = offsetX - start.x();
                double deltaZ = offsetZ - start.z();
                bestSquared = deltaX * deltaX + deltaZ * deltaZ;
                bestRight = -deltaX * start.unitZ()
                        + deltaZ * start.unitX();
            }
            double allowedOvershoot = Math.max(
                    0.0,
                    rangeEnd - availableLength
            );
            double endLongitudinal = (offsetX - rangeEndSample.x())
                    * rangeEndSample.unitX()
                    + (offsetZ - rangeEndSample.z())
                            * rangeEndSample.unitZ();
            if (endLongitudinal > allowedOvershoot
                    + FOOTPRINT_TOLERANCE) {
                return null;
            }
            return new LocalCoordinates(bestRight, bestSquared);
        }

        boolean curved() {
            return !curvedSamples.isEmpty();
        }

        BoundaryPoint boundaryAt(double distance, double lateralOffset) {
            ApproachSample sample = sampleAt(distance);
            return new BoundaryPoint(
                    sample.x() - sample.unitZ() * lateralOffset,
                    sample.z() + sample.unitX() * lateralOffset,
                    sample.unitX(),
                    sample.unitZ()
            );
        }

        private ApproachSample sampleAt(double distance) {
            if (curvedSamples.isEmpty()) {
                return new ApproachSample(
                        distance,
                        originX + unitX * distance,
                        originZ + unitZ * distance,
                        unitX,
                        unitZ
                );
            }
            double bounded = Math.max(
                    0.0,
                    Math.min(distance, curvedSamples.get(
                            curvedSamples.size() - 1
                    ).distance())
            );
            for (int index = 1; index < curvedSamples.size(); index++) {
                ApproachSample second = curvedSamples.get(index);
                if (second.distance() < bounded) {
                    continue;
                }
                ApproachSample first = curvedSamples.get(index - 1);
                if (second.distance() == first.distance()) {
                    return first;
                }
                return first.interpolate(
                        second,
                        (bounded - first.distance())
                                / (second.distance() - first.distance())
                );
            }
            return curvedSamples.get(curvedSamples.size() - 1);
        }

        double rightSidewalkWidth() {
            int width = 0;
            while (width < columnRoles.size()
                    && columnRoles.get(width) == RoadSurfaceRole.SIDEWALK) {
                width++;
            }
            return width;
        }

        double leftSidewalkWidth() {
            int width = 0;
            for (int index = columnRoles.size() - 1;
                    index >= 0
                            && columnRoles.get(index)
                            == RoadSurfaceRole.SIDEWALK;
                    index--) {
                width++;
            }
            return width;
        }
    }

    private record ApproachSample(
            double distance,
            double x,
            double z,
            double unitX,
            double unitZ
    ) {
        ApproachSample interpolate(ApproachSample other, double amount) {
            double x = this.x + (other.x - this.x) * amount;
            double z = this.z + (other.z - this.z) * amount;
            double tangentX = unitX + (other.unitX - unitX) * amount;
            double tangentZ = unitZ + (other.unitZ - unitZ) * amount;
            double length = Math.hypot(tangentX, tangentZ);
            return new ApproachSample(
                    distance + (other.distance - distance) * amount,
                    x,
                    z,
                    tangentX / length,
                    tangentZ / length
            );
        }
    }

    private record LocalCoordinates(
            double rightCoordinate,
            double distanceSquared
    ) {
    }

    private record BoundaryPoint(
            double x,
            double z,
            double unitX,
            double unitZ
    ) {
    }

    private record FilletSample(double x, double z) {
    }

    private record Axis(
            double originX,
            double originZ,
            double unitX,
            double unitZ,
            double width
    ) {
        double halfWidth() {
            return width / 2.0;
        }

        double distance(double offsetX, double offsetZ) {
            return Math.abs(offsetX * unitZ - offsetZ * unitX);
        }

        double cross(Axis other) {
            return unitX * other.unitZ - unitZ * other.unitX;
        }

        boolean sameLine(Axis other) {
            if (Math.abs(cross(other)) >= MINIMUM_DISTINCT_AXIS_CROSS) {
                return false;
            }
            double deltaX = other.originX - originX;
            double deltaZ = other.originZ - originZ;
            return Math.abs(deltaX * unitZ - deltaZ * unitX)
                    <= FOOTPRINT_TOLERANCE;
        }
    }

    private record CornerFillet(
            double cornerX,
            double cornerZ,
            double centerX,
            double centerZ,
            double firstX,
            double firstZ,
            double secondX,
            double secondZ,
            double cross,
            double tangentLength,
            double radius,
            double sidewalkWidth,
            double conflictCenterX,
            double conflictCenterZ,
            double conflictTangentLength,
            double conflictRadius,
            List<FilletSample> curvedBoundary,
            double curvedInteriorSign,
            double maximumCurvedPavementDepth
    ) {
        static Optional<CornerFillet> create(
                Approach first,
                Approach second,
                double angle,
                double maximumFootprintRadius
        ) {
            double sine = Math.sin(angle);
            double tangentFactor = 1.0 / Math.tan(angle / 2.0);
            if (sine <= PARALLEL_TOLERANCE
                    || tangentFactor <= PARALLEL_TOLERANCE) {
                return Optional.empty();
            }

            double sidewalkWidth = Math.max(
                    first.leftSidewalkWidth(),
                    second.rightSidewalkWidth()
            );
            double desiredRadius = Math.max(
                    MINIMUM_FILLET_RADIUS,
                    Math.min(
                            MAXIMUM_FILLET_RADIUS,
                            Math.max(
                                    MINIMUM_FILLET_RADIUS + sidewalkWidth,
                                    Math.min(
                                            first.halfWidth(),
                                            second.halfWidth()
                                    )
                            )
                    )
            );
            double firstRightX = -first.unitZ();
            double firstRightZ = first.unitX();
            double secondLeftX = second.unitZ();
            double secondLeftZ = -second.unitX();
            double firstLineX = first.originX()
                    + firstRightX * first.halfWidth();
            double firstLineZ = first.originZ()
                    + firstRightZ * first.halfWidth();
            double secondLineX = second.originX()
                    + secondLeftX * second.halfWidth();
            double secondLineZ = second.originZ()
                    + secondLeftZ * second.halfWidth();
            double lineCross = StraightRoadIntersectionMaterializer.cross(
                    first.unitX(),
                    first.unitZ(),
                    second.unitX(),
                    second.unitZ()
            );
            double firstDistance = StraightRoadIntersectionMaterializer.cross(
                    secondLineX - firstLineX,
                    secondLineZ - firstLineZ,
                    second.unitX(),
                    second.unitZ()
            ) / lineCross;
            double secondDistance = StraightRoadIntersectionMaterializer.cross(
                    secondLineX - firstLineX,
                    secondLineZ - firstLineZ,
                    first.unitX(),
                    first.unitZ()
            ) / lineCross;
            // The offset edge intersection can lie far beyond a short arm
            // at an acute angle. Tangency must fit AFTER that intersection,
            // not merely within a distance measured from the junction node.
            double firstRemaining = Math.min(first.availableLength(), maximumFootprintRadius)
                    - firstDistance;
            double secondRemaining = Math.min(second.availableLength(), maximumFootprintRadius)
                    - secondDistance;
            double maximumTangent = Math.min(MAXIMUM_FILLET_TANGENT,
                    Math.min(firstRemaining, secondRemaining));
            double radius = Math.min(desiredRadius, maximumTangent / tangentFactor);
            if (radius < 0.5) {
                // No rounded corner fits. Keep the overlapping arm surfaces;
                // do not create a detached patch on extended boundary lines.
                return Optional.empty();
            }
            double tangentLength = radius * tangentFactor;

            double cornerX = firstLineX
                    + first.unitX() * firstDistance;
            double cornerZ = firstLineZ
                    + first.unitZ() * firstDistance;
            double centerScale = radius / sine;
            double centerX = cornerX + centerScale
                    * (first.unitX() + second.unitX());
            double centerZ = cornerZ + centerScale
                    * (first.unitZ() + second.unitZ());
            double conflictTangentLength = Math.min(
                    MAXIMUM_FILLET_TANGENT,
                    Math.min(
                            first.leftSidewalkWidth(),
                            second.rightSidewalkWidth()
                    )
            );
            conflictTangentLength = Math.min(
                    conflictTangentLength,
                    maximumTangent
            );
            double conflictRadius = Math.min(
                    MAXIMUM_CONFLICT_FILLET_RADIUS,
                    conflictTangentLength / tangentFactor
            );
            double conflictCenterScale = conflictRadius / sine;
            double conflictCenterX = cornerX - conflictCenterScale
                    * (first.unitX() + second.unitX());
            double conflictCenterZ = cornerZ - conflictCenterScale
                    * (first.unitZ() + second.unitZ());
            List<FilletSample> curvedBoundary = first.curved()
                    || second.curved()
                    ? curvedBoundary(
                            first,
                            second,
                            Math.max(
                                    0.0,
                                    Math.min(
                                            firstDistance + tangentLength,
                                            first.availableLength()
                                    )
                            ),
                            Math.max(
                                    0.0,
                                    Math.min(
                                            secondDistance + tangentLength,
                                            second.availableLength()
                                    )
                            ),
                            radius,
                            angle
                    )
                    : List.of();
            double curvedInteriorSign = curvedBoundary.isEmpty()
                    ? 0.0
                    : interiorSign(curvedBoundary, cornerX, cornerZ);
            return Optional.of(new CornerFillet(
                    cornerX,
                    cornerZ,
                    centerX,
                    centerZ,
                    first.unitX(),
                    first.unitZ(),
                    second.unitX(),
                    second.unitZ(),
                    lineCross,
                    tangentLength,
                    radius,
                    sidewalkWidth,
                    conflictCenterX,
                    conflictCenterZ,
                    conflictTangentLength,
                    conflictRadius,
                    curvedBoundary,
                    curvedInteriorSign,
                    radius + Math.max(first.halfWidth(), second.halfWidth())
            ));
        }

        FilletCell cell(double offsetX, double offsetZ) {
            if (!curvedBoundary.isEmpty()) {
                return curvedCell(offsetX, offsetZ);
            }
            double relativeX = offsetX - cornerX;
            double relativeZ = offsetZ - cornerZ;
            double firstCoefficient = StraightRoadIntersectionMaterializer.cross(
                    relativeX,
                    relativeZ,
                    secondX,
                    secondZ
            ) / cross;
            double secondCoefficient = StraightRoadIntersectionMaterializer.cross(
                    firstX,
                    firstZ,
                    relativeX,
                    relativeZ
            ) / cross;
            // Only the triangle between the corner and the two tangent
            // attachments belongs to this fillet. The rest of their
            // parallelogram produces a second lobe at acute angles.
            if (firstCoefficient < -FOOTPRINT_TOLERANCE
                    || secondCoefficient < -FOOTPRINT_TOLERANCE
                    || firstCoefficient + secondCoefficient
                    > tangentLength + FOOTPRINT_TOLERANCE
                    || firstCoefficient
                    > tangentLength + FOOTPRINT_TOLERANCE
                    || secondCoefficient
                    > tangentLength + FOOTPRINT_TOLERANCE) {
                return FilletCell.OUTSIDE;
            }
            double distance = Math.hypot(
                    offsetX - centerX,
                    offsetZ - centerZ
            );
            if (distance + BLOCK_CELL_RADIUS + FOOTPRINT_TOLERANCE
                    < radius) {
                return FilletCell.OUTSIDE;
            }
            if (sidewalkWidth > 0.0
                    && distance < radius + sidewalkWidth
                            + FOOTPRINT_TOLERANCE) {
                return FilletCell.SIDEWALK;
            }
            return FilletCell.PAVEMENT;
        }

        private FilletCell curvedCell(double offsetX, double offsetZ) {
            if (!insideCurvedPatch(offsetX, offsetZ)) {
                return FilletCell.OUTSIDE;
            }
            double bestSquared = Double.POSITIVE_INFINITY;
            double bestSignedDistance = 0.0;
            for (int index = 1; index < curvedBoundary.size(); index++) {
                FilletSample start = curvedBoundary.get(index - 1);
                FilletSample end = curvedBoundary.get(index);
                double deltaX = end.x() - start.x();
                double deltaZ = end.z() - start.z();
                double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
                if (lengthSquared <= FOOTPRINT_TOLERANCE) {
                    continue;
                }
                double amount = ((offsetX - start.x()) * deltaX
                        + (offsetZ - start.z()) * deltaZ)
                        / lengthSquared;
                amount = Math.max(0.0, Math.min(1.0, amount));
                double projectedX = start.x() + deltaX * amount;
                double projectedZ = start.z() + deltaZ * amount;
                double relativeX = offsetX - projectedX;
                double relativeZ = offsetZ - projectedZ;
                double squared = relativeX * relativeX
                        + relativeZ * relativeZ;
                if (squared >= bestSquared) {
                    continue;
                }
                bestSquared = squared;
                double length = Math.sqrt(lengthSquared);
                bestSignedDistance = (deltaX * relativeZ
                        - deltaZ * relativeX) / length
                        * curvedInteriorSign;
            }
            if (bestSquared == Double.POSITIVE_INFINITY
                    || bestSignedDistance
                            < -BLOCK_CELL_RADIUS - FOOTPRINT_TOLERANCE
                    || bestSignedDistance
                            > maximumCurvedPavementDepth
                                    + FOOTPRINT_TOLERANCE) {
                return FilletCell.OUTSIDE;
            }
            return sidewalkWidth > 0.0
                    && bestSignedDistance
                            <= sidewalkWidth + FOOTPRINT_TOLERANCE
                    ? FilletCell.SIDEWALK
                    : FilletCell.PAVEMENT;
        }

        private boolean insideCurvedPatch(
                double offsetX,
                double offsetZ
        ) {
            boolean inside = false;
            double bestEdgeDistanceSquared = Double.POSITIVE_INFINITY;
            FilletSample previous = new FilletSample(cornerX, cornerZ);
            for (FilletSample sample : curvedBoundary) {
                double deltaX = sample.x() - previous.x();
                double deltaZ = sample.z() - previous.z();
                double lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
                if (lengthSquared > FOOTPRINT_TOLERANCE) {
                    double amount = ((offsetX - previous.x()) * deltaX
                            + (offsetZ - previous.z()) * deltaZ)
                            / lengthSquared;
                    amount = Math.max(0.0, Math.min(1.0, amount));
                    double projectedX = previous.x() + deltaX * amount;
                    double projectedZ = previous.z() + deltaZ * amount;
                    double relativeX = offsetX - projectedX;
                    double relativeZ = offsetZ - projectedZ;
                    double squared = relativeX * relativeX
                            + relativeZ * relativeZ;
                    if (squared < bestEdgeDistanceSquared) {
                        bestEdgeDistanceSquared = squared;
                    }
                    if ((previous.z() > offsetZ)
                            != (sample.z() > offsetZ)) {
                        double crossingX = previous.x()
                                + (offsetZ - previous.z())
                                        * deltaX / deltaZ;
                        if (offsetX < crossingX) {
                            inside = !inside;
                        }
                    }
                }
                previous = sample;
            }
            double closingX = cornerX - previous.x();
            double closingZ = cornerZ - previous.z();
            double closingLengthSquared = closingX * closingX
                    + closingZ * closingZ;
            if (closingLengthSquared > FOOTPRINT_TOLERANCE) {
                double amount = ((offsetX - previous.x()) * closingX
                        + (offsetZ - previous.z()) * closingZ)
                        / closingLengthSquared;
                amount = Math.max(0.0, Math.min(1.0, amount));
                double projectedX = previous.x() + closingX * amount;
                double projectedZ = previous.z() + closingZ * amount;
                double relativeX = offsetX - projectedX;
                double relativeZ = offsetZ - projectedZ;
                double squared = relativeX * relativeX
                        + relativeZ * relativeZ;
                if (squared < bestEdgeDistanceSquared) {
                    bestEdgeDistanceSquared = squared;
                }
                if ((previous.z() > offsetZ)
                        != (cornerZ > offsetZ)) {
                    double crossingX = previous.x()
                            + (offsetZ - previous.z())
                                    * closingX / closingZ;
                    if (offsetX < crossingX) {
                        inside = !inside;
                    }
                }
            }
            return inside
                    || bestEdgeDistanceSquared
                            <= BLOCK_CELL_RADIUS * BLOCK_CELL_RADIUS
                                    + FOOTPRINT_TOLERANCE;
        }

        private static List<FilletSample> curvedBoundary(
                Approach first,
                Approach second,
                double firstAttachment,
                double secondAttachment,
                double radius,
                double angle
        ) {
            BoundaryPoint start = first.boundaryAt(
                    firstAttachment,
                    first.halfWidth()
            );
            BoundaryPoint end = second.boundaryAt(
                    secondAttachment,
                    -second.halfWidth()
            );
            double handle = Math.min(
                    Math.min(firstAttachment, secondAttachment),
                    4.0 / 3.0 * Math.tan(angle / 4.0) * radius
            );
            double firstControlX = start.x() - start.unitX() * handle;
            double firstControlZ = start.z() - start.unitZ() * handle;
            double secondControlX = end.x() - end.unitX() * handle;
            double secondControlZ = end.z() - end.unitZ() * handle;
            int steps = 32;
            List<FilletSample> samples = new ArrayList<>(steps + 1);
            for (int index = 0; index <= steps; index++) {
                double amount = index / (double) steps;
                double inverse = 1.0 - amount;
                double startWeight = inverse * inverse * inverse;
                double firstWeight = 3.0 * inverse * inverse * amount;
                double secondWeight = 3.0 * inverse * amount * amount;
                double endWeight = amount * amount * amount;
                samples.add(new FilletSample(
                        start.x() * startWeight
                                + firstControlX * firstWeight
                                + secondControlX * secondWeight
                                + end.x() * endWeight,
                        start.z() * startWeight
                                + firstControlZ * firstWeight
                                + secondControlZ * secondWeight
                                + end.z() * endWeight
                ));
            }
            return List.copyOf(samples);
        }

        private static double interiorSign(
                List<FilletSample> boundary,
                double interiorX,
                double interiorZ
        ) {
            int middle = boundary.size() / 2;
            FilletSample before = boundary.get(middle - 1);
            FilletSample after = boundary.get(middle + 1);
            FilletSample point = boundary.get(middle);
            double cross = (after.x() - before.x())
                    * (interiorZ - point.z())
                    - (after.z() - before.z())
                            * (interiorX - point.x());
            return cross < 0.0 ? -1.0 : 1.0;
        }

        FilletCell conflictCell(double offsetX, double offsetZ) {
            if (sidewalkWidth <= 0.0 || conflictRadius < 0.5) {
                return FilletCell.OUTSIDE;
            }
            if (!curvedBoundary.isEmpty()) {
                return curvedCell(offsetX, offsetZ);
            }
            double relativeX = offsetX - cornerX;
            double relativeZ = offsetZ - cornerZ;
            double firstCoefficient = StraightRoadIntersectionMaterializer.cross(
                    relativeX,
                    relativeZ,
                    secondX,
                    secondZ
            ) / cross;
            double secondCoefficient = StraightRoadIntersectionMaterializer.cross(
                    firstX,
                    firstZ,
                    relativeX,
                    relativeZ
            ) / cross;
            if (firstCoefficient > FOOTPRINT_TOLERANCE
                    || secondCoefficient > FOOTPRINT_TOLERANCE
                    || firstCoefficient + secondCoefficient
                    < -conflictTangentLength - FOOTPRINT_TOLERANCE
                    || firstCoefficient
                    < -conflictTangentLength - FOOTPRINT_TOLERANCE
                    || secondCoefficient
                    < -conflictTangentLength - FOOTPRINT_TOLERANCE) {
                return FilletCell.OUTSIDE;
            }
            double distance = Math.hypot(
                    offsetX - conflictCenterX,
                    offsetZ - conflictCenterZ
            );
            return distance + BLOCK_CELL_RADIUS + FOOTPRINT_TOLERANCE
                    >= conflictRadius
                    ? FilletCell.SIDEWALK
                    : FilletCell.PAVEMENT;
        }

        double maximumExtent() {
            double farX = cornerX
                    + tangentLength * (firstX + secondX);
            double farZ = cornerZ
                    + tangentLength * (firstZ + secondZ);
            double straightExtent = Math.max(
                    Math.max(Math.abs(cornerX), Math.abs(cornerZ)),
                    Math.max(
                            Math.max(Math.abs(centerX), Math.abs(centerZ)),
                            Math.max(Math.abs(farX), Math.abs(farZ))
                    )
            ) + 1.0;
            if (curvedBoundary.isEmpty()) {
                return straightExtent;
            }
            double curvedExtent = curvedBoundary.stream()
                    .mapToDouble(sample -> Math.max(
                            Math.abs(sample.x()),
                            Math.abs(sample.z())
                    ))
                    .max()
                    .orElse(0.0)
                    + maximumCurvedPavementDepth
                    + BLOCK_CELL_RADIUS;
            return Math.max(straightExtent, curvedExtent);
        }
    }

    private enum FilletCell {
        OUTSIDE,
        SIDEWALK,
        PAVEMENT
    }
}

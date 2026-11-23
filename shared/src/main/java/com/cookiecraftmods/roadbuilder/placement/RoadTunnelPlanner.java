package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class RoadTunnelPlanner {
    public static final int FREE_HEIGHT = 7;
    public static final double MINIMUM_SEGMENT_LENGTH = 8.0;
    public static final int PILLAR_SPACING = 2;
    public static final int LIGHT_SPACING = 10;

    public Result addTunnels(
            BlockPlacementPlan surfacePlan,
            List<RoadSegment> segments,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition,
            int maximumAffectedBlocks
    ) {
        Objects.requireNonNull(surfacePlan, "surfacePlan");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(existingRoadPosition, "existingRoadPosition");
        if (!surfacePlan.dimension().equals(world.dimension())) {
            throw new IllegalArgumentException("plan and world dimensions differ");
        }
        if (maximumAffectedBlocks < 1) {
            throw new IllegalArgumentException("maximumAffectedBlocks must be positive");
        }

        Map<BlockPos, PlannedBlock> targets = new LinkedHashMap<>();
        Set<BlockPos> surfacePositions = new HashSet<>(surfacePlan.positions());
        SurfaceIndex surfaceIndex = new SurfaceIndex(surfacePositions);
        Set<BlockPos> tunnelSurfaces = detectTunnelSurfaces(
                surfaceIndex,
                segments,
                world,
                existingRoadPosition
        );
        Set<BlockPos> protectedRoof = new HashSet<>();
        for (PlannedBlock surface : surfacePlan.blocks()) {
            if (surface.targetState().isAir()
                    || !tunnelSurfaces.contains(surface.position())) {
                continue;
            }
            for (int offset = 1; offset <= FREE_HEIGHT; offset++) {
                BlockPos clearance = surface.position().above(offset);
                if (existingRoadPosition.test(clearance)
                        || world.hasBlockEntity(clearance)
                        || world.blockState(clearance).isAir()) {
                    continue;
                }
                if (!put(targets, clearance, Blocks.AIR.defaultBlockState(), maximumAffectedBlocks)) {
                    return Result.failure();
                }
            }
            BlockPos roof = surface.position().above(FREE_HEIGHT + 1);
            if (!existingRoadPosition.test(roof)
                    && !world.hasBlockEntity(roof)
                    && !put(
                            targets,
                            roof,
                            Blocks.POLISHED_ANDESITE.defaultBlockState(),
                            maximumAffectedBlocks
                    )) {
                return Result.failure();
            }
            for (int offset = FREE_HEIGHT + 1;
                    offset <= RoadClearancePlanner.DEFAULT_CLEARANCE_HEIGHT;
                    offset++) {
                protectedRoof.add(surface.position().above(offset));
            }
            for (int offset = FREE_HEIGHT + 2;
                    offset <= RoadClearancePlanner.DEFAULT_CLEARANCE_HEIGHT;
                    offset++) {
                BlockPos vegetation = surface.position().above(offset);
                BlockState state = world.blockState(vegetation);
                if (existingRoadPosition.test(vegetation)
                        || world.hasBlockEntity(vegetation)) {
                    break;
                }
                if (state.isAir()) {
                    continue;
                }
                if (!isUnsupportedVegetation(state)) {
                    break;
                }
                if (!put(
                        targets,
                        vegetation,
                        Blocks.AIR.defaultBlockState(),
                        maximumAffectedBlocks
                )) {
                    return Result.failure();
                }
            }
        }

        if (tunnelSurfaces.isEmpty()) {
            return Result.success(emptyPlan(surfacePlan), Set.of());
        }
        // Sweep the lateral boundaries. A rasterized footprint also has steps and
        // end faces; extruding those faces creates columns across the portal.
        for (RoadSegment segment : segments) {
            if (segment.presetSnapshot().sunkenRoad()) continue;
            ReferenceGeometry geometry = segment.referenceGeometry();
            double halfWidth = segment.presetSnapshot().surfaceBlockWidth() / 2.0;
            for (double distance = 0; distance <= geometry.length(); distance += 0.25) {
                PathSample sample = geometry.sample(distance);
                RoadVector left = sample.tangent().horizontalLeftUnit();
                for (int side : new int[]{-1, 1}) {
                    Optional<BlockPos> edge = surfaceIndex.resolve(sample.position().add(
                            left.scale(side * (halfWidth - 0.5))));
                    if (edge.isEmpty() || !tunnelSurfaces.contains(edge.get())) continue;
                    BlockPos outside = surfacePosition(sample.position().add(left.scale(side * (halfWidth + 0.5))));
                    outside = new BlockPos(outside.getX(), edge.get().getY(), outside.getZ());
                    if (surfacePositions.contains(outside) || surfacePositions.contains(outside.above())
                            || surfacePositions.contains(outside.below())) continue;
                    for (int height = 0; height <= FREE_HEIGHT + 1; height++) {
                        BlockPos wall = outside.above(height);
                        if (existingRoadPosition.test(wall) || world.hasBlockEntity(wall)) continue;
                        if (!put(targets, wall, Blocks.POLISHED_ANDESITE.defaultBlockState(), maximumAffectedBlocks))
                            return Result.failure();
                    }
                }
            }
        }
        for (RoadSegment segment : segments) {
            if (segment.presetSnapshot().sunkenRoad()) continue;
            addMedianAndPillars(
                    targets,
                    tunnelSurfaces,
                    surfacePositions,
                    surfaceIndex,
                    segment,
                    world,
                    maximumAffectedBlocks
            );
            addLights(
                    targets,
                    tunnelSurfaces,
                    surfaceIndex,
                    segment,
                    world,
                    maximumAffectedBlocks
            );
            if (targets.size() > maximumAffectedBlocks) {
                return Result.failure();
            }
        }
        return Result.success(
                BlockPlacementPlan.create(surfacePlan.dimension(), targets.values()),
                protectedRoof
        );
    }

    private static Set<BlockPos> detectTunnelSurfaces(
            SurfaceIndex surfaceIndex,
            List<RoadSegment> segments,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition
    ) {
        Set<BlockPos> detected = new HashSet<>();
        for (RoadSegment segment : segments) {
            if (segment.presetSnapshot().sunkenRoad()) continue;
            if (segment.referenceGeometry().length() < MINIMUM_SEGMENT_LENGTH) continue;
            Set<BlockPos> segmentSurfaces = new HashSet<>();
            double coveredLength = 0;
            double currentRun = 0;
            double longestRun = 0;
            ReferenceGeometry geometry = segment.referenceGeometry();
            double halfWidth = segment.presetSnapshot().surfaceBlockWidth() / 2.0;
            for (double distance = 0.0;
                    distance <= geometry.length();
                    distance += 0.25) {
                PathSample sample = geometry.sample(
                        Math.min(distance, geometry.length())
                );
                RoadVector left = sample.tangent().horizontalLeftUnit();
                Set<BlockPos> crossSection = new HashSet<>();
                for (double lateral = -halfWidth - 0.5;
                        lateral <= halfWidth + 0.5;
                        lateral += 0.25) {
                    surfaceIndex.resolve(
                            sample.position().add(left.scale(lateral))
                    ).ifPresent(crossSection::add);
                }
                boolean covered = covered(sample, segment.presetSnapshot().surfaceBlockWidth(),
                        world, existingRoadPosition, surfaceIndex);
                segmentSurfaces.addAll(crossSection);
                if (covered) {
                    coveredLength += 0.25;
                    currentRun += 0.25;
                    longestRun = Math.max(longestRun, currentRun);
                } else currentRun = 0;
            }
            if (coveredLength >= geometry.length() * 0.6 && longestRun >= MINIMUM_SEGMENT_LENGTH - 0.5)
                detected.addAll(segmentSurfaces);
        }
        return detected;
    }

    public static boolean isTerrain(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return state.blocksMotion() && !(state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)
                && !path.endsWith("_log") && !path.endsWith("_wood")
                && !path.endsWith("_stem") && !path.endsWith("_hyphae")
                && !state.is(net.minecraft.tags.BlockTags.LOGS)
                && !state.is(net.minecraft.tags.BlockTags.LEAVES) && !state.canBeReplaced();
    }

    /** Portal cuts are measured along the center line and include the full road width. */
    public static List<Double> portalFractions(ReferenceGeometry geometry, int width, PlacementBlockWorld world) {
        return portalFractions(geometry, width, world, position -> false);
    }

    public static List<Double> portalFractions(ReferenceGeometry geometry, int width, PlacementBlockWorld world,
                                                Predicate<BlockPos> existingRoadPosition) {
        List<Double> cuts = new ArrayList<>();
        boolean previous = covered(geometry, width, world, existingRoadPosition, 0);
        for (double distance = 1; distance < geometry.length(); distance += 1) {
            boolean current = covered(geometry, width, world, existingRoadPosition, distance);
            if (current != previous) cuts.add(distance / geometry.length());
            previous = current;
        }
        return stableCuts(geometry.length(), covered(geometry, width, world, existingRoadPosition, 0), cuts);
    }

    private static List<Double> stableCuts(double length, boolean startsCovered, List<Double> cuts) {
        List<Double> boundaries = new ArrayList<>();
        boundaries.add(0.0);
        cuts.forEach(cut -> boundaries.add(cut * length));
        boundaries.add(length);
        List<Boolean> states = new ArrayList<>();
        for (int i = 0; i < boundaries.size()-1; i++) states.add(startsCovered ^ (i % 2 == 1));
        // Ignore short terrain spikes before filling short gaps between tunnels.
        for (int i = 0; i < states.size(); i++)
            if (states.get(i) && boundaries.get(i+1)-boundaries.get(i) < MINIMUM_SEGMENT_LENGTH) states.set(i, false);
        List<Double> result = new ArrayList<>();
        boolean state = states.get(0);
        for (int i = 1; i < states.size(); i++) {
            if (states.get(i) != state) {
                result.add(boundaries.get(i) / length);
                state = states.get(i);
            }
        }
        // Removing a pair merges a short open gap. Removing an endpoint cut
        // absorbs a short approach into the adjacent, already valid segment.
        boolean changed = true;
        while (changed && !result.isEmpty()) {
            changed = false;
            for (int i=0; i<=result.size(); i++) {
                double from = i==0 ? 0 : result.get(i-1)*length;
                double to = i==result.size() ? length : result.get(i)*length;
                if (to-from >= MINIMUM_SEGMENT_LENGTH) continue;
                if (i==0) result.remove(0);
                else if (i==result.size()) result.remove(i-1);
                else { result.remove(i); result.remove(i-1); }
                changed = true;
                break;
            }
        }
        return result;
    }

    private static boolean covered(ReferenceGeometry geometry, int width, PlacementBlockWorld world, Predicate<BlockPos> existingRoadPosition, double distance) {
        return covered(geometry.sample(distance), width, world, existingRoadPosition, null);
    }

    private static boolean covered(PathSample sample, int width, PlacementBlockWorld world,
                                   Predicate<BlockPos> existingRoadPosition, SurfaceIndex surfaceIndex) {
        RoadVector left = sample.tangent().horizontalLeftUnit();
        int coveredColumns = 0;
        boolean centerCovered = false;
        for (int column = 0; column < width; column++) {
            double lateral = column - (width - 1) / 2.0;
            RoadPoint point = sample.position().add(left.scale(lateral));
            BlockPos surface = surfaceIndex == null ? surfacePosition(point)
                    : surfaceIndex.resolve(point).orElse(surfacePosition(point));
            BlockPos probe = surface.above(FREE_HEIGHT);
            boolean terrain = !existingRoadPosition.test(probe) && isTerrain(world.blockState(probe));
            if (terrain) coveredColumns++;
            if (Math.abs(lateral) <= 0.5) centerCovered |= terrain;
        }
        // A cut into one side of a hillside needs a retaining wall, not a roof
        // extending over otherwise open lanes.
        return centerCovered && coveredColumns >= Math.ceil(width * 0.6);
    }

    private static boolean isUnsupportedVegetation(BlockState state) {
        return state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LEAVES);
    }

    private static void addMedianAndPillars(
            Map<BlockPos, PlannedBlock> targets,
            Set<BlockPos> tunnelSurfaces,
            Set<BlockPos> surfacePositions,
            SurfaceIndex surfaceIndex,
            RoadSegment segment,
            PlacementBlockWorld world,
            int maximumAffectedBlocks
    ) {
        Optional<BlockState> sidewalk = sidewalkState(segment);
        if (sidewalk.isEmpty() || !hasDoubleContinuousMedian(segment)) {
            return;
        }
        ReferenceGeometry geometry = segment.referenceGeometry();
        for (double distance = 0.0; distance <= geometry.length(); distance += 0.25) {
            PathSample sample = geometry.sample(Math.min(distance, geometry.length()));
            RoadVector left = sample.tangent().horizontalLeftUnit();
            for (int lateral = -1; lateral <= 1; lateral++) {
                surfaceIndex.resolve(sample.position().add(left.scale(lateral)))
                        .filter(tunnelSurfaces::contains)
                        .filter(surfacePositions::contains)
                        .ifPresent(median -> put(
                                targets,
                                median,
                                sidewalk.orElseThrow(),
                                maximumAffectedBlocks
                        ));
            }
        }
        for (double distance = PILLAR_SPACING;
                distance < geometry.length();
                distance += PILLAR_SPACING) {
            Optional<BlockPos> resolvedBase = surfaceIndex.resolve(
                    geometry.sample(distance).position()
            );
            if (resolvedBase.isEmpty()
                    || !tunnelSurfaces.contains(resolvedBase.orElseThrow())) {
                continue;
            }
            BlockPos base = resolvedBase.orElseThrow();
            for (int offset = 1; offset <= FREE_HEIGHT; offset++) {
                BlockPos pillar = base.above(offset);
                if (!world.hasBlockEntity(pillar)) {
                    put(targets, pillar, Blocks.STONE_BRICKS.defaultBlockState(), maximumAffectedBlocks);
                }
            }
        }
    }

    private static void addLights(
            Map<BlockPos, PlannedBlock> targets,
            Set<BlockPos> tunnelSurfaces,
            SurfaceIndex surfaceIndex,
            RoadSegment segment,
            PlacementBlockWorld world,
            int maximumAffectedBlocks
    ) {
        ReferenceGeometry geometry = segment.referenceGeometry();
        List<Double> offsets = laneSideOffsets(segment);
        for (double distance = LIGHT_SPACING;
                distance < geometry.length();
                distance += LIGHT_SPACING) {
            PathSample sample = geometry.sample(distance);
            RoadVector left = sample.tangent().horizontalLeftUnit();
            for (double lateralOffset : offsets) {
                Optional<BlockPos> resolvedSurface = surfaceIndex.resolve(
                        sample.position().add(left.scale(-lateralOffset))
                );
                if (resolvedSurface.isEmpty()
                        || !tunnelSurfaces.contains(resolvedSurface.orElseThrow())) {
                    continue;
                }
                BlockPos surface = resolvedSurface.orElseThrow();
                BlockPos light = surface.above(FREE_HEIGHT + 1);
                if (!world.hasBlockEntity(light)) {
                    put(targets, light, Blocks.SEA_LANTERN.defaultBlockState(), maximumAffectedBlocks);
                }
            }
        }
    }

    private static List<Double> laneSideOffsets(RoadSegment segment) {
        List<LaneComponent> lanes = segment.presetSnapshot().lanes(
                segment.trafficHandedness()
        );
        List<Double> negative = new ArrayList<>();
        List<Double> positive = new ArrayList<>();
        for (LaneComponent lane : lanes) {
            (lane.lateralOffset() < 0.0 ? negative : positive)
                    .add(lane.lateralOffset());
        }
        double fallback = Math.max(1.0,
                segment.presetSnapshot().surfaceBlockWidth() / 4.0);
        return List.of(
                average(negative, -fallback),
                average(positive, fallback)
        );
    }

    private static double average(List<Double> values, double fallback) {
        return values.isEmpty()
                ? fallback
                : values.stream().mapToDouble(Double::doubleValue).average().orElse(fallback);
    }

    private static boolean hasDoubleContinuousMedian(RoadSegment segment) {
        List<RoadSurfaceBand> bands = segment.presetSnapshot().surfaceBands();
        int center = bands.stream().mapToInt(band ->
                band.pattern() == RoadMarkingPattern.DOUBLE_CONTINUOUS ? 3 : band.width()
        ).sum() / 2;
        int cursor = 0;
        for (RoadSurfaceBand band : bands) {
            int width = band.pattern() == RoadMarkingPattern.DOUBLE_CONTINUOUS
                    ? 3 : band.width();
            if (band.pattern() == RoadMarkingPattern.DOUBLE_CONTINUOUS
                    && center >= cursor && center < cursor + width) {
                return true;
            }
            cursor += width;
        }
        return false;
    }

    private static Optional<BlockState> sidewalkState(RoadSegment segment) {
        return segment.presetSnapshot().surfaceBands().stream()
                .filter(band -> band.role() == RoadSurfaceRole.SIDEWALK)
                .map(RoadSurfaceBand::block)
                .map(BuiltInRegistries.BLOCK::getOptional)
                .flatMap(Optional::stream)
                .map(Block::defaultBlockState)
                .findFirst();
    }

    private static BlockPos surfacePosition(RoadPoint point) {
        return new BlockPos(
                floor(point.x()),
                floor(point.y() + 0.5),
                floor(point.z())
        );
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    /**
     * Resolves geometry samples to the surface blocks that were actually
     * rasterized. Half-step roads deliberately place bottom slabs in the
     * block above the nominal elevation, and floating-point drift near .5
     * must not make tunnel features skip those columns.
     */
    private static final class SurfaceIndex {
        private final Map<HorizontalColumn, List<BlockPos>> positionsByColumn =
                new java.util.HashMap<>();

        private SurfaceIndex(Set<BlockPos> positions) {
            for (BlockPos position : positions) {
                positionsByColumn.computeIfAbsent(
                        new HorizontalColumn(position.getX(), position.getZ()),
                        ignored -> new ArrayList<>()
                ).add(position);
            }
        }

        private Optional<BlockPos> resolve(RoadPoint point) {
            List<BlockPos> candidates = positionsByColumn.get(
                    new HorizontalColumn(floor(point.x()), floor(point.z()))
            );
            if (candidates == null) {
                return Optional.empty();
            }
            return candidates.stream()
                    .filter(position -> Math.abs(
                            position.getY() - point.y()
                    ) <= 1.0)
                    .min(java.util.Comparator.comparingDouble(
                            position -> Math.abs(position.getY() - point.y())
                    ));
        }
    }

    private record HorizontalColumn(int x, int z) {
    }

    private static boolean put(
            Map<BlockPos, PlannedBlock> targets,
            BlockPos position,
            BlockState state,
            int maximumAffectedBlocks
    ) {
        if (!targets.containsKey(position) && targets.size() >= maximumAffectedBlocks) {
            return false;
        }
        targets.put(position.immutable(), new PlannedBlock(position, state));
        return true;
    }

    private static BlockPlacementPlan emptyPlan(BlockPlacementPlan surfacePlan) {
        // BlockPlacementPlan intentionally rejects empty plans; a harmless
        // surface cell is returned and loses to the identical surface layer.
        PlannedBlock first = surfacePlan.blocks().get(0);
        return BlockPlacementPlan.create(surfacePlan.dimension(), List.of(first));
    }

    public record Result(
            Optional<BlockPlacementPlan> plan,
            Set<BlockPos> protectedRoofPositions
    ) {
        public Result {
            Objects.requireNonNull(plan, "plan");
            protectedRoofPositions = BlockPositionSets.copyOf(Objects.requireNonNull(
                    protectedRoofPositions,
                    "protectedRoofPositions"
            ));
        }

        static Result success(BlockPlacementPlan plan, Set<BlockPos> protectedRoof) {
            return new Result(Optional.of(plan), protectedRoof);
        }

        static Result failure() {
            return new Result(Optional.empty(), Set.of());
        }
    }
}

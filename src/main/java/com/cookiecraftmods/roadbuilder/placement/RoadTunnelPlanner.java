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
        for (RoadSegment segment : segments) {
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
                boolean covered = crossSection.stream().anyMatch(position -> {
                    BlockPos probe = position.above(FREE_HEIGHT);
                    BlockState state = world.blockState(probe);
                    return state.blocksMotion()
                            && !existingRoadPosition.test(probe);
                });
                if (covered) {
                    detected.addAll(crossSection);
                }
            }
        }
        return detected;
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
            protectedRoofPositions = Set.copyOf(Objects.requireNonNull(
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

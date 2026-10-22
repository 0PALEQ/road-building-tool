package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class RoadStructurePlanner {
    public static final int MAXIMUM_TERRAIN_FILL_HEIGHT = 5;
    public static final int MAXIMUM_SOLID_BASE_HEIGHT = 7;
    public static final int DECK_THICKNESS = 2;
    public static final int MAXIMUM_GROUND_SCAN_DEPTH = 128;

    private static final int PAIRED_PIER_MINIMUM_ROAD_WIDTH = 24;
    private static final double PIER_SPACING = 20.0;
    private static final double PIER_END_INSET = 2.0;
    private static final double ENDPOINT_DECK_PROBE_DISTANCE = 3.0;
    private static final double ENDPOINT_DECK_ZONE_MARGIN = 12.0;

    private static final int EMBANKMENT_RADIUS =
            MAXIMUM_TERRAIN_FILL_HEIGHT - 1;
    private static final int CUT_WALL_HEIGHT =
            RoadTunnelPlanner.FREE_HEIGHT + 2;
    private static final int MINIMUM_EXPOSED_HEIGHT = 2;
    private static final int AT_GRADE_ROAD_GAP = 6;
    private static final int[][] CARDINAL_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private final RoadStructureMaterialSet materials;

    public RoadStructurePlanner() {
        this(RoadStructureMaterialSet.standard());
    }

    public RoadStructurePlanner(RoadStructureMaterialSet materials) {
        this.materials = Objects.requireNonNull(materials, "materials");
    }

    public BlockPlanResult addStructures(
            BlockPlacementPlan surfacePlan,
            PlacementBlockWorld world,
            int maximumAffectedBlocks
    ) {
        return addStructures(
                surfacePlan,
                world,
                List.of(),
                position -> false,
                maximumAffectedBlocks
        );
    }

    public BlockPlanResult addStructures(
            BlockPlacementPlan surfacePlan,
            PlacementBlockWorld world,
            ReferenceGeometry geometry,
            int roadWidth,
            Predicate<BlockPos> existingRoadPosition,
            int maximumAffectedBlocks
    ) {
        List<StructureSpan> spans = geometry == null
                ? List.of()
                : List.of(new StructureSpan(geometry, roadWidth));
        return addStructures(
                surfacePlan,
                world,
                spans,
                existingRoadPosition,
                maximumAffectedBlocks
        );
    }

    public BlockPlanResult addStructures(
            BlockPlacementPlan surfacePlan,
            PlacementBlockWorld world,
            List<StructureSpan> spans,
            Predicate<BlockPos> existingRoadPosition,
            int maximumAffectedBlocks
    ) {
        Objects.requireNonNull(surfacePlan, "surfacePlan");
        Objects.requireNonNull(world, "world");
        spans = List.copyOf(Objects.requireNonNull(spans, "spans"));
        Objects.requireNonNull(existingRoadPosition, "existingRoadPosition");
        if (!surfacePlan.dimension().equals(world.dimension())) {
            throw new IllegalArgumentException("plan and world dimensions differ");
        }
        if (maximumAffectedBlocks < 1) {
            throw new IllegalArgumentException(
                    "maximumAffectedBlocks must be positive"
            );
        }

        surfacePlan = prepareRailingSurface(surfacePlan, spans, world, existingRoadPosition);
        Map<BlockPos, PlannedBlock> targets = new LinkedHashMap<>();
        Set<BlockPos> surfacePositions = new HashSet<>();
        Set<BlockPos> roadSurfacePositions = new HashSet<>();
        Set<HorizontalColumn> footprint = new HashSet<>();
        for (PlannedBlock block : surfacePlan.blocks()) {
            targets.put(block.position(), block);
            surfacePositions.add(block.position());
            if (!block.targetState().isAir()) {
                roadSurfacePositions.add(block.position());
                footprint.add(new HorizontalColumn(
                        block.position().getX(),
                        block.position().getZ()
                ));
            }
        }

        Map<GroundKey, Ground> groundCache = new HashMap<>();
        List<EndpointDeckZone> endpointDeckZones = endpointDeckZones(
                spans,
                world,
                existingRoadPosition,
                groundCache
        );
        Map<HorizontalColumn, EmbankmentCandidate> embankments =
                new HashMap<>();
        Set<BlockPos> railingSurfacePositions = new HashSet<>();
        for (PlannedBlock surface : surfacePlan.blocks()) {
            if (surface.targetState().isAir()) {
                continue;
            }
            BlockPos position = surface.position();
            Ground ground = groundBelow(
                    position,
                    world,
                    existingRoadPosition,
                    groundCache
            );
            int verticalGap = position.getY() - ground.position().getY();
            boolean edge = isEdge(position, footprint);
            boolean sideEdge = isSideEdge(position, footprint, spans);
            boolean roadBelow = ground.solid()
                    && verticalGap > AT_GRADE_ROAD_GAP
                    && existingRoadPosition.test(ground.position());
            boolean forcedDeck = endpointDeckZones.stream()
                    .anyMatch(zone -> zone.contains(position));
            boolean deck = forcedDeck
                    || roadBelow
                    || ground.fluidEncountered()
                    || verticalGap > MAXIMUM_SOLID_BASE_HEIGHT;

            if (deck) {
                if (!addDeck(
                        targets,
                        surfacePositions,
                        position,
                        ground.position().getY(),
                        sideEdge,
                        maximumAffectedBlocks
                )) {
                    return failure();
                }
                railingSurfacePositions.add(position);
            } else if (verticalGap > 1) {
                if (sideEdge && verticalGap > MAXIMUM_TERRAIN_FILL_HEIGHT) {
                    railingSurfacePositions.add(position);
                }
                BlockState interior = verticalGap
                        <= MAXIMUM_TERRAIN_FILL_HEIGHT
                        ? materials.terrainFill()
                        : materials.reinforcedBase();
                BlockState state = sideEdge
                        && verticalGap > MAXIMUM_TERRAIN_FILL_HEIGHT
                        ? materials.retainingWall()
                        : interior;
                for (int y = position.getY() - 1;
                        y > ground.position().getY();
                        y--) {
                    BlockPos support = new BlockPos(
                            position.getX(),
                            y,
                            position.getZ()
                    );
                    if (existingRoadPosition.test(support)) {
                        continue;
                    }
                    if (!put(
                            targets,
                            surfacePositions,
                            support,
                            state,
                            maximumAffectedBlocks
                    )) {
                        return failure();
                    }
                }
            }
            if (edge
                    && !deck
                    && verticalGap <= MAXIMUM_TERRAIN_FILL_HEIGHT) {
                addEmbankmentCandidates(
                        embankments,
                        position,
                        footprint
                );
            }

            if (sideEdge
                    && !deck
                    && !roadBelow
                    && !ground.fluidEncountered()
                    && verticalGap <= MAXIMUM_SOLID_BASE_HEIGHT
                    && !addCutWalls(
                            targets,
                            surfacePositions,
                            position,
                            footprint,
                            spans,
                            world,
                            existingRoadPosition,
                            maximumAffectedBlocks
                    )) {
                return failure();
            }
        }

        Set<BlockPos> barrierPositions = barrierPositions(
                railingSurfacePositions,
                roadSurfacePositions,
                spans,
                world,
                existingRoadPosition
        );
        // A terrain cut wall beside the road is not an exposed drop.
        barrierPositions.removeIf(position -> {
            PlannedBlock curb = targets.get(position.below());
            return curb != null && !curb.targetState().isAir();
        });
        Set<BlockPos> presetBarriers = new HashSet<>();
        for (StructureSpan span : spans) {
            int column = 0;
            for (RoadSurfaceBand band : span.surfaceBands()) {
                if (band.role() == RoadSurfaceRole.RAILING) {
                    Set<BlockPos> bandPositions = new HashSet<>();
                    for (int index = 0; index < band.width(); index++) {
                        addBarrierPath(bandPositions, span.geometry(),
                                (span.roadWidth() - 1) / 2.0 - column - index);
                    }
                    if (column == 0 || column + band.width() == span.roadWidth()) {
                        bandPositions.removeIf(position -> !isExposedHorizontalEdge(
                                position, roadSurfacePositions, existingRoadPosition));
                    }
                    bandPositions.removeIf(position -> {
                        PlannedBlock base = targets.get(position);
                        return base == null || !isRailingMaterial(base.targetState(), band);
                    });
                    presetBarriers.addAll(bandPositions);
                }
                column += band.width();
            }
        }
        presetBarriers.retainAll(roadSurfacePositions);
        // An explicit edge railing replaces the automatic outer bridge railing.
        barrierPositions.removeIf(position -> {
            for (int[] direction : CARDINAL_DIRECTIONS) {
                if (presetBarriers.contains(position.below().offset(
                        direction[0], 0, direction[1]))) {
                    return true;
                }
            }
            return false;
        });
        presetBarriers.stream().map(BlockPos::above).forEach(barrierPositions::add);
        if (!addBarriers(
                targets,
                surfacePositions,
                barrierPositions,
                roadSurfacePositions,
                world,
                existingRoadPosition,
                maximumAffectedBlocks
        )) {
            return failure();
        }

        if (!addEmbankments(
                targets,
                surfacePositions,
                embankments,
                world,
                existingRoadPosition,
                groundCache,
                maximumAffectedBlocks
        )) {
            return failure();
        }
        for (StructureSpan span : spans) {
            if (!addPillars(
                    targets,
                    surfacePositions,
                    span.geometry(),
                    span.roadWidth(),
                    world,
                    existingRoadPosition,
                    footprint,
                    groundCache,
                    maximumAffectedBlocks
            )) {
                return failure();
            }
        }
        return new BlockPlanResult.Success(BlockPlacementPlan.create(
                surfacePlan.dimension(),
                targets.values()
        ));
    }

    /** Replaces buried railing foundations with pavement and gives exposed bars a full-height base. */
    public BlockPlacementPlan prepareRailingSurface(
            BlockPlacementPlan surface, List<StructureSpan> spans,
            PlacementBlockWorld world, Predicate<BlockPos> existingRoadPosition
    ) {
        Map<BlockPos, PlannedBlock> blocks = new LinkedHashMap<>();
        surface.blocks().forEach(block -> blocks.put(block.position(), block));
        Set<BlockPos> footprint = surface.blocks().stream()
                .filter(block -> !block.targetState().isAir())
                .map(PlannedBlock::position).collect(java.util.stream.Collectors.toSet());
        for (StructureSpan span : spans) {
            RoadSurfaceBand pavement = span.surfaceBands().stream()
                    .filter(band -> band.role() == RoadSurfaceRole.PAVEMENT)
                    .findFirst().orElse(null);
            if (pavement == null) {
                continue;
            }
            int column = 0;
            for (RoadSurfaceBand band : span.surfaceBands()) {
                if (band.role() == RoadSurfaceRole.RAILING) {
                    Set<BlockPos> positions = new HashSet<>();
                    for (int index = 0; index < band.width(); index++) {
                        addBarrierPath(positions, span.geometry(),
                                (span.roadWidth() - 1) / 2.0 - column - index);
                    }
                    boolean edge = column == 0 || column + band.width() == span.roadWidth();
                    for (BlockPos position : positions) {
                        PlannedBlock base = blocks.get(position);
                        if (base == null || !isRailingMaterial(base.targetState(), band)) {
                            continue;
                        }
                        BlockState old = world.blockState(position);
                        boolean existingPavement = existingRoadPosition.test(position)
                                && !old.isAir() && !isRailingMaterial(old, band);
                        boolean buriedEdge = edge && !isExposedHorizontalEdge(
                                position, footprint, existingRoadPosition);
                        BlockState state = base.targetState();
                        if (existingPavement) {
                            state = old;
                        } else if (buriedEdge) {
                            state = RoadBlockStateFactory.solid(
                                    BuiltInRegistries.BLOCK.get(pavement.block()));
                            if (base.targetState().getBlock() instanceof SlabBlock
                                    && base.targetState().getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
                                state = RoadBlockStateFactory.slabCompanion(state)
                                        .map(slab -> slab.setValue(SlabBlock.TYPE, SlabType.BOTTOM))
                                        .orElse(state);
                            }
                        } else if (state.getBlock() instanceof SlabBlock) {
                            state = state.setValue(SlabBlock.TYPE, SlabType.DOUBLE);
                        }
                        blocks.put(position, new PlannedBlock(position, state));
                    }
                }
                column += band.width();
            }
        }
        return BlockPlacementPlan.create(surface.dimension(), blocks.values());
    }

    private static boolean isRailingMaterial(BlockState state, RoadSurfaceBand band) {
        return band.blockSet().stream().anyMatch(id -> {
            BlockState material = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
            return state.is(material.getBlock()) || RoadBlockStateFactory.slabCompanion(material)
                    .map(slab -> state.is(slab.getBlock())).orElse(false);
        });
    }

    private boolean addDeck(
            Map<BlockPos, PlannedBlock> targets,
            Set<BlockPos> surfacePositions,
            BlockPos surface,
            int groundY,
            boolean sideEdge,
            int maximumAffectedBlocks
    ) {
        for (int depth = 1; depth <= DECK_THICKNESS; depth++) {
            int y = surface.getY() - depth;
            if (y <= groundY) {
                break;
            }
            if (depth == DECK_THICKNESS && sideEdge) {
                continue;
            }
            BlockState state = depth == 1
                    ? sideEdge
                            ? materials.deckFascia()
                            : materials.deckCore()
                    : materials.deckSoffit();
            if (!put(
                    targets,
                    surfacePositions,
                    new BlockPos(surface.getX(), y, surface.getZ()),
                    state,
                    maximumAffectedBlocks
            )) {
                return false;
            }
        }
        return true;
    }

    private boolean addBarriers(
            Map<BlockPos, PlannedBlock> targets,
            Set<BlockPos> surfacePositions,
            Set<BlockPos> barrierPositions,
            Set<BlockPos> roadSurfacePositions,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition,
            int maximumAffectedBlocks
    ) {
        for (BlockPos position : barrierPositions) {
            // Carry the fascia out beneath the railing and up to road level.
            // A full curb supports the bars even beside a bottom-slab surface.
            if (!surfacePositions.contains(position.below())
                    && (!put(targets, surfacePositions, position.below(2),
                    materials.deckFascia(), maximumAffectedBlocks)
                    || !put(targets, surfacePositions, position.below(),
                    materials.deckFascia(), maximumAffectedBlocks))) {
                return false;
            }
            BlockState state = materials.barrier();
            if (state.is(Blocks.IRON_BARS)) {
                state = state
                        .setValue(
                                CrossCollisionBlock.NORTH,
                                barrierPositions.contains(position.north())
                        )
                        .setValue(
                                CrossCollisionBlock.EAST,
                                barrierPositions.contains(position.east())
                        )
                        .setValue(
                                CrossCollisionBlock.SOUTH,
                                barrierPositions.contains(position.south())
                        )
                        .setValue(
                                CrossCollisionBlock.WEST,
                                barrierPositions.contains(position.west())
                        );
            }
            if (!put(
                    targets,
                    surfacePositions,
                    position,
                    state,
                    maximumAffectedBlocks
            ) || !put(
                    targets,
                    surfacePositions,
                    position.above(),
                    materials.barrierCap(),
                    maximumAffectedBlocks
            )) {
                return false;
            }
        }
        for (BlockPos surface : roadSurfacePositions) {
            BlockPos barrier = surface.above();
            if (!barrierPositions.contains(barrier)
                    && existingRoadPosition.test(barrier)
                    && world.blockState(barrier).is(
                            materials.barrier().getBlock()
                    )
                    && !put(
                            targets,
                            surfacePositions,
                            barrier,
                            Blocks.AIR.defaultBlockState(),
                            maximumAffectedBlocks
                    )) {
                return false;
            }
            BlockPos cap = barrier.above();
            if (!barrierPositions.contains(barrier)
                    && existingRoadPosition.test(cap)
                    && world.blockState(cap).is(
                            materials.barrierCap().getBlock()
                    )
                    && !put(
                            targets,
                            surfacePositions,
                            cap,
                            Blocks.AIR.defaultBlockState(),
                            maximumAffectedBlocks
                    )) {
                return false;
            }
        }
        return true;
    }

    private Set<BlockPos> barrierPositions(
            Set<BlockPos> railingSurfacePositions,
            Set<BlockPos> roadSurfacePositions,
            List<StructureSpan> spans,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition
    ) {
        Set<BlockPos> candidates = new HashSet<>();
        Set<BlockPos> outerDeckPositions = new HashSet<>();
        Set<HorizontalColumn> footprint = new HashSet<>();
        for (BlockPos surface : roadSurfacePositions) {
            footprint.add(new HorizontalColumn(surface.getX(), surface.getZ()));
        }
        for (BlockPos surface : railingSurfacePositions) {
            for (int[] direction : CARDINAL_DIRECTIONS) {
                BlockPos outer = surface.offset(direction[0], 0, direction[1]);
                boolean existingLedge = existingRoadPosition.test(outer)
                        && world.blockState(outer).equals(materials.deckFascia())
                        && existingRoadPosition.test(outer.above())
                        && world.blockState(outer.above()).is(
                                materials.barrier().getBlock());
                boolean occupied = existingRoadPosition.test(outer)
                        || existingRoadPosition.test(outer.below())
                        || existingRoadPosition.test(outer.above())
                        || existingRoadPosition.test(outer.above(2));
                if (!footprint.contains(new HorizontalColumn(
                        outer.getX(), outer.getZ()))
                        && !isEndDirection(surface, direction, spans)
                        && (existingLedge || !occupied)) {
                    outerDeckPositions.add(outer);
                }
            }
        }
        if (spans.isEmpty()) {
            candidates.addAll(outerDeckPositions);
        } else {
            for (StructureSpan span : spans) {
                double edgeOffset = (span.roadWidth() + 1) / 2.0;
                addBarrierPath(candidates, span.geometry(), edgeOffset);
                if (edgeOffset != 0.0) {
                    addBarrierPath(candidates, span.geometry(), -edgeOffset);
                }
            }
            candidates.retainAll(outerDeckPositions);
        }
        candidates.removeIf(position -> !isExposedHorizontalEdge(
                position,
                roadSurfacePositions,
                existingRoadPosition
        ));
        return candidates.stream()
                .map(BlockPos::above)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static void addBarrierPath(
            Set<BlockPos> candidates,
            ReferenceGeometry geometry,
            double lateralOffset
    ) {
        int steps = Math.max(
                1,
                (int) Math.ceil(
                        geometry.length() / RoadPathPlanner.MAXIMUM_PATH_STEP
                )
        );
        PathSample previous = geometry.sample(0.0);
        for (int index = 1; index <= steps; index++) {
            double distance = index == steps
                    ? geometry.length()
                    : geometry.length() * index / steps;
            PathSample next = geometry.sample(distance);
            rasterizeBarrierSpan(
                    candidates,
                    previous.offsetLaterally(lateralOffset).position(),
                    next.offsetLaterally(lateralOffset).position()
            );
            previous = next;
        }
    }

    private static void rasterizeBarrierSpan(
            Set<BlockPos> candidates,
            RoadPoint start,
            RoadPoint end
    ) {
        int x = floorToInt(start.x());
        int z = floorToInt(start.z());
        int finalX = floorToInt(end.x());
        int finalZ = floorToInt(end.z());
        addBarrierCandidate(candidates, x, z, start.y());
        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        int stepX = Double.compare(deltaX, 0.0);
        int stepZ = Double.compare(deltaZ, 0.0);
        double deltaTimeX = stepX == 0
                ? Double.POSITIVE_INFINITY
                : Math.abs(1.0 / deltaX);
        double deltaTimeZ = stepZ == 0
                ? Double.POSITIVE_INFINITY
                : Math.abs(1.0 / deltaZ);
        double nextTimeX = firstBoundaryTime(
                start.x(),
                x,
                deltaX,
                stepX
        );
        double nextTimeZ = firstBoundaryTime(
                start.z(),
                z,
                deltaZ,
                stepZ
        );
        while (x != finalX || z != finalZ) {
            if (x == finalX) {
                z += stepZ;
                addBarrierCandidate(
                        candidates,
                        x,
                        z,
                        interpolate(start.y(), end.y(), nextTimeZ)
                );
                nextTimeZ += deltaTimeZ;
            } else if (z == finalZ) {
                x += stepX;
                addBarrierCandidate(
                        candidates,
                        x,
                        z,
                        interpolate(start.y(), end.y(), nextTimeX)
                );
                nextTimeX += deltaTimeX;
            } else if (Math.abs(nextTimeX - nextTimeZ) <= 1.0e-12) {
                double time = nextTimeX;
                x += stepX;
                addBarrierCandidate(
                        candidates,
                        x,
                        z,
                        interpolate(start.y(), end.y(), time)
                );
                z += stepZ;
                addBarrierCandidate(
                        candidates,
                        x,
                        z,
                        interpolate(start.y(), end.y(), time)
                );
                nextTimeX += deltaTimeX;
                nextTimeZ += deltaTimeZ;
            } else if (nextTimeX < nextTimeZ) {
                x += stepX;
                addBarrierCandidate(
                        candidates,
                        x,
                        z,
                        interpolate(start.y(), end.y(), nextTimeX)
                );
                nextTimeX += deltaTimeX;
            } else {
                z += stepZ;
                addBarrierCandidate(
                        candidates,
                        x,
                        z,
                        interpolate(start.y(), end.y(), nextTimeZ)
                );
                nextTimeZ += deltaTimeZ;
            }
        }
    }

    private static void addBarrierCandidate(
            Set<BlockPos> candidates,
            int x,
            int z,
            double y
    ) {
        candidates.add(new BlockPos(x, floorToInt(y + 0.5), z));
    }

    private static double firstBoundaryTime(
            double coordinate,
            int cell,
            double delta,
            int step
    ) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? cell + 1.0 : cell;
        return (boundary - coordinate) / delta;
    }

    private static double interpolate(double start, double end, double time) {
        return start + (end - start) * Math.max(0.0, Math.min(1.0, time));
    }

    private static boolean isExposedHorizontalEdge(
            BlockPos position,
            Set<BlockPos> roadSurfacePositions,
            Predicate<BlockPos> existingRoadPosition
    ) {
        for (int[] direction : CARDINAL_DIRECTIONS) {
            BlockPos neighbor = position.offset(direction[0], 0, direction[1]);
            if (!roadSurfacePositions.contains(neighbor)
                    && !existingRoadPosition.test(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private static void addEmbankmentCandidates(
            Map<HorizontalColumn, EmbankmentCandidate> candidates,
            BlockPos surface,
            Set<HorizontalColumn> footprint
    ) {
        int radius = EMBANKMENT_RADIUS;
        for (int xOffset = -radius; xOffset <= radius; xOffset++) {
            for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                int distance = Math.max(
                        Math.abs(xOffset),
                        Math.abs(zOffset)
                );
                if (distance == 0) {
                    continue;
                }
                HorizontalColumn column = new HorizontalColumn(
                        surface.getX() + xOffset,
                        surface.getZ() + zOffset
                );
                if (footprint.contains(column)) {
                    continue;
                }
                EmbankmentCandidate candidate = new EmbankmentCandidate(
                        surface.getY() - distance,
                        surface.getY() + CUT_WALL_HEIGHT
                );
                candidates.merge(column, candidate, (first, second) ->
                        new EmbankmentCandidate(
                                Math.max(first.topY(), second.topY()),
                                Math.max(
                                        first.clearanceY(),
                                        second.clearanceY()
                                )
                        ));
            }
        }
    }

    private boolean addEmbankments(
            Map<BlockPos, PlannedBlock> targets,
            Set<BlockPos> surfacePositions,
            Map<HorizontalColumn, EmbankmentCandidate> candidates,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition,
            Map<GroundKey, Ground> groundCache,
            int maximumAffectedBlocks
    ) {
        for (Map.Entry<HorizontalColumn, EmbankmentCandidate> entry
                : candidates.entrySet()) {
            HorizontalColumn column = entry.getKey();
            EmbankmentCandidate candidate = entry.getValue();
            Ground ground = groundBelow(
                    new BlockPos(
                            column.x(),
                            candidate.topY() + 1,
                            column.z()
                    ),
                    world,
                    existingRoadPosition,
                    groundCache
            );
            if (!ground.solid()
                    || ground.fluidEncountered()
                    || ground.position().getY() >= candidate.topY()
                    || existingRoadPosition.test(ground.position())) {
                continue;
            }
            boolean available = true;
            for (int y = ground.position().getY() + 1;
                    y <= candidate.topY();
                    y++) {
                BlockPos position = new BlockPos(column.x(), y, column.z());
                BlockState state = world.blockState(position);
                if (existingRoadPosition.test(position)
                        || world.hasBlockEntity(position)
                        || !state.getFluidState().isEmpty()) {
                    available = false;
                    break;
                }
            }
            if (!available) {
                continue;
            }
            for (int y = ground.position().getY() + 1;
                    y <= candidate.topY();
                    y++) {
                BlockState state = y == candidate.topY()
                        ? materials.embankmentSurface()
                        : materials.terrainFill();
                if (!put(
                        targets,
                        surfacePositions,
                        new BlockPos(column.x(), y, column.z()),
                        state,
                        maximumAffectedBlocks
                )) {
                    return false;
                }
            }
            for (int y = candidate.topY() + 1;
                    y <= candidate.clearanceY();
                    y++) {
                BlockPos position = new BlockPos(column.x(), y, column.z());
                if (existingRoadPosition.test(position)
                        || world.hasBlockEntity(position)) {
                    break;
                }
                BlockState state = world.blockState(position);
                if (!state.getFluidState().isEmpty()) {
                    break;
                }
                if (isVegetation(state)
                        && !put(
                                targets,
                                surfacePositions,
                                position,
                                Blocks.AIR.defaultBlockState(),
                                maximumAffectedBlocks
                        )) {
                    return false;
                }
                if (isSolidTerrain(state)) {
                    break;
                }
            }
        }
        return true;
    }

    private boolean addCutWalls(
            Map<BlockPos, PlannedBlock> targets,
            Set<BlockPos> surfacePositions,
            BlockPos surface,
            Set<HorizontalColumn> footprint,
            List<StructureSpan> spans,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition,
            int maximumAffectedBlocks
    ) {
        for (int[] direction : CARDINAL_DIRECTIONS) {
            HorizontalColumn neighbor = new HorizontalColumn(
                    surface.getX() + direction[0],
                    surface.getZ() + direction[1]
            );
            if (footprint.contains(neighbor)) {
                continue;
            }
            if (isEndDirection(surface, direction, spans)) {
                continue;
            }
            boolean wallGate = true;
            for (int offset = 0;
                    offset <= MINIMUM_EXPOSED_HEIGHT;
                    offset++) {
                BlockPos position = new BlockPos(
                        neighbor.x(),
                        surface.getY() + offset,
                        neighbor.z()
                );
                if (existingRoadPosition.test(position)
                        || world.hasBlockEntity(position)) {
                    wallGate = false;
                    break;
                }
                BlockState state = world.blockState(position);
                if (!state.getFluidState().isEmpty()) {
                    wallGate = false;
                    break;
                }
                if (state.isAir()
                        || isVegetation(state)
                        || !isSolidTerrain(state)) {
                    wallGate = false;
                    break;
                }
            }
            boolean solidRun = true;
            for (int offset = 0; offset < CUT_WALL_HEIGHT; offset++) {
                BlockPos position = new BlockPos(
                        neighbor.x(),
                        surface.getY() + offset,
                        neighbor.z()
                );
                if (existingRoadPosition.test(position)
                        || world.hasBlockEntity(position)) {
                    break;
                }
                BlockState state = world.blockState(position);
                if (!state.getFluidState().isEmpty()) {
                    break;
                }
                if (state.isAir()) {
                    solidRun = false;
                    continue;
                }
                if (isVegetation(state)) {
                    solidRun = false;
                    if (!put(
                            targets,
                            surfacePositions,
                            position,
                            Blocks.AIR.defaultBlockState(),
                            maximumAffectedBlocks
                    )) {
                        return false;
                    }
                    continue;
                }
                if (!solidRun || !isSolidTerrain(state)) {
                    break;
                }
                if (wallGate && !put(
                        targets,
                        surfacePositions,
                        position,
                        materials.retainingWall(),
                        maximumAffectedBlocks
                )) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<EndpointDeckZone> endpointDeckZones(
            List<StructureSpan> spans,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition,
            Map<GroundKey, Ground> groundCache
    ) {
        List<EndpointDeckZone> zones = new java.util.ArrayList<>();
        for (StructureSpan span : spans) {
            ReferenceGeometry geometry = span.geometry();
            double probeDistance = Math.min(
                    ENDPOINT_DECK_PROBE_DISTANCE,
                    geometry.length() / 2.0
            );
            addEndpointDeckZone(
                    zones,
                    geometry.sample(0.0).position(),
                    geometry.sample(probeDistance).position(),
                    span.roadWidth(),
                    world,
                    existingRoadPosition,
                    groundCache
            );
            addEndpointDeckZone(
                    zones,
                    geometry.sample(geometry.length()).position(),
                    geometry.sample(
                            geometry.length() - probeDistance
                    ).position(),
                    span.roadWidth(),
                    world,
                    existingRoadPosition,
                    groundCache
            );
        }
        return List.copyOf(zones);
    }

    private static void addEndpointDeckZone(
            List<EndpointDeckZone> zones,
            RoadPoint endpoint,
            RoadPoint probe,
            int roadWidth,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition,
            Map<GroundKey, Ground> groundCache
    ) {
        BlockPos probePosition = new BlockPos(
                floorToInt(probe.x()),
                floorToInt(probe.y() + 0.5),
                floorToInt(probe.z())
        );
        Ground ground = groundBelow(
                probePosition,
                world,
                existingRoadPosition,
                groundCache
        );
        int verticalGap = probePosition.getY() - ground.position().getY();
        if (!ground.fluidEncountered()
                && verticalGap <= MAXIMUM_SOLID_BASE_HEIGHT) {
            return;
        }
        zones.add(new EndpointDeckZone(
                endpoint.x(),
                endpoint.z(),
                roadWidth / 2.0 + ENDPOINT_DECK_ZONE_MARGIN
        ));
    }

    private boolean addPillars(
            Map<BlockPos, PlannedBlock> targets,
            Set<BlockPos> surfacePositions,
            ReferenceGeometry geometry,
            int roadWidth,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition,
            Set<HorizontalColumn> footprint,
            Map<GroundKey, Ground> groundCache,
            int maximumAffectedBlocks
    ) {
        int diameter = roadWidth >= 15 ? 3 : roadWidth >= 7 ? 2 : 1;
        boolean paired = roadWidth >= PAIRED_PIER_MINIMUM_ROAD_WIDTH;
        double pairedOffset = roadWidth * 0.2;
        double[] lateralOffsets = paired
                ? new double[]{-pairedOffset, pairedOffset}
                : new double[]{0.0};
        for (double distance : pillarDistances(geometry.length())) {
            PathSample sample = geometry.sample(distance);
            HorizontalColumn sampleColumn = new HorizontalColumn(
                    floorToInt(sample.position().x()),
                    floorToInt(sample.position().z())
            );
            if (!footprint.contains(sampleColumn)) {
                continue;
            }
            RoadVector left = sample.tangent().horizontalLeftUnit();
            double horizontalLength = sample.tangent().horizontalLength();
            RoadVector forward = new RoadVector(
                    sample.tangent().x() / horizontalLength,
                    0.0,
                    sample.tangent().z() / horizontalLength
            );
            int surfaceY = floorToInt(sample.position().y() + 0.5);
            int pillarTopY = surfaceY - DECK_THICKNESS - 1;
            int minimumOffset = -(diameter / 2);
            int maximumOffset = minimumOffset + diameter - 1;
            int capReach = Math.min(4, Math.max(1, roadWidth / 4));
            int lowerCapReach = Math.max(1, capReach / 2);
            List<PierSite> sites = new java.util.ArrayList<>(
                    lateralOffsets.length
            );
            for (double lateralOffset : lateralOffsets) {
                RoadPoint center = sample.position().add(
                        left.scale(lateralOffset)
                );
                int centerX = floorToInt(center.x());
                int centerZ = floorToInt(center.z());
                Ground centerGround = groundBelow(
                        new BlockPos(centerX, surfaceY, centerZ),
                        world,
                        existingRoadPosition,
                        groundCache
                );
                int verticalGap = surfaceY - centerGround.position().getY();
                boolean active = centerGround.solid()
                        && (centerGround.fluidEncountered()
                        || verticalGap > MAXIMUM_SOLID_BASE_HEIGHT)
                        && !existingRoadPosition.test(
                                centerGround.position()
                        );
                PierSite site = new PierSite(
                        (int) Math.signum(lateralOffset),
                        active
                );
                sites.add(site);
                if (!active) {
                    continue;
                }

                Set<HorizontalColumn> shaft = localColumns(
                        center,
                        forward,
                        left,
                        minimumOffset,
                        maximumOffset,
                        minimumOffset,
                        maximumOffset
                );
                for (HorizontalColumn column : shaft) {
                    Ground ground = groundBelow(
                            new BlockPos(column.x(), surfaceY, column.z()),
                            world,
                            existingRoadPosition,
                            groundCache
                    );
                    int columnGap = surfaceY - ground.position().getY();
                    if (!ground.solid()
                            || (!ground.fluidEncountered()
                            && columnGap <= MAXIMUM_SOLID_BASE_HEIGHT)
                            || existingRoadPosition.test(ground.position())) {
                        continue;
                    }
                    for (int y = pillarTopY - 2;
                            y >= ground.position().getY();
                            y--) {
                        BlockPos position = new BlockPos(
                                column.x(),
                                y,
                                column.z()
                        );
                        if (existingRoadPosition.test(position)) {
                            break;
                        }
                        if (!put(
                                targets,
                                surfacePositions,
                                position,
                                materials.pillar(),
                                maximumAffectedBlocks
                        )) {
                            return false;
                        }
                    }
                }

                Set<HorizontalColumn> footing = localColumns(
                        center,
                        forward,
                        left,
                        minimumOffset,
                        maximumOffset,
                        minimumOffset - 1,
                        maximumOffset + 1
                );
                for (HorizontalColumn column : footing) {
                    Ground ground = groundBelow(
                            new BlockPos(column.x(), surfaceY, column.z()),
                            world,
                            existingRoadPosition,
                            groundCache
                    );
                    if (!ground.solid()
                            || existingRoadPosition.test(ground.position())) {
                        continue;
                    }
                    if (!put(
                            targets,
                            surfacePositions,
                            ground.position(),
                            materials.pillar(),
                            maximumAffectedBlocks
                    )) {
                        return false;
                    }
                }

                int topMinimum;
                int topMaximum;
                int lowerMinimum;
                int lowerMaximum;
                if (!paired) {
                    topMinimum = minimumOffset - capReach;
                    topMaximum = maximumOffset + capReach;
                    lowerMinimum = minimumOffset - lowerCapReach;
                    lowerMaximum = maximumOffset + lowerCapReach;
                } else if (site.outwardDirection() < 0) {
                    topMinimum = minimumOffset - capReach;
                    topMaximum = minimumOffset;
                    lowerMinimum = minimumOffset - lowerCapReach;
                    lowerMaximum = maximumOffset - 1;
                } else {
                    topMinimum = maximumOffset;
                    topMaximum = maximumOffset + capReach;
                    lowerMinimum = minimumOffset + 1;
                    lowerMaximum = maximumOffset + lowerCapReach;
                }
                if (!putPillarLayer(
                        targets,
                        surfacePositions,
                        localColumns(
                                center,
                                forward,
                                left,
                                minimumOffset,
                                maximumOffset,
                                topMinimum,
                                topMaximum
                        ),
                        pillarTopY,
                        existingRoadPosition,
                        maximumAffectedBlocks
                ) || !putPillarLayer(
                        targets,
                        surfacePositions,
                        localColumns(
                                center,
                                forward,
                                left,
                                minimumOffset,
                                maximumOffset,
                                lowerMinimum,
                                lowerMaximum
                        ),
                        pillarTopY - 1,
                        existingRoadPosition,
                        maximumAffectedBlocks
                )) {
                    return false;
                }
            }

            if (paired && sites.stream().allMatch(PierSite::active)) {
                int beamHalfWidth = (int) Math.floor(pairedOffset)
                        + diameter / 2;
                if (!putPillarLayer(
                        targets,
                        surfacePositions,
                        localColumns(
                                sample.position(),
                                forward,
                                left,
                                minimumOffset,
                                maximumOffset,
                                -beamHalfWidth,
                                beamHalfWidth
                        ),
                        pillarTopY - 3,
                        existingRoadPosition,
                        maximumAffectedBlocks
                )) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Double> pillarDistances(double length) {
        if (length <= PIER_END_INSET * 2.0) {
            return List.of(length / 2.0);
        }
        List<Double> distances = new java.util.ArrayList<>();
        for (double distance = PIER_END_INSET;
                distance <= length - PIER_END_INSET;
                distance += PIER_SPACING) {
            distances.add(distance);
        }
        return List.copyOf(distances);
    }

    private static Set<HorizontalColumn> localColumns(
            RoadPoint center,
            RoadVector forward,
            RoadVector left,
            int minimumLongitudinalOffset,
            int maximumLongitudinalOffset,
            int minimumLateralOffset,
            int maximumLateralOffset
    ) {
        Set<HorizontalColumn> columns = new HashSet<>();
        for (int longitudinal = minimumLongitudinalOffset;
                longitudinal <= maximumLongitudinalOffset;
                longitudinal++) {
            for (int lateral = minimumLateralOffset;
                    lateral <= maximumLateralOffset;
                    lateral++) {
                RoadPoint position = center
                        .add(forward.scale(longitudinal))
                        .add(left.scale(lateral));
                columns.add(new HorizontalColumn(
                        floorToInt(position.x()),
                        floorToInt(position.z())
                ));
            }
        }
        return columns;
    }

    private boolean putPillarLayer(
            Map<BlockPos, PlannedBlock> targets,
            Set<BlockPos> surfacePositions,
            Set<HorizontalColumn> columns,
            int y,
            Predicate<BlockPos> existingRoadPosition,
            int maximumAffectedBlocks
    ) {
        for (HorizontalColumn column : columns) {
            BlockPos position = new BlockPos(column.x(), y, column.z());
            if (existingRoadPosition.test(position)) {
                continue;
            }
            if (!put(
                    targets,
                    surfacePositions,
                    position,
                    materials.pillar(),
                    maximumAffectedBlocks
            )) {
                return false;
            }
        }
        return true;
    }

    private static Ground groundBelow(
            BlockPos surface,
            PlacementBlockWorld world,
            Predicate<BlockPos> existingRoadPosition,
            Map<GroundKey, Ground> cache
    ) {
        GroundKey key = new GroundKey(
                surface.getX(),
                surface.getY(),
                surface.getZ()
        );
        Ground cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        int minimumY = Math.max(
                world.minimumBuildHeight(),
                surface.getY() - MAXIMUM_GROUND_SCAN_DEPTH
        );
        boolean fluidEncountered = false;
        for (int y = surface.getY() - 1; y >= minimumY; y--) {
            BlockPos position = new BlockPos(surface.getX(), y, surface.getZ());
            if (existingRoadPosition.test(position)) {
                continue;
            }
            BlockState state = world.blockState(position);
            if (!state.getFluidState().isEmpty()) {
                fluidEncountered = true;
                continue;
            }
            if (!isSolidTerrain(state)) {
                continue;
            }
            Ground ground = new Ground(position, true, fluidEncountered);
            cache.put(key, ground);
            return ground;
        }
        Ground ground = new Ground(
                new BlockPos(surface.getX(), minimumY, surface.getZ()),
                false,
                fluidEncountered
        );
        cache.put(key, ground);
        return ground;
    }

    private static boolean isVegetation(BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && (state.canBeReplaced()
                || state.is(BlockTags.LEAVES)
                || state.getBlock() instanceof LeavesBlock);
    }

    private static boolean isSolidTerrain(BlockState state) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !isVegetation(state)
                && state.blocksMotion();
    }

    private static boolean isEdge(
            BlockPos position,
            Set<HorizontalColumn> footprint
    ) {
        for (int[] direction : CARDINAL_DIRECTIONS) {
            if (!footprint.contains(new HorizontalColumn(
                    position.getX() + direction[0],
                    position.getZ() + direction[1]
            ))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSideEdge(
            BlockPos position,
            Set<HorizontalColumn> footprint,
            List<StructureSpan> spans
    ) {
        for (int[] direction : CARDINAL_DIRECTIONS) {
            if (!footprint.contains(new HorizontalColumn(
                    position.getX() + direction[0],
                    position.getZ() + direction[1]
            )) && !isEndDirection(position, direction, spans)) {
                return true;
            }
        }
        return false;
    }

        private static boolean isEndDirection(
            BlockPos position,
            int[] direction,
            List<StructureSpan> spans
    ) {
        if (spans.isEmpty()) {
            return false;
        }
        double x = position.getX() + 0.5;
        double z = position.getZ() + 0.5;
        for (StructureSpan span : spans) {
            double radius = span.roadWidth() / 2.0 + 1.0;
            PathSample start = span.geometry().sample(0.0);
            if (horizontalDistanceSquared(x, z, start.position())
                    <= radius * radius
                    && horizontalDot(direction, start.tangent()) < -0.5) {
                return true;
            }
            PathSample end = span.geometry().sample(span.geometry().length());
            if (horizontalDistanceSquared(x, z, end.position())
                    <= radius * radius
                    && horizontalDot(direction, end.tangent()) > 0.5) {
                return true;
            }
        }
        return false;
    }

    private static double horizontalDistanceSquared(
            double x,
            double z,
            RoadPoint point
    ) {
        double xOffset = x - point.x();
        double zOffset = z - point.z();
        return xOffset * xOffset + zOffset * zOffset;
    }

    private static double horizontalDot(int[] direction, RoadVector vector) {
        return direction[0] * vector.x() + direction[1] * vector.z();
    }

    private static boolean put(
            Map<BlockPos, PlannedBlock> targets,
            Set<BlockPos> surfacePositions,
            BlockPos position,
            BlockState state,
            int maximumAffectedBlocks
    ) {
        if (surfacePositions.contains(position)) {
            return true;
        }
        if (!targets.containsKey(position)
                && targets.size() >= maximumAffectedBlocks) {
            return false;
        }
        targets.put(position, new PlannedBlock(position, state));
        return true;
    }

    private static int floorToInt(double value) {
        double floor = Math.floor(value);
        if (!Double.isFinite(floor)
                || floor < Integer.MIN_VALUE
                || floor > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "structure coordinate is out of range"
            );
        }
        return (int) floor;
    }

    private static BlockPlanResult.Failure failure() {
        return new BlockPlanResult.Failure(
                BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED
        );
    }

    public record StructureSpan(
            ReferenceGeometry geometry,
            int roadWidth,
            List<RoadSurfaceBand> surfaceBands
    ) {
        public StructureSpan(ReferenceGeometry geometry, int roadWidth) {
            this(geometry, roadWidth, List.of());
        }

        public StructureSpan {
            surfaceBands = List.copyOf(Objects.requireNonNull(surfaceBands, "surfaceBands"));
            if (!surfaceBands.isEmpty()
                    && surfaceBands.stream().mapToInt(RoadSurfaceBand::width).sum() != roadWidth) {
                throw new IllegalArgumentException("surface bands must match road width");
            }
            Objects.requireNonNull(geometry, "geometry");
            if (roadWidth < 1) {
                throw new IllegalArgumentException(
                        "roadWidth must be positive"
                );
            }
        }
    }

    private record HorizontalColumn(int x, int z) {
    }

    private record PierSite(
            int outwardDirection,
            boolean active
    ) {
    }

    private record EndpointDeckZone(double x, double z, double radius) {
        boolean contains(BlockPos position) {
            double deltaX = position.getX() + 0.5 - x;
            double deltaZ = position.getZ() + 0.5 - z;
            return deltaX * deltaX + deltaZ * deltaZ <= radius * radius;
        }
    }

    private record EmbankmentCandidate(int topY, int clearanceY) {
    }

    private record GroundKey(int x, int surfaceY, int z) {
    }

    private record Ground(
            BlockPos position,
            boolean solid,
            boolean fluidEncountered
    ) {
    }
}

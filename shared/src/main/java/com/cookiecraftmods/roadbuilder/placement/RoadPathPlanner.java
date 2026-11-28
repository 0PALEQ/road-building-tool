package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public final class RoadPathPlanner {
    public static final double MAXIMUM_PATH_STEP = 0.25;
    private static final double TRAVERSAL_TOLERANCE = 1.0e-12;
    private static final double GRADE_TOLERANCE = 1.0e-12;
    private static final int MINIMUM_PARALLEL_SPANS = 16_384;
    private static final int SPANS_PER_TASK = 2_048;
    private static final int MAXIMUM_CANDIDATES_PER_TASK = 8_192;
    private static final int MAXIMUM_TASKS_PER_BATCH = 8;

    private final int maximumTasksPerBatch;

    public RoadPathPlanner() {
        this(MAXIMUM_TASKS_PER_BATCH);
    }

    RoadPathPlanner(int maximumTasksPerBatch) {
        this.maximumTasksPerBatch = maximumTasksPerBatch;
    }

    public BlockPlanResult plan(
            RoadPathPlanRequest request,
            int maximumAffectedBlocks,
            int maximumPlanningCandidates
    ) {
        return plan(
                request,
                maximumAffectedBlocks,
                maximumPlanningCandidates,
                ConstructionLimits.DEFAULT_MAXIMUM_GRADE
        );
    }

    public BlockPlanResult plan(
            RoadPathPlanRequest request,
            int maximumAffectedBlocks,
            int maximumPlanningCandidates,
            double maximumGrade
    ) {
        if (request == null) {
            throw new NullPointerException("request");
        }
        if (maximumAffectedBlocks <= 0) {
            throw new IllegalArgumentException(
                    "maximumAffectedBlocks must be greater than zero"
            );
        }
        if (maximumPlanningCandidates <= 0) {
            throw new IllegalArgumentException(
                    "maximumPlanningCandidates must be greater than zero"
            );
        }
        if (!Double.isFinite(maximumGrade) || maximumGrade < 0.0) {
            throw new IllegalArgumentException(
                    "maximumGrade must be finite and non-negative"
            );
        }

        ReferenceGeometry geometry = request.geometry();
        long stepCountLong = Math.max(
                1L,
                (long) Math.ceil(geometry.length() / MAXIMUM_PATH_STEP)
        );
        long baseSampleWork = stepCountLong + 1L;
        if (stepCountLong > Integer.MAX_VALUE
                || baseSampleWork > maximumPlanningCandidates) {
            return failure(
                    BlockPlanFailureCode.RASTERIZATION_WORK_LIMIT_EXCEEDED
            );
        }
        int stepCount = (int) stepCountLong;

        List<PathSample> centerSamples = new ArrayList<>(stepCount + 1);
        for (int index = 0; index <= stepCount; index++) {
            double distance = index == stepCount
                    ? geometry.length()
                    : geometry.length() * index / stepCount;
            PathSample sample = geometry.sample(distance);
            if (!coordinateInRange(sample.position())) {
                return failure(BlockPlanFailureCode.COORDINATE_OUT_OF_RANGE);
            }
            if (grade(sample) - maximumGrade > GRADE_TOLERANCE) {
                return failure(BlockPlanFailureCode.GRADE_LIMIT_EXCEEDED);
            }
            centerSamples.add(sample);
        }

        ResolvedRoadSurface surface = request.surface();
        Rasterization rasterization = new Rasterization(centerSamples, surface, geometry.length());
        WorkBudget work = new WorkBudget(maximumPlanningCandidates, baseSampleWork);
        BlockCollector collector = new BlockCollector(maximumAffectedBlocks);
        if (!rasterization.collect(work, collector, maximumTasksPerBatch)) {
            return failure(work.failureCode());
        }
        if (collector.blocks.isEmpty()) {
            return failure(BlockPlanFailureCode.INVALID_GEOMETRY);
        }
        return new BlockPlanResult.Success(
                BlockPlacementPlan.create(request.dimension(), collector.blocks)
        );
    }

    private record Rasterization(
            List<PathSample> samples,
            ResolvedRoadSurface surface,
            double pathLength
    ) {
        private boolean collect(WorkBudget work, BlockCollector collector, int maximumTasksPerBatch) {
            long spans = (long) (samples.size() - 1) * surface.blockWidth();
            int tasksPerBatch = Math.min(maximumTasksPerBatch, ForkJoinPool.getCommonPoolParallelism());
            if (spans < MINIMUM_PARALLEL_SPANS || tasksPerBatch < 2
                    || work.remaining() < MINIMUM_PARALLEL_SPANS || hasFlatWholeBlockElevation()) {
                return rasterize(0, spans, work, collector::add);
            }

            for (long batchStart = 0; batchStart < spans; ) {
                long first = batchStart;
                if (work.remaining() < SPANS_PER_TASK) {
                    return rasterize(first, spans, work, collector::add);
                }
                int tasks = (int) Math.min(tasksPerBatch,
                        (spans - first + SPANS_PER_TASK - 1) / SPANS_PER_TASK);
                int taskBudget = (int) Math.min(work.remaining(), MAXIMUM_CANDIDATES_PER_TASK);
                List<Optional<List<RasterCandidate>>> batches = IntStream.range(0, tasks).parallel()
                        .mapToObj(task -> collectBatch(
                                first + (long) task * SPANS_PER_TASK, spans, taskBudget))
                        .toList();
                // Replay in column/span order so ties and limit failures match serial planning.
                for (Optional<List<RasterCandidate>> batch : batches) {
                    // Replay an incomplete worker batch through the serial path, where the
                    // remaining work and block limits determine which error comes first.
                    if (batch.isEmpty()) {
                        if (!rasterize(batchStart, Math.min(spans, batchStart + SPANS_PER_TASK),
                                work, collector::add)) {
                            return false;
                        }
                    } else {
                        for (RasterCandidate candidate : batch.orElseThrow()) {
                            if (!work.consume() || !collector.add(candidate.position(), candidate.state(),
                                    candidate.distanceSquared())) {
                                return false;
                            }
                        }
                    }
                    batchStart += SPANS_PER_TASK;
                }
            }
            return true;
        }

        private boolean hasFlatWholeBlockElevation() {
            // Whole-block placement skips slab resolution. For this case the ordered
            // merge costs about as much as the parallel work saves.
            double elevation = Math.rint(samples.get(0).position().y());
            return samples.stream().allMatch(sample -> Math.abs(sample.position().y() - elevation) <= 0.25);
        }

        private Optional<List<RasterCandidate>> collectBatch(long first, long spans, int maximumCandidates) {
            List<RasterCandidate> candidates = new ArrayList<>();
            WorkBudget work = new WorkBudget(maximumCandidates, 0);
            boolean complete = rasterize(first, Math.min(spans, first + SPANS_PER_TASK), work,
                    (position, state, distanceSquared) -> {
                        candidates.add(new RasterCandidate(position, state, distanceSquared));
                        return true;
                    });
            return complete ? Optional.of(candidates) : Optional.empty();
        }

        private boolean rasterize(long first, long end, WorkBudget work, CandidateSink sink) {
            int spansPerColumn = samples.size() - 1;
            int surfaceWidth = surface.blockWidth();
            while (first < end) {
                int lateralIndex = (int) (first / spansPerColumn);
                int sampleIndex = (int) (first % spansPerColumn);
                int spanCount = (int) Math.min(end - first, spansPerColumn - sampleIndex);
                int lastSample = sampleIndex + spanCount;
                double lateralOffset = (surfaceWidth - 1) / 2.0 - lateralIndex;
                PathSample previousSample = samples.get(sampleIndex);
                RoadPoint previous = offset(previousSample, lateralOffset);
                for (sampleIndex++; sampleIndex <= lastSample; sampleIndex++) {
                    PathSample nextSample = samples.get(sampleIndex);
                    RoadPoint next = offset(nextSample, lateralOffset);
                    if (!rasterizeSpan(previous, next, previousSample.distance(), nextSample.distance(),
                            surface, lateralIndex, pathLength, work, sink)) {
                        return false;
                    }
                    previous = next;
                    previousSample = nextSample;
                }
                first += spanCount;
            }
            return true;
        }
    }

    private static RoadPoint offset(
            PathSample sample,
            double lateralOffset
    ) {
        return lateralOffset == 0.0
                ? sample.position()
                : sample.offsetLaterally(lateralOffset).position();
    }

    private static boolean rasterizeSpan(
            RoadPoint start,
            RoadPoint end,
            double startDistance,
            double endDistance,
            ResolvedRoadSurface surface,
            int lateralIndex,
            double maximumPathDistance,
            WorkBudget work,
            CandidateSink sink
    ) {
        OptionalInt startX = floorToInt(start.x());
        OptionalInt startZ = floorToInt(start.z());
        OptionalInt endX = floorToInt(end.x());
        OptionalInt endZ = floorToInt(end.z());
        if (startX.isEmpty() || startZ.isEmpty()
                || endX.isEmpty() || endZ.isEmpty()) {
            work.markCoordinateFailure();
            return false;
        }

        int cellX = startX.getAsInt();
        int cellZ = startZ.getAsInt();
        int finalCellX = endX.getAsInt();
        int finalCellZ = endZ.getAsInt();
        if (!addTarget(
                cellX,
                cellZ,
                start,
                end,
                startDistance,
                endDistance,
                surface,
                lateralIndex,
                maximumPathDistance,
                work,
                sink
        )) {
            return false;
        }

        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        int stepX = Double.compare(deltaX, 0.0);
        int stepZ = Double.compare(deltaZ, 0.0);
        double tDeltaX = stepX == 0
                ? Double.POSITIVE_INFINITY
                : Math.abs(1.0 / deltaX);
        double tDeltaZ = stepZ == 0
                ? Double.POSITIVE_INFINITY
                : Math.abs(1.0 / deltaZ);
        double tMaxX = firstBoundaryTime(start.x(), cellX, deltaX, stepX);
        double tMaxZ = firstBoundaryTime(start.z(), cellZ, deltaZ, stepZ);

        while (cellX != finalCellX || cellZ != finalCellZ) {
            int comparison = compareTraversalTimes(tMaxX, tMaxZ);
            if (comparison < 0) {
                cellX += stepX;
                tMaxX += tDeltaX;
                if (!addTarget(
                        cellX, cellZ, start, end,
                        startDistance, endDistance, surface, lateralIndex,
                        maximumPathDistance,
                        work, sink
                )) {
                    return false;
                }
            } else if (comparison > 0) {
                cellZ += stepZ;
                tMaxZ += tDeltaZ;
                if (!addTarget(
                        cellX, cellZ, start, end,
                        startDistance, endDistance, surface, lateralIndex,
                        maximumPathDistance,
                        work, sink
                )) {
                    return false;
                }
            } else {
                int nextX = cellX + stepX;
                int nextZ = cellZ + stepZ;
                if (!addTarget(
                        nextX, cellZ, start, end,
                        startDistance, endDistance, surface, lateralIndex,
                        maximumPathDistance,
                        work, sink
                ) || !addTarget(
                        cellX, nextZ, start, end,
                        startDistance, endDistance, surface, lateralIndex,
                        maximumPathDistance,
                        work, sink
                ) || !addTarget(
                        nextX, nextZ, start, end,
                        startDistance, endDistance, surface, lateralIndex,
                        maximumPathDistance,
                        work, sink
                )) {
                    return false;
                }
                cellX = nextX;
                cellZ = nextZ;
                tMaxX += tDeltaX;
                tMaxZ += tDeltaZ;
            }
        }
        return true;
    }

    private static boolean addTarget(
            int blockX,
            int blockZ,
            RoadPoint start,
            RoadPoint end,
            double startDistance,
            double endDistance,
            ResolvedRoadSurface surface,
            int lateralIndex,
            double maximumPathDistance,
            WorkBudget work,
            CandidateSink sink
    ) {
        if (!work.consume()) {
            return false;
        }
        double projectedParameter = parameterAt(
                blockX,
                blockZ,
                start,
                end
        );
        double elevationParameter = Math.max(
                0.0,
                Math.min(1.0, projectedParameter)
        );
        double elevation = elevationAt(
                start,
                end,
                elevationParameter
        );
        OptionalInt rangeCheck = nearestBlockToInt(elevation);
        if (rangeCheck.isEmpty()) {
            work.markCoordinateFailure();
            return false;
        }
        double pathDistance = startDistance
                + (endDistance - startDistance) * elevationParameter;
        pathDistance = Math.max(
                0.0,
                Math.min(maximumPathDistance, pathDistance)
        );
        double verticalLevel = Math.rint(elevation * 2.0) / 2.0;
        ResolvedRoadSurface.SurfacePlacement target = surface.targetPlacement(
                lateralIndex,
                pathDistance,
                blockX,
                blockZ,
                verticalLevel
        );
        BlockPos position = target.position();
        // Adjacent swept columns overlap on the voxel grid. Assign the cell
        // to the nearest column span, rather than the first column visited.
        double dx = blockX + 0.5
                - (start.x() + (end.x() - start.x()) * elevationParameter);
        double dz = blockZ + 0.5
                - (start.z() + (end.z() - start.z()) * elevationParameter);
        double distanceSquared = dx * dx + dz * dz;
        return sink.add(position, target.state(), distanceSquared);
    }

    private static double parameterAt(
            int blockX,
            int blockZ,
            RoadPoint start,
            RoadPoint end
    ) {
        double deltaX = end.x() - start.x();
        double deltaZ = end.z() - start.z();
        double horizontalLengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        if (horizontalLengthSquared == 0.0) {
            return 0.5;
        }
        double parameter = ((blockX + 0.5 - start.x()) * deltaX
                + (blockZ + 0.5 - start.z()) * deltaZ)
                / horizontalLengthSquared;
        return parameter;
    }

    private static double elevationAt(
            RoadPoint start,
            RoadPoint end,
            double parameter
    ) {
        return start.y() + (end.y() - start.y()) * parameter;
    }

    private static double grade(PathSample sample) {
        double horizontal = sample.tangent().horizontalLength();
        return horizontal == 0.0
                ? Double.POSITIVE_INFINITY
                : Math.abs(sample.tangent().y()) / horizontal;
    }

    private static boolean coordinateInRange(RoadPoint point) {
        return floorToInt(point.x()).isPresent()
                && floorToInt(point.z()).isPresent()
                && nearestBlockToInt(point.y()).isPresent();
    }

    private static double firstBoundaryTime(
            double start,
            int startCell,
            double delta,
            int step
    ) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? startCell + 1.0 : startCell;
        return (boundary - start) / delta;
    }

    private static int compareTraversalTimes(double first, double second) {
        if (Double.doubleToLongBits(first)
                == Double.doubleToLongBits(second)) {
            return 0;
        }
        if (!Double.isFinite(first) || !Double.isFinite(second)) {
            return Double.compare(first, second);
        }
        double scale = Math.max(
                1.0,
                Math.max(Math.abs(first), Math.abs(second))
        );
        if (Math.abs(first - second) <= TRAVERSAL_TOLERANCE * scale) {
            return 0;
        }
        return Double.compare(first, second);
    }

    private static OptionalInt floorToInt(double value) {
        double floored = Math.floor(value);
        if (!Double.isFinite(floored)
                || floored < Integer.MIN_VALUE
                || floored > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) floored);
    }

    private static OptionalInt nearestBlockToInt(double value) {
        return floorToInt(value + 0.5);
    }

    private static BlockPlanResult.Failure failure(
            BlockPlanFailureCode code
    ) {
        return new BlockPlanResult.Failure(code);
    }

    @FunctionalInterface
    private interface CandidateSink {
        boolean add(BlockPos position, BlockState state, double distanceSquared);
    }

    private record RasterCandidate(BlockPos position, BlockState state, double distanceSquared) {}

    private record CellCandidate(int index, double distanceSquared) {}

    private static final class BlockCollector {
        private final int maximumAffectedBlocks;
        private final List<PlannedBlock> blocks = new ArrayList<>();
        private final Map<BlockPos, CellCandidate> occupiedPositions = new HashMap<>();

        private BlockCollector(int maximumAffectedBlocks) {
            this.maximumAffectedBlocks = maximumAffectedBlocks;
        }

        private boolean add(BlockPos position, BlockState state, double distanceSquared) {
            CellCandidate previous = occupiedPositions.get(position);
            if (previous != null && previous.distanceSquared() <= distanceSquared + 1.0e-12) {
                return true;
            }
            PlannedBlock block = new PlannedBlock(position, state);
            if (previous == null) {
                if (blocks.size() >= maximumAffectedBlocks) {
                    return false;
                }
                occupiedPositions.put(position, new CellCandidate(blocks.size(), distanceSquared));
                blocks.add(block);
            } else {
                occupiedPositions.put(position, new CellCandidate(previous.index(), distanceSquared));
                blocks.set(previous.index(), block);
            }
            return true;
        }
    }

    private static final class WorkBudget {
        private final long maximum;
        private long consumed;
        private boolean coordinateFailure;

        private WorkBudget(long maximum, long consumed) {
            this.maximum = maximum;
            this.consumed = consumed;
        }

        private long remaining() {
            return maximum - consumed;
        }

        private boolean consume() {
            if (consumed >= maximum) {
                return false;
            }
            consumed++;
            return true;
        }

        private boolean exhausted() {
            return consumed >= maximum && !coordinateFailure;
        }

        private void markCoordinateFailure() {
            coordinateFailure = true;
        }

        private BlockPlanFailureCode failureCode() {
            if (coordinateFailure) {
                return BlockPlanFailureCode.COORDINATE_OUT_OF_RANGE;
            }
            return exhausted()
                    ? BlockPlanFailureCode.RASTERIZATION_WORK_LIMIT_EXCEEDED
                    : BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED;
        }
    }
}

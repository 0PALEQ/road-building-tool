package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

public final class RoadPathPlanner {
    public static final double MAXIMUM_PATH_STEP = 0.25;
    private static final double TRAVERSAL_TOLERANCE = 1.0e-12;
    private static final double GRADE_TOLERANCE = 1.0e-12;

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

        List<PlannedBlock> blocks = new ArrayList<>();
        Set<BlockPos> occupiedPositions = new HashSet<>();
        WorkBudget work = new WorkBudget(
                maximumPlanningCandidates,
                baseSampleWork
        );
        int surfaceWidth = request.surface().blockWidth();
        for (int lateralIndex = 0; lateralIndex < surfaceWidth; lateralIndex++) {
            double lateralOffset = (surfaceWidth - 1) / 2.0
                    - lateralIndex;
            PathSample previousSample = centerSamples.get(0);
            RoadPoint previous = offset(previousSample, lateralOffset);
            for (int sampleIndex = 1;
                 sampleIndex < centerSamples.size();
                 sampleIndex++) {
                PathSample nextSample = centerSamples.get(sampleIndex);
                RoadPoint next = offset(
                        nextSample,
                        lateralOffset
                );
                if (!rasterizeSpan(
                        previous,
                        next,
                        previousSample.distance(),
                        nextSample.distance(),
                        request.surface(),
                        lateralIndex,
                        geometry.length(),
                        maximumAffectedBlocks,
                        work,
                        blocks,
                        occupiedPositions
                )) {
                    return failure(work.failureCode());
                }
                previous = next;
                previousSample = nextSample;
            }
        }
        if (blocks.isEmpty()) {
            return failure(BlockPlanFailureCode.INVALID_GEOMETRY);
        }
        return new BlockPlanResult.Success(
                BlockPlacementPlan.create(request.dimension(), blocks)
        );
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
            int maximumAffectedBlocks,
            WorkBudget work,
            List<PlannedBlock> blocks,
            Set<BlockPos> occupiedPositions
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
                maximumAffectedBlocks,
                work,
                blocks,
                occupiedPositions
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
                        maximumAffectedBlocks, work, blocks, occupiedPositions
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
                        maximumAffectedBlocks, work, blocks, occupiedPositions
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
                        maximumAffectedBlocks, work, blocks, occupiedPositions
                ) || !addTarget(
                        cellX, nextZ, start, end,
                        startDistance, endDistance, surface, lateralIndex,
                        maximumPathDistance,
                        maximumAffectedBlocks, work, blocks, occupiedPositions
                ) || !addTarget(
                        nextX, nextZ, start, end,
                        startDistance, endDistance, surface, lateralIndex,
                        maximumPathDistance,
                        maximumAffectedBlocks, work, blocks, occupiedPositions
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
            int maximumAffectedBlocks,
            WorkBudget work,
            List<PlannedBlock> blocks,
            Set<BlockPos> occupiedPositions
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
                + (endDistance - startDistance) * projectedParameter;
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
        if (!occupiedPositions.add(position)) {
            return true;
        }
        if (blocks.size() >= maximumAffectedBlocks) {
            return false;
        }
        blocks.add(new PlannedBlock(
                position,
                target.state()
        ));
        return true;
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

    private static final class WorkBudget {
        private final long maximum;
        private long consumed;
        private boolean coordinateFailure;

        private WorkBudget(long maximum, long consumed) {
            this.maximum = maximum;
            this.consumed = consumed;
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

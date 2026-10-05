package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

public final class StraightRoadPlanner {
    private static final double TRAVERSAL_TOLERANCE = 1.0e-12;
    private static final double GRADE_TOLERANCE = 1.0e-12;

    public BlockPlanResult plan(
            StraightRoadPlanRequest request,
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
            StraightRoadPlanRequest request,
            int maximumAffectedBlocks,
            int maximumPlanningCandidates,
            double maximumGrade
    ) {
        if (request == null) {
            throw new NullPointerException("request");
        }
        if (maximumAffectedBlocks <= 0) {
            throw new IllegalArgumentException("maximumAffectedBlocks must be greater than zero");
        }
        if (maximumPlanningCandidates <= 0) {
            throw new IllegalArgumentException("maximumPlanningCandidates must be greater than zero");
        }
        if (!Double.isFinite(maximumGrade) || maximumGrade < 0.0) {
            throw new IllegalArgumentException("maximumGrade must be finite and non-negative");
        }

        StraightGeometry geometry = request.geometry();
        RoadPoint start = geometry.start();
        RoadPoint end = geometry.end();
        ElevationProfile elevation = new ElevationProfile(start, end);
        if (elevation.grade() - maximumGrade > GRADE_TOLERANCE) {
            return failure(BlockPlanFailureCode.GRADE_LIMIT_EXCEEDED);
        }
        if (nearestBlockToInt(start.y()).isEmpty()
                || nearestBlockToInt(end.y()).isEmpty()) {
            return failure(BlockPlanFailureCode.COORDINATE_OUT_OF_RANGE);
        }

        RoadVector left = geometry.tangent().horizontalLeftUnit();
        int surfaceBlockWidth = request.surface().blockWidth();
        List<RasterLine> rasterLines = new ArrayList<>(surfaceBlockWidth);
        long estimatedCandidates = 0L;

        for (int lateralIndex = 0; lateralIndex < surfaceBlockWidth; lateralIndex++) {
            double lateralOffset = (surfaceBlockWidth - 1) / 2.0
                    - lateralIndex;
            RasterLine line = createRasterLine(
                    start,
                    end,
                    left,
                    lateralOffset,
                    lateralIndex,
                    geometry.length()
            );
            if (line == null) {
                return failure(BlockPlanFailureCode.COORDINATE_OUT_OF_RANGE);
            }

            long lineCandidates = line.maximumCandidateCount();
            if (lineCandidates > maximumPlanningCandidates - estimatedCandidates) {
                return failure(BlockPlanFailureCode.RASTERIZATION_WORK_LIMIT_EXCEEDED);
            }
            estimatedCandidates += lineCandidates;
            rasterLines.add(line);
        }

        int initialCapacity = (int) Math.min(maximumAffectedBlocks, estimatedCandidates);
        List<PlannedBlock> blocks = new ArrayList<>(initialCapacity);
        Set<BlockPos> occupiedPositions = new HashSet<>(initialCapacity);
        for (RasterLine line : rasterLines) {
            if (!rasterizeLine(
                    line,
                    elevation,
                    request.surface(),
                    maximumAffectedBlocks,
                    blocks,
                    occupiedPositions
            )) {
                return failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED);
            }
        }

        return new BlockPlanResult.Success(BlockPlacementPlan.create(request.dimension(), blocks));
    }

    private static RasterLine createRasterLine(
            RoadPoint start,
            RoadPoint end,
            RoadVector left,
            double lateralOffset,
            int lateralIndex,
            double pathLength
    ) {
        double startX = start.x() + left.x() * lateralOffset;
        double startZ = start.z() + left.z() * lateralOffset;
        double endX = end.x() + left.x() * lateralOffset;
        double endZ = end.z() + left.z() * lateralOffset;
        OptionalInt startCellX = floorToInt(startX);
        OptionalInt startCellZ = floorToInt(startZ);
        OptionalInt endCellX = floorToInt(endX);
        OptionalInt endCellZ = floorToInt(endZ);
        if (startCellX.isEmpty() || startCellZ.isEmpty() || endCellX.isEmpty() || endCellZ.isEmpty()) {
            return null;
        }
        return new RasterLine(
                startX,
                startZ,
                endX,
                endZ,
                startCellX.getAsInt(),
                startCellZ.getAsInt(),
                endCellX.getAsInt(),
                endCellZ.getAsInt(),
                lateralIndex,
                pathLength
        );
    }

        private static boolean rasterizeLine(
            RasterLine line,
            ElevationProfile elevation,
            ResolvedRoadSurface surface,
            int maximumAffectedBlocks,
            List<PlannedBlock> blocks,
            Set<BlockPos> occupiedPositions
    ) {
        int cellX = line.startCellX();
        int cellZ = line.startCellZ();
        if (!addTarget(
                cellX,
                cellZ,
                elevation,
                line,
                surface,
                maximumAffectedBlocks,
                blocks,
                occupiedPositions
        )) {
            return false;
        }

        double deltaX = line.endX() - line.startX();
        double deltaZ = line.endZ() - line.startZ();
        int stepX = Double.compare(deltaX, 0.0);
        int stepZ = Double.compare(deltaZ, 0.0);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / deltaX);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / deltaZ);
        double tMaxX = firstBoundaryTime(line.startX(), cellX, deltaX, stepX);
        double tMaxZ = firstBoundaryTime(line.startZ(), cellZ, deltaZ, stepZ);

        while (cellX != line.endCellX() || cellZ != line.endCellZ()) {
            int timeComparison = compareTraversalTimes(tMaxX, tMaxZ);
            if (timeComparison < 0) {
                cellX += stepX;
                tMaxX += tDeltaX;
                if (!addTarget(
                        cellX, cellZ, elevation, line, surface,
                        maximumAffectedBlocks, blocks, occupiedPositions
                )) {
                    return false;
                }
            } else if (timeComparison > 0) {
                cellZ += stepZ;
                tMaxZ += tDeltaZ;
                if (!addTarget(
                        cellX, cellZ, elevation, line, surface,
                        maximumAffectedBlocks, blocks, occupiedPositions
                )) {
                    return false;
                }
            } else {
                int nextX = cellX + stepX;
                int nextZ = cellZ + stepZ;
                if (!addTarget(
                        nextX, cellZ, elevation, line, surface,
                        maximumAffectedBlocks, blocks, occupiedPositions
                ) || !addTarget(
                        cellX, nextZ, elevation, line, surface,
                        maximumAffectedBlocks, blocks, occupiedPositions
                ) || !addTarget(
                        nextX, nextZ, elevation, line, surface,
                        maximumAffectedBlocks, blocks, occupiedPositions
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

    private static double firstBoundaryTime(double start, int startCell, double delta, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double boundary = step > 0 ? startCell + 1.0 : startCell;
        return (boundary - start) / delta;
    }

    private static int compareTraversalTimes(double first, double second) {
        if (Double.doubleToLongBits(first) == Double.doubleToLongBits(second)) {
            return 0;
        }
        if (!Double.isFinite(first) || !Double.isFinite(second)) {
            return Double.compare(first, second);
        }
        double scale = Math.max(1.0, Math.max(Math.abs(first), Math.abs(second)));
        if (Math.abs(first - second) <= TRAVERSAL_TOLERANCE * scale) {
            return 0;
        }
        return Double.compare(first, second);
    }

    private static boolean addTarget(
            int blockX,
            int blockZ,
            ElevationProfile elevation,
            RasterLine line,
            ResolvedRoadSurface surface,
            int maximumAffectedBlocks,
            List<PlannedBlock> blocks,
            Set<BlockPos> occupiedPositions
    ) {
        double verticalLevel = elevation.halfBlockLevelAt(blockX, blockZ);
        ResolvedRoadSurface.SurfacePlacement target = surface.targetPlacement(
                line.lateralIndex(),
                line.pathDistanceAt(blockX, blockZ),
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

    private static OptionalInt floorToInt(double value) {
        double floored = Math.floor(value);
        if (!Double.isFinite(floored) || floored < Integer.MIN_VALUE || floored > Integer.MAX_VALUE) {
            return OptionalInt.empty();
        }
        return OptionalInt.of((int) floored);
    }

    private static OptionalInt nearestBlockToInt(double value) {
        return floorToInt(value + 0.5);
    }

    private static BlockPlanResult.Failure failure(BlockPlanFailureCode code) {
        return new BlockPlanResult.Failure(code);
    }

    private record RasterLine(
            double startX,
            double startZ,
            double endX,
            double endZ,
            int startCellX,
            int startCellZ,
            int endCellX,
            int endCellZ,
            int lateralIndex,
            double pathLength
    ) {
        long maximumCandidateCount() {
            long xCrossings = Math.abs((long) endCellX - startCellX);
            long zCrossings = Math.abs((long) endCellZ - startCellZ);
            return 1L + xCrossings + zCrossings + Math.min(xCrossings, zCrossings);
        }

        double pathDistanceAt(int blockX, int blockZ) {
            double deltaX = endX - startX;
            double deltaZ = endZ - startZ;
            double horizontalLengthSquared = deltaX * deltaX
                    + deltaZ * deltaZ;
            double parameter = ((blockX + 0.5 - startX) * deltaX
                    + (blockZ + 0.5 - startZ) * deltaZ)
                    / horizontalLengthSquared;
            return pathLength * Math.max(0.0, Math.min(1.0, parameter));
        }
    }

    private record ElevationProfile(
            double startX,
            double startY,
            double startZ,
            double deltaX,
            double deltaY,
            double deltaZ,
            double horizontalLengthSquared,
            double grade
    ) {
        ElevationProfile(RoadPoint start, RoadPoint end) {
            this(
                    start.x(),
                    start.y(),
                    start.z(),
                    end.x() - start.x(),
                    end.y() - start.y(),
                    end.z() - start.z(),
                    Math.pow(end.x() - start.x(), 2.0)
                            + Math.pow(end.z() - start.z(), 2.0),
                    Math.abs(end.y() - start.y())
                            / Math.hypot(end.x() - start.x(), end.z() - start.z())
            );
        }

        double halfBlockLevelAt(int blockX, int blockZ) {
            double centerX = blockX + 0.5;
            double centerZ = blockZ + 0.5;
            double parameter = ((centerX - startX) * deltaX
                    + (centerZ - startZ) * deltaZ) / horizontalLengthSquared;
            double clampedParameter = Math.max(0.0, Math.min(1.0, parameter));
            return Math.rint((startY + deltaY * clampedParameter) * 2.0)
                    / 2.0;
        }
    }
}

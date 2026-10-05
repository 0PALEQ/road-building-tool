package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodePortResolver;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SmoothRoadTransitionMaterializer {
    private static final double SAMPLE_STEP = 0.20;
    private static final double LATERAL_SAMPLE_STEP = 0.20;
    private static final double MAXIMUM_TRANSITION_TURN = Math.toRadians(110.0);
    private static final double CELL_MARGIN = Math.sqrt(0.5);

    public static boolean supports(RoadNode node, List<RoadSegment> incident) {
        Objects.requireNonNull(node, "node");
        incident = List.copyOf(Objects.requireNonNull(incident, "incident"));
        if (incident.size() != 2
                || incident.get(0).presetSnapshot().surfaceBlockWidth()
                == incident.get(1).presetSnapshot().surfaceBlockWidth()
                || incident.stream().anyMatch(segment ->
                        !RoadNodePortResolver.isCenteredBoundary(
                                node,
                                segment
                        ))) {
            return false;
        }
        RoadVector first = outwardTangent(incident.get(0), node);
        RoadVector second = outwardTangent(incident.get(1), node);
        double lengths = first.horizontalLength() * second.horizontalLength();
        if (lengths == 0.0) {
            return false;
        }
        double dot = (first.x() * second.x() + first.z() * second.z()) / lengths;
        return dot <= -Math.cos(MAXIMUM_TRANSITION_TURN);
    }

    public BlockPlanResult plan(
            ResourceKey<Level> dimension,
            RoadNode node,
            List<RoadSegment> incident,
            int maximumAffectedBlocks,
            int maximumPlanningCandidates
    ) {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(node, "node");
        incident = List.copyOf(Objects.requireNonNull(incident, "incident"));
        if (!supports(node, incident)) {
            return failure(BlockPlanFailureCode.INVALID_GEOMETRY);
        }

        RoadSegment first = incident.get(0);
        RoadSegment second = incident.get(1);
        RoadSegment narrow = first.presetSnapshot().surfaceBlockWidth()
                < second.presetSnapshot().surfaceBlockWidth() ? first : second;
        RoadSegment wide = narrow == first ? second : first;
        CrossSection narrowSection = crossSection(narrow, node);
        CrossSection wideSection = crossSection(wide, node);
        Optional<ResolvedRoadSurface> narrowSurface = surface(narrow);
        Optional<ResolvedRoadSurface> wideSurface = surface(wide);
        Optional<SurfaceMaterials> narrowMaterials = materials(narrow);
        Optional<SurfaceMaterials> wideMaterials = materials(wide);
        if (narrowMaterials.isEmpty() || wideMaterials.isEmpty()
                || narrowSurface.isEmpty() || wideSurface.isEmpty()) {
            return failure(BlockPlanFailureCode.UNKNOWN_SURFACE_BLOCK);
        }
        narrowSection = narrowSection.withSurface(narrowSurface.orElseThrow());
        wideSection = wideSection.withSurface(wideSurface.orElseThrow());

        double widthDifference = wideSection.width() - narrowSection.width();
        double requestedTransitionLength = Math.max(
                Math.max(8.0, widthDifference * 3.0),
                transitionTurn(node, incident) * 8.0
        );
        double transitionLength = Math.min(
                narrow.referenceGeometry().length(),
                requestedTransitionLength
        );
        ReferenceGeometry transitionGeometry = transitionGeometry(
                node,
                narrow,
                wide,
                transitionLength
        );
        int longitudinalSteps = Math.max(
                1,
                (int) Math.ceil(transitionGeometry.length() / SAMPLE_STEP)
        );
        int lateralSteps = Math.max(
                1,
                (int) Math.ceil(wideSection.width() / LATERAL_SAMPLE_STEP)
        );
        long estimatedWork = (long) (longitudinalSteps + 1)
                * (lateralSteps + 1L);
        if (estimatedWork > maximumPlanningCandidates) {
            return failure(BlockPlanFailureCode.RASTERIZATION_WORK_LIMIT_EXCEEDED);
        }

        List<MarkingTransition> markingTransitions = markingTransitions(
                wideSection,
                narrowSection
        );
        Map<BlockPos, PlannedBlock> blocks = new LinkedHashMap<>();
        for (int longitudinalIndex = 0;
             longitudinalIndex <= longitudinalSteps;
             longitudinalIndex++) {
            double curveDistance = transitionGeometry.length()
                    * longitudinalIndex / longitudinalSteps;
            double distanceFromNode = transitionLength
                    * longitudinalIndex / longitudinalSteps;
            PathSample sample = transitionGeometry.sample(curveDistance);
            RoadVector outward = sample.tangent();
            RoadVector right = horizontalRight(outward);
            double progress = distanceFromNode / transitionLength;
            double eased = progress * progress * (3.0 - 2.0 * progress);
            double halfWidth = interpolate(
                    wideSection.width() / 2.0,
                    narrowSection.width() / 2.0,
                    eased
            );
            double negativeSidewalk = interpolate(
                    wideSection.negativeSidewalkWidth(),
                    narrowSection.negativeSidewalkWidth(),
                    eased
            );
            double positiveSidewalk = interpolate(
                    wideSection.positiveSidewalkWidth(),
                    narrowSection.positiveSidewalkWidth(),
                    eased
            );
            SurfaceMaterials selectedMaterials = progress < 0.5
                    ? wideMaterials.orElseThrow()
                    : narrowMaterials.orElseThrow();

            for (int lateralIndex = 0;
                 lateralIndex <= lateralSteps;
                 lateralIndex++) {
                double lateral = -wideSection.width() / 2.0
                        + wideSection.width() * lateralIndex / lateralSteps;
                if (Math.abs(lateral) > halfWidth + CELL_MARGIN) {
                    continue;
                }
                RoadPoint position = sample.position().add(right.scale(lateral));
                int blockX = floorToInt(position.x());
                int blockZ = floorToInt(position.z());
                double cellLateral = (blockX + 0.5 - sample.position().x())
                        * right.x()
                        + (blockZ + 0.5 - sample.position().z()) * right.z();
                if (Math.abs(cellLateral) > halfWidth + CELL_MARGIN) {
                    continue;
                }
                boolean targetSidewalk = cellLateral < 0.0
                        ? cellLateral <= -halfWidth + negativeSidewalk
                        : cellLateral >= halfWidth - positiveSidewalk;
                ResolvedRoadSurface surface = targetSidewalk
                        ? selectedMaterials.sidewalk()
                        : selectedMaterials.pavement();
                double verticalLevel = Math.rint(sample.position().y() * 2.0) / 2.0;
                ResolvedRoadSurface.SurfacePlacement target = surface.targetPlacement(
                        0,
                        distanceFromNode,
                        blockX,
                        blockZ,
                        verticalLevel
                );
                blocks.put(target.position(), new PlannedBlock(
                        target.position(),
                        target.state()
                ));
                if (blocks.size() > maximumAffectedBlocks) {
                    return failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED);
                }
            }
            for (MarkingTransition marking : markingTransitions) {
                Optional<MarkingColumn> selected = marking.column(progress);
                if (selected.isEmpty()) {
                    continue;
                }
                double lateral = marking.lateralOffset(eased);
                RoadPoint position = sample.position().add(right.scale(lateral));
                int blockX = floorToInt(position.x());
                int blockZ = floorToInt(position.z());
                double verticalLevel = Math.rint(sample.position().y() * 2.0)
                        / 2.0;
                ResolvedRoadSurface.SurfacePlacement target = selected
                        .orElseThrow()
                        .targetPlacement(
                                distanceFromNode,
                                blockX,
                                blockZ,
                                verticalLevel
                        );
                blocks.put(target.position(), new PlannedBlock(
                        target.position(),
                        target.state()
                ));
                if (blocks.size() > maximumAffectedBlocks) {
                    return failure(BlockPlanFailureCode.BLOCK_LIMIT_EXCEEDED);
                }
            }
        }
        if (blocks.isEmpty()) {
            return failure(BlockPlanFailureCode.INVALID_GEOMETRY);
        }
        return new BlockPlanResult.Success(BlockPlacementPlan.create(
                dimension,
                new ArrayList<>(blocks.values())
        ));
    }

    private static CrossSection crossSection(RoadSegment segment, RoadNode node) {
        List<RoadSurfaceRole> roles = new ArrayList<>(
                segment.presetSnapshot().surfaceColumnRoles()
        );
        if (segment.endNodeId().equals(node.id())) {
            Collections.reverse(roles);
        }
        int negativeSidewalk = 0;
        while (negativeSidewalk < roles.size()
                && roles.get(negativeSidewalk) == RoadSurfaceRole.SIDEWALK) {
            negativeSidewalk++;
        }
        int positiveSidewalk = 0;
        for (int index = roles.size() - 1;
             index >= 0 && roles.get(index) == RoadSurfaceRole.SIDEWALK;
             index--) {
            positiveSidewalk++;
        }
        return new CrossSection(
                roles,
                negativeSidewalk,
                positiveSidewalk,
                null,
                segment.endNodeId().equals(node.id())
        );
    }

    private static Optional<ResolvedRoadSurface> surface(RoadSegment segment) {
        RoadSurfaceResolver.Result result = RoadSurfaceResolver.resolve(
                segment.presetSnapshot().surfaceColumns()
        );
        return result instanceof RoadSurfaceResolver.Result.Success success
                ? Optional.of(success.surface())
                : Optional.empty();
    }

    private static ReferenceGeometry transitionGeometry(
            RoadNode node,
            RoadSegment narrow,
            RoadSegment wide,
            double transitionLength
    ) {
        boolean narrowStartsAtNode = narrow.startNodeId().equals(node.id());
        double endDistance = narrowStartsAtNode
                ? transitionLength
                : narrow.referenceGeometry().length() - transitionLength;
        RoadPoint end = narrow.referenceGeometry().sample(endDistance).position();
        RoadVector startTangent = outwardTangent(wide, node).scale(-1.0);
        RoadVector endTangent = outwardTangent(narrow, node);
        double chord = node.position().distanceTo(end);
        double handle = Math.min(
                transitionLength * 0.45,
                Math.max(2.0, chord * 0.4)
        );
        return new CubicBezierGeometry(
                node.position(),
                node.position().add(startTangent.scale(handle)),
                end.subtract(endTangent.scale(handle)),
                end
        );
    }

    private static double transitionTurn(
            RoadNode node,
            List<RoadSegment> incident
    ) {
        RoadVector first = outwardTangent(incident.get(0), node);
        RoadVector second = outwardTangent(incident.get(1), node);
        double denominator = first.horizontalLength() * second.horizontalLength();
        double outwardDot = (first.x() * second.x() + first.z() * second.z())
                / denominator;
        double continuationDot = Math.max(-1.0, Math.min(1.0, -outwardDot));
        return Math.acos(continuationDot);
    }

    private static List<MarkingTransition> markingTransitions(
            CrossSection wide,
            CrossSection narrow
    ) {
        List<MarkingColumn> from = wide.markings();
        List<MarkingColumn> to = narrow.markings();
        if (from.isEmpty() && to.isEmpty()) {
            return List.of();
        }
        int count = Math.max(from.size(), to.size());
        List<MarkingTransition> transitions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            MarkingColumn start = proportional(from, index, count);
            MarkingColumn end = proportional(to, index, count);
            double startOffset = start == null
                    ? end.lateralOffset()
                    : start.lateralOffset();
            double endOffset = end == null
                    ? start.lateralOffset()
                    : end.lateralOffset();
            transitions.add(new MarkingTransition(
                    start,
                    end,
                    startOffset,
                    endOffset
            ));
        }
        return List.copyOf(transitions);
    }

    private static MarkingColumn proportional(
            List<MarkingColumn> columns,
            int index,
            int outputCount
    ) {
        if (columns.isEmpty()) {
            return null;
        }
        if (columns.size() == 1 || outputCount == 1) {
            return columns.get(0);
        }
        int selected = (int) Math.round(
                (double) index * (columns.size() - 1) / (outputCount - 1)
        );
        return columns.get(selected);
    }

    private static Optional<SurfaceMaterials> materials(RoadSegment segment) {
        ResourceLocation pavementId = segment.presetSnapshot()
                .intersectionStyle().pavementBlock();
        ResourceLocation sidewalkId = segment.presetSnapshot().surfaceBands().stream()
                .filter(band -> band.role() == RoadSurfaceRole.SIDEWALK)
                .findFirst()
                .map(band -> band.block())
                .orElse(pavementId);
        Optional<BlockState> pavement = BuiltInRegistries.BLOCK
                .getOptional(pavementId)
                .map(RoadBlockStateFactory::solid);
        Optional<BlockState> sidewalk = BuiltInRegistries.BLOCK
                .getOptional(sidewalkId)
                .map(RoadBlockStateFactory::solid);
        if (pavement.isEmpty() || sidewalk.isEmpty()
                || pavement.orElseThrow().isAir()
                || sidewalk.orElseThrow().isAir()) {
            return Optional.empty();
        }
        return Optional.of(new SurfaceMaterials(
                new ResolvedRoadSurface(1, pavement.orElseThrow()),
                new ResolvedRoadSurface(1, sidewalk.orElseThrow())
        ));
    }

    private static RoadVector outwardTangent(RoadSegment segment, RoadNode node) {
        boolean startsAtNode = segment.startNodeId().equals(node.id());
        RoadVector tangent = segment.referenceGeometry().sample(
                startsAtNode ? 0.0 : segment.referenceGeometry().length()
        ).tangent();
        return startsAtNode ? tangent : tangent.scale(-1.0);
    }

    private static RoadVector horizontalRight(RoadVector direction) {
        double length = direction.horizontalLength();
        return new RoadVector(-direction.z() / length, 0.0, direction.x() / length);
    }

    private static double interpolate(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    private static int floorToInt(double value) {
        double floor = Math.floor(value);
        if (!Double.isFinite(floor)
                || floor < Integer.MIN_VALUE
                || floor > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("transition coordinate is out of range");
        }
        return (int) floor;
    }

    private static BlockPlanResult.Failure failure(BlockPlanFailureCode code) {
        return new BlockPlanResult.Failure(code);
    }

    private record CrossSection(
            List<RoadSurfaceRole> roles,
            double negativeSidewalkWidth,
            double positiveSidewalkWidth,
            ResolvedRoadSurface surface,
            boolean reversed
    ) {
        CrossSection {
            roles = List.copyOf(roles);
        }

        double width() {
            return roles.size();
        }

        boolean isSidewalk(double lateral) {
            int column = (int) Math.floor(lateral + width() / 2.0);
            column = Math.max(0, Math.min(roles.size() - 1, column));
            return roles.get(column) == RoadSurfaceRole.SIDEWALK;
        }

        CrossSection withSurface(ResolvedRoadSurface resolved) {
            return new CrossSection(
                    roles,
                    negativeSidewalkWidth,
                    positiveSidewalkWidth,
                    Objects.requireNonNull(resolved, "resolved"),
                    reversed
            );
        }

        List<MarkingColumn> markings() {
            List<MarkingColumn> markings = new ArrayList<>();
            for (int index = 0; index < roles.size(); index++) {
                if (roles.get(index) != RoadSurfaceRole.MARKING) {
                    continue;
                }
                int surfaceIndex = reversed
                        ? roles.size() - 1 - index
                        : index;
                markings.add(new MarkingColumn(
                        index + 0.5 - width() / 2.0,
                        surface,
                        surfaceIndex
                ));
            }
            return List.copyOf(markings);
        }
    }

    private record MarkingColumn(
            double lateralOffset,
            ResolvedRoadSurface surface,
            int columnIndex
    ) {
        ResolvedRoadSurface.SurfacePlacement targetPlacement(
                double pathDistance,
                int blockX,
                int blockZ,
                double verticalLevel
        ) {
            return surface.targetPlacement(
                    columnIndex,
                    pathDistance,
                    blockX,
                    blockZ,
                    verticalLevel
            );
        }
    }

    private record MarkingTransition(
            MarkingColumn start,
            MarkingColumn end,
            double startOffset,
            double endOffset
    ) {
        Optional<MarkingColumn> column(double progress) {
            if (start == null) {
                return progress < 0.15 ? Optional.empty() : Optional.of(end);
            }
            if (end == null) {
                return progress > 0.85 ? Optional.empty() : Optional.of(start);
            }
            return Optional.of(progress < 0.5 ? start : end);
        }

        double lateralOffset(double easedProgress) {
            return interpolate(startOffset, endOffset, easedProgress);
        }
    }

    private record SurfaceMaterials(
            ResolvedRoadSurface pavement,
            ResolvedRoadSurface sidewalk
    ) {
    }
}

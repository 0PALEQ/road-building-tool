package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.network.PreparedSegmentSplit;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RoadMaterializationRepartitioner {
    private final ConstructionLimits limits;

    public RoadMaterializationRepartitioner(ConstructionLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public OperationMaterializationRepartition prepare(
            PlacementOperation source,
            List<RoadSegment> children,
            ResourceKey<Level> dimension,
            Map<RoadNodeId, BlockPlacementPlan> nodePlans
    ) {
        Objects.requireNonNull(source, "source");
        List<RoadSegment> childSegments = List.copyOf(
                Objects.requireNonNull(children, "children")
        );
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(nodePlans, "nodePlans");
        if (childSegments.isEmpty()) {
            throw new IllegalArgumentException("children must not be empty");
        }

        List<Set<BlockPos>> childPositions = new ArrayList<>(
                childSegments.size()
        );
        List<List<RoadPoint>> childSamples = childSegments.stream()
                .map(RoadMaterializationRepartitioner::sampleCenterline)
                .toList();
        StraightRoadSegmentMaterializer materializer =
                new StraightRoadSegmentMaterializer();
        for (RoadSegment child : childSegments) {
            BlockPlanResult result = materializer.plan(dimension, child, limits);
            if (!(result instanceof BlockPlanResult.Success success)) {
                throw new IllegalStateException(
                        "could not rematerialize split child"
                );
            }
            childPositions.add(new HashSet<>(success.plan().positions()));
        }

        List<List<BlockPos>> partitions = new ArrayList<>(
                childSegments.size()
        );
        for (int index = 0; index < childSegments.size(); index++) {
            partitions.add(new ArrayList<>());
        }
        Set<BlockPos> nodePositions = new HashSet<>();
        nodePlans.values().forEach(plan -> nodePositions.addAll(plan.positions()));
        for (BlockPos position : source.positions()) {
            if (nodePositions.contains(position)) {
                continue;
            }
            boolean assigned = false;
            for (int index = 0; index < children.size(); index++) {
                if (childPositions.get(index).contains(position)) {
                    partitions.get(index).add(position);
                    assigned = true;
                }
            }
            if (!assigned) {
                partitions.get(nearestChild(position, childSamples))
                        .add(position);
            }
        }

        List<MaterializationPartition> materializations = new ArrayList<>();
        for (int index = 0; index < partitions.size(); index++) {
            int childIndex = index;
            if (partitions.get(index).isEmpty()) {
                BlockPos closest = source.positions().stream()
                        .min(java.util.Comparator.comparingDouble(position ->
                                distanceSquared(
                                        position,
                                        childSamples.get(childIndex)
                                )))
                        .orElseThrow();
                partitions.get(index).add(closest);
            }
            materializations.add(new MaterializationPartition(
                    index == 0 ? source.id() : PlacementOperationId.create(),
                    RoadMaterializationOwner.segment(
                            childSegments.get(index).id()
                    ),
                    partitions.get(index)
            ));
        }
        for (Map.Entry<RoadNodeId, BlockPlacementPlan> entry
                : nodePlans.entrySet()) {
            materializations.add(new MaterializationPartition(
                    PlacementOperationId.create(),
                    RoadMaterializationOwner.node(entry.getKey()),
                    entry.getValue().positions()
            ));
        }
        return new OperationMaterializationRepartition(
                source.id(),
                materializations
        );
    }

    public List<OperationMaterializationRepartition> prepareExisting(
            PreparedRoadNetworkMutation mutation,
            ResourceKey<Level> dimension,
            PlacementOwnershipLedger ledger
    ) {
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(ledger, "ledger");
        List<OperationMaterializationRepartition> result =
                new ArrayList<>(mutation.segmentSplits().size());
        RoadMaterializationResolver resolver =
                new RoadMaterializationResolver(limits);
        boolean rebuild = mutation.rebuildsExistingGeometry();
        for (PreparedSegmentSplit split : mutation.segmentSplits()) {
            PlacementOperation source = resolver.resolve(
                    split.original(),
                    dimension,
                    ledger
            ).orElseThrow(() -> new IllegalStateException(
                    "crossed segment has no materialization history"
            ));
            if (rebuild) {
                var owner = RoadMaterializationOwner.segment(split.replacements().get(0).id());
                result.add(new OperationMaterializationRepartition(source.id(), List.of(
                        new MaterializationPartition(source.id(), owner, source.positions()))));
            } else {
                result.add(prepare(source, split.replacements(), dimension, Map.of()));
            }
        }
        if (rebuild) {
            Set<PlacementOperationId> known = result.stream().map(OperationMaterializationRepartition::sourceOperationId)
                    .collect(java.util.stream.Collectors.toSet());
            for (var id : RoadMaterializationPlanner.replacedOperations(mutation,dimension,ledger,limits)) {
                if (known.add(id)) {
                    var source = ledger.operation(id).orElseThrow();
                    result.add(new OperationMaterializationRepartition(id,List.of(new MaterializationPartition(
                            id,source.materializationOwner().orElseThrow(),source.positions()))));
                }
            }
            return List.copyOf(result);
        }
        convertedJunctionOwners(mutation).forEach((oldNode, newNode) ->
                ledger.operation(RoadMaterializationOwner.node(oldNode)).ifPresent(source ->
                        result.add(new OperationMaterializationRepartition(source.id(), List.of(
                                new MaterializationPartition(source.id(), RoadMaterializationOwner.node(newNode),
                                        source.positions()))))));
        return List.copyOf(result);
    }

    /** Move the old junction's surface history to a surviving ring port. */
    public static Map<RoadNodeId, RoadNodeId> convertedJunctionOwners(PreparedRoadNetworkMutation mutation) {
        Set<RoadNodeId> retained = new HashSet<>();
        mutation.allUpsertedSegments().forEach(segment -> {
            retained.add(segment.startNodeId());
            retained.add(segment.endNodeId());
        });
        Map<RoadNodeId, RoadNodeId> owners = new java.util.LinkedHashMap<>();
        for (PreparedSegmentSplit split : mutation.segmentSplits()) {
            for (RoadNodeId old : List.of(split.original().startNodeId(), split.original().endNodeId())) {
                if (!retained.contains(old)) owners.put(old, mutation.placedSegments().get(0).startNodeId());
            }
        }
        return Map.copyOf(owners);
    }

    private static int nearestChild(
            BlockPos position,
            List<List<RoadPoint>> children
    ) {
        int nearest = 0;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int index = 0; index < children.size(); index++) {
            double distance = distanceSquared(position, children.get(index));
            if (distance < nearestDistance) {
                nearest = index;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static List<RoadPoint> sampleCenterline(RoadSegment segment) {
        var geometry = segment.referenceGeometry();
        double length = geometry.length();
        int samples = Math.max(1, (int) Math.ceil(length * 2.0));
        List<RoadPoint> points = new ArrayList<>(samples + 1);
        for (int index = 0; index <= samples; index++) {
            // Multiplying before dividing can round the endpoint past length.
            double distance = index == samples ? length : length * index / samples;
            points.add(geometry.sample(distance).position());
        }
        return points;
    }

    private static double distanceSquared(BlockPos position, List<RoadPoint> samples) {
        double nearest = Double.POSITIVE_INFINITY;
        for (RoadPoint point : samples) {
            double deltaX = position.getX() + 0.5 - point.x();
            double deltaY = position.getY() + 0.5 - point.y();
            double deltaZ = position.getZ() + 0.5 - point.z();
            nearest = Math.min(
                    nearest,
                    deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ
            );
        }
        return nearest;
    }
}

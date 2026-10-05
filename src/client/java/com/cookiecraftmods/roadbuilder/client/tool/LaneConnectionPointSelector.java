package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.network.RoadLaneGraph;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

public final class LaneConnectionPointSelector {
    private static final double PICK_RADIUS = 0.85;

    private LaneConnectionPointSelector() {
    }

    public static Optional<RoadLaneGraph.ConnectionPoint> select(
            RoadLaneGraph graph,
            CursorRay ray
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(ray, "ray");
        return graph.connectionPoints().stream()
                .map(point -> candidate(point, ray))
                .flatMap(Optional::stream)
                .min(Comparator
                        .comparingDouble(Candidate::distance)
                        .thenComparingDouble(Candidate::rayDistance)
                        .thenComparing(candidate ->
                                candidate.point().nodeId())
                        .thenComparing(candidate ->
                                candidate.point().laneId()))
                .map(Candidate::point);
    }

    private static Optional<Candidate> candidate(
            RoadLaneGraph.ConnectionPoint point,
            CursorRay ray
    ) {
        RoadPoint position = point.position();
        double rayDistance = ray.direction().dot(
                ray.origin().vectorTo(position)
        );
        rayDistance = Math.max(
                0.0,
                Math.min(ray.maximumDistance(), rayDistance)
        );
        RoadPoint closest = ray.origin().add(
                ray.direction().scale(rayDistance)
        );
        double distance = closest.distanceTo(position);
        return distance <= PICK_RADIUS
                ? Optional.of(new Candidate(point, distance, rayDistance))
                : Optional.empty();
    }

    private record Candidate(
            RoadLaneGraph.ConnectionPoint point,
            double distance,
            double rayDistance
    ) {
    }
}

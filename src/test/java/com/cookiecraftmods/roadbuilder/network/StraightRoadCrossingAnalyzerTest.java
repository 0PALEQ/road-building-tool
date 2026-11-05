package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StraightRoadCrossingAnalyzerTest {
    private final StraightRoadCrossingAnalyzer analyzer = new StraightRoadCrossingAnalyzer();

    @Test
    void findsAProperSameGradeCrossingAndBuildsOneSharedNodeCandidate() {
        RoadSegment crossed = segment(
                1,
                new RoadPoint(0.0, 64.0, 0.0),
                new RoadPoint(10.0, 64.0, 0.0)
        );
        StraightGeometry proposed = new StraightGeometry(
                new RoadPoint(5.0, 64.0, -5.0),
                new RoadPoint(5.0, 64.0, 5.0)
        );

        RoadCrossingCandidate candidate = analyzer.analyze(
                proposed,
                List.of(crossed)
        ).get(0);

        assertEquals(crossed.id(), candidate.crossedSegmentId());
        assertEquals(new RoadPoint(5.0, 64.0, 0.0), candidate.nodePosition());
        assertEquals(0.5, candidate.proposedPathFraction());
        assertEquals(0.5, candidate.crossedPathFraction());
        assertEquals(0.0, candidate.verticalGap());
    }

    @Test
    void excludesCrossingsBeyondOneBlockAndNonProperContacts() {
        StraightGeometry proposed = new StraightGeometry(
                new RoadPoint(5.0, 64.0, -5.0),
                new RoadPoint(5.0, 64.0, 5.0)
        );
        RoadSegment bridge = segment(
                1,
                new RoadPoint(0.0, 66.0, 0.0),
                new RoadPoint(10.0, 66.0, 0.0)
        );
        RoadSegment endpoint = segment(
                2,
                new RoadPoint(5.0, 64.0, 0.0),
                new RoadPoint(10.0, 64.0, 0.0)
        );
        RoadSegment parallel = segment(
                3,
                new RoadPoint(6.0, 64.0, -5.0),
                new RoadPoint(6.0, 64.0, 5.0)
        );

        assertTrue(analyzer.analyze(
                proposed,
                List.of(bridge, endpoint, parallel)
        ).isEmpty());
    }

    @Test
    void joinsAtTheExistingRoadHeightAtTheOneBlockBoundary() {
        StraightGeometry proposed = new StraightGeometry(
                new RoadPoint(5.0, 65.0, -5.0),
                new RoadPoint(5.0, 65.0, 5.0)
        );
        RoadSegment crossed = segment(
                1,
                new RoadPoint(0.0, 64.0, 0.0),
                new RoadPoint(10.0, 64.0, 0.0)
        );

        RoadCrossingCandidate candidate = analyzer.analyze(
                proposed,
                List.of(crossed)
        ).get(0);

        assertEquals(1.0, candidate.verticalGap());
        assertEquals(new RoadPoint(5.0, 64.0, 0.0), candidate.nodePosition());
    }

    @Test
    void ordersCandidatesAlongTheProposedPathInsteadOfInputOrUuidOrder() {
        StraightGeometry proposed = new StraightGeometry(
                new RoadPoint(0.0, 64.0, 0.0),
                new RoadPoint(10.0, 64.0, 0.0)
        );
        RoadSegment later = segment(
                1,
                new RoadPoint(7.0, 64.0, -2.0),
                new RoadPoint(7.0, 64.0, 2.0)
        );
        RoadSegment earlier = segment(
                2,
                new RoadPoint(3.0, 64.0, -2.0),
                new RoadPoint(3.0, 64.0, 2.0)
        );

        List<RoadCrossingCandidate> candidates =
                analyzer.analyze(proposed, List.of(later, earlier));

        assertEquals(List.of(earlier.id(), later.id()), candidates.stream()
                .map(RoadCrossingCandidate::crossedSegmentId)
                .toList());
    }

    private static RoadSegment segment(long id, RoadPoint start, RoadPoint end) {
        LaneComponent lane = new LaneComponent(
                LaneType.VEHICLE,
                1.0,
                0.0,
                LaneTravelDirection.FORWARD
        );
        return RoadSegment.straight(
                roadSegmentId(id),
                new RoadId(new UUID(0, 100 + id)),
                new RoadNodeId(new UUID(0, 200 + id * 2)),
                new RoadNodeId(new UUID(0, 201 + id * 2)),
                new StraightGeometry(start, end),
                new ResourceLocation("roadbuilder", "test"),
                1,
                new RoadPresetSnapshot(
                        1,
                        new ResourceLocation("minecraft", "stone"),
                        List.of(lane)
                ),
                TrafficHandedness.RHT,
                List.of(new RoadLaneId(new UUID(0, 300 + id)))
        );
    }

    private static RoadSegmentId roadSegmentId(long id) {
        return new RoadSegmentId(new UUID(0, id));
    }
}

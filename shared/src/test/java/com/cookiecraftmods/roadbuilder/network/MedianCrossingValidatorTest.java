package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.IntersectionControl;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadClassification;
import com.cookiecraftmods.roadbuilder.preset.RoadIntersectionStyle;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MedianCrossingValidatorTest {
    private static final RoadNodeId NODE_ID = id(RoadNodeId::new, 1);
    private static final RoadId ROAD_ID = id(RoadId::new, 2);

    @Test
    void uturnAroundTheEndOfAMedianIsAllowed() {
        RoadSegment divided = dividedSegment(
                10,
                point(0, 0),
                point(10, 0),
                true
        );
        RoadLane forward = lane(divided, LaneTravelDirection.FORWARD);
        RoadLane backward = lane(divided, LaneTravelDirection.BACKWARD);

        assertFalse(MedianCrossingValidator.crossesMedian(
                divided,
                forward,
                divided,
                backward,
                NODE_ID
        ));
    }

    @Test
    void turnBetweenAngledDividedRoadsGoesAroundMedianEnds() {
        RoadSegment first = dividedSegment(
                80, point(-10, 0), point(0, 0), false
        );
        RoadSegment second = dividedSegment(
                90, point(-8, -6), point(0, 0), false
        );
        assertFalse(MedianCrossingValidator.crossesMedian(
                first, lane(first, LaneTravelDirection.FORWARD),
                second, lane(second, LaneTravelDirection.BACKWARD), NODE_ID
        ));
    }

    @Test
    void lateralCrossingBetweenAlignedMediansIsStillRejected() {
        RoadSegment first = dividedSegment(
                100, point(-10, 0), point(0, 0), false
        );
        RoadSegment second = dividedSegment(
                110, point(0, 0), point(10, 0), true
        );
        assertTrue(MedianCrossingValidator.crossesMedian(
                first, lane(first, LaneTravelDirection.FORWARD),
                second, lane(second, LaneTravelDirection.BACKWARD), NODE_ID
        ));
    }

    @Test
    void straightThroughBetweenDividedRoadsIsAllowed() {
        RoadSegment first = dividedSegment(
                20,
                point(0, 0),
                point(10, 0),
                true
        );
        RoadSegment second = dividedSegment(
                30,
                point(10, 0),
                point(20, 0),
                false
        );
        RoadLane firstForward = lane(first, LaneTravelDirection.FORWARD);
        RoadLane secondForward = lane(second, LaneTravelDirection.FORWARD);

        assertFalse(MedianCrossingValidator.crossesMedian(
                first,
                firstForward,
                second,
                secondForward,
                NODE_ID
        ));
    }

    @Test
    void sideRoadIntoTheNearSideLaneOfADividedRoadIsAllowed() {
        RoadSegment divided = dividedSegment(
                40,
                point(-10, 0),
                point(0, 0),
                true
        );
        RoadSegment side = plainSegment(
                50,
                point(0, 0),
                point(0, -10),
                false
        );
        RoadLane dividedForward = lane(divided, LaneTravelDirection.FORWARD);
        RoadLane sideForward = lane(side, LaneTravelDirection.FORWARD);

        assertFalse(MedianCrossingValidator.crossesMedian(
                divided,
                dividedForward,
                side,
                sideForward,
                NODE_ID
        ));
    }

    @Test
    void roadsWithoutMediansNeverCross() {
        RoadSegment first = plainSegment(
                60,
                point(0, 0),
                point(10, 0),
                true
        );
        RoadSegment second = plainSegment(
                70,
                point(10, 0),
                point(20, 0),
                false
        );
        RoadLane firstForward = lane(first, LaneTravelDirection.FORWARD);
        RoadLane secondForward = lane(second, LaneTravelDirection.FORWARD);

        assertFalse(MedianCrossingValidator.crossesMedian(
                first,
                firstForward,
                second,
                secondForward,
                NODE_ID
        ));
    }

    private static RoadSegment dividedSegment(
            int seed,
            RoadPoint start,
            RoadPoint end,
            boolean startsAtNode
    ) {
        RoadPresetSnapshot snapshot = new RoadPresetSnapshot(
                11,
                MinecraftIds.id("minecraft", "gray_concrete"),
                List.of(
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3,
                                -3.5,
                                LaneTravelDirection.FORWARD
                        ),
                        new LaneComponent(
                                LaneType.VEHICLE,
                                3,
                                3.5,
                                LaneTravelDirection.BACKWARD
                        )
                ),
                List.of(
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                4,
                                MinecraftIds.id(
                                        "minecraft",
                                        "gray_concrete"
                                )
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.GRASS_MEDIAN,
                                3,
                                MinecraftIds.id(
                                        "minecraft",
                                        "green_concrete"
                                )
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                4,
                                MinecraftIds.id(
                                        "minecraft",
                                        "gray_concrete"
                                )
                        )
                )
        );
        return RoadSegment.straight(
                id(RoadSegmentId::new, seed),
                ROAD_ID,
                startsAtNode ? NODE_ID : id(RoadNodeId::new, seed + 100),
                startsAtNode ? id(RoadNodeId::new, seed + 100) : NODE_ID,
                new StraightGeometry(start, end),
                MinecraftIds.id("roadbuilder", "test"),
                1,
                snapshot,
                TrafficHandedness.RHT,
                List.of(
                        id(RoadLaneId::new, seed + 200),
                        id(RoadLaneId::new, seed + 300)
                )
        );
    }

    private static RoadSegment plainSegment(
            int seed,
            RoadPoint start,
            RoadPoint end,
            boolean startsAtNode
    ) {
        RoadPresetSnapshot snapshot = new RoadPresetSnapshot(
                5,
                MinecraftIds.id("minecraft", "gray_concrete"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        3,
                        0,
                        LaneTravelDirection.FORWARD
                )),
                List.of(),
                RoadClassification.DEFAULT,
                new RoadIntersectionStyle(
                        MinecraftIds.id("minecraft", "gray_concrete"),
                        Optional.empty(),
                        Optional.of(MinecraftIds.id(
                                "minecraft",
                                "white_concrete"
                        )),
                        Optional.of(MinecraftIds.id(
                                "minecraft",
                                "white_concrete"
                        )),
                        false,
                        IntersectionControl.FREE
                )
        );
        return RoadSegment.straight(
                id(RoadSegmentId::new, seed),
                ROAD_ID,
                startsAtNode ? NODE_ID : id(RoadNodeId::new, seed + 100),
                startsAtNode ? id(RoadNodeId::new, seed + 100) : NODE_ID,
                new StraightGeometry(start, end),
                MinecraftIds.id("roadbuilder", "test"),
                1,
                snapshot,
                TrafficHandedness.RHT,
                List.of(id(RoadLaneId::new, seed + 200))
        );
    }

    private static RoadLane lane(
            RoadSegment segment,
            LaneTravelDirection direction
    ) {
        return segment.lanes().stream()
                .filter(lane -> lane.component().direction() == direction)
                .findFirst()
                .orElseThrow();
    }

    private static RoadPoint point(double x, double z) {
        return new RoadPoint(x, 64, z);
    }

    private static <T> T id(
            java.util.function.Function<UUID, T> factory,
            long seed
    ) {
        return factory.apply(new UUID(0, seed));
    }
}

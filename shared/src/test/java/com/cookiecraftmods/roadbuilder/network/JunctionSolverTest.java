package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.IntersectionControl;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadClass;
import com.cookiecraftmods.roadbuilder.preset.RoadClassification;
import com.cookiecraftmods.roadbuilder.preset.RoadIntersectionStyle;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JunctionSolverTest {
    private static final RoadNodeId NODE_ID = id(RoadNodeId::new, 1);
    private static final RoadId MAIN_ROAD = id(RoadId::new, 2);

    @Test
    void shortArmsKeepPositiveNonOverlappingTrims() {
        RoadNode node = new RoadNode(NODE_ID, point(0, 0));
        for (double length : new double[]{0.1, 0.5, 1.0, 2.0}) {
            RoadSegment shortArm = segment(10, MAIN_ROAD, node.position(),
                    point(length, 0), true, RoadClassification.DEFAULT);
            RoadSegment incoming = segment(20, MAIN_ROAD, point(-30, 0),
                    node.position(), false, RoadClassification.DEFAULT);
            var solved = assertInstanceOf(JunctionSolveResult.Success.class,
                    new JunctionSolver().solve(node, List.of(shortArm, incoming),
                            JunctionDefinition.automatic(NODE_ID)));
            var trim = solved.approaches().stream()
                    .filter(arm -> arm.segment() == shortArm).findFirst().orElseThrow().trimDistance();
            assertTrue(trim > 0 && trim * 2 < length);
        }
    }

    @Test
    void higherPriorityRoadControlsAutomaticTJunctionPriority() {
        RoadNode node = new RoadNode(NODE_ID, point(0, 0));
        RoadSegment west = segment(
                10,
                MAIN_ROAD,
                point(-12, 0),
                node.position(),
                false,
                RoadClassification.DEFAULT
        );
        RoadSegment east = segment(
                20,
                MAIN_ROAD,
                node.position(),
                point(12, 0),
                true,
                RoadClassification.DEFAULT
        );
        RoadSegment stem = segment(
                30,
                id(RoadId::new, 3),
                node.position(),
                point(0, 12),
                true,
                new RoadClassification(RoadClass.HIGHWAY, 15, 120)
        );

        var result = assertInstanceOf(
                JunctionSolveResult.Success.class,
                new JunctionSolver().solve(
                        node,
                        List.of(stem, east, west),
                        JunctionDefinition.automatic(NODE_ID)
                )
        );

        assertEquals(
                IntersectionControl.FREE,
                result.approaches().stream()
                        .filter(approach ->
                                approach.segment().id().equals(stem.id()))
                        .findFirst().orElseThrow().control()
        );
        assertTrue(result.approaches().stream()
                .filter(approach -> approach.segment().id().equals(stem.id()))
                .findFirst().orElseThrow().primary());
        assertTrue(result.approaches().stream()
                .filter(approach -> approach.segment().id().equals(west.id())
                        || approach.segment().id().equals(east.id()))
                .allMatch(approach ->
                        approach.control() == IntersectionControl.YIELD));
    }

    @Test
    void manualAllWayStopAndCrosswalkOverridesAreHonored() {
        RoadNode node = new RoadNode(NODE_ID, point(0, 0));
        RoadSegment west = segment(
                10, MAIN_ROAD, point(-12, 0), node.position(), false,
                RoadClassification.DEFAULT
        );
        RoadSegment east = segment(
                20, MAIN_ROAD, node.position(), point(12, 0), true,
                RoadClassification.DEFAULT
        );
        ApproachKey westKey = ApproachKey.at(west, NODE_ID);
        JunctionDefinition definition = new JunctionDefinition(
                NODE_ID,
                JunctionStrategy.MANUAL,
                JunctionPriorityPolicy.ALL_WAY_STOP,
                List.of(westKey),
                JunctionCrossingPolicy.MANUAL,
                Map.of(westKey, new JunctionApproachOverride(
                        IntersectionControl.STOP,
                        true
                )),
                1
        );
        var result = assertInstanceOf(
                JunctionSolveResult.Success.class,
                new JunctionSolver().solve(
                        node,
                        List.of(west, east),
                        definition
                )
        );
        assertTrue(result.approaches().stream()
                .allMatch(approach ->
                        approach.control() == IntersectionControl.STOP));
        assertTrue(result.approaches().stream()
                .filter(approach -> approach.key().equals(westKey))
                .findFirst().orElseThrow().crosswalk());
        assertFalse(result.approaches().stream()
                .filter(approach -> !approach.key().equals(westKey))
                .findFirst().orElseThrow().crosswalk());
    }

    @Test
    void wideRoadForkUsesTheRealPortsAndRemainsFreeFlow() {
        RoadNode node = new RoadNode(NODE_ID, point(0, 0));
        RoadSegment trunk = segment(
                40,
                MAIN_ROAD,
                node.position(),
                point(24, 0),
                true,
                RoadClassification.DEFAULT,
                12
        );
        RoadPoint lowerPort = point(0, -3.5);
        RoadPoint upperPort = point(0, 3.5);
        RoadSegment lowerBranch = segment(
                50,
                id(RoadId::new, 5),
                lowerPort,
                point(-24, -10),
                true,
                RoadClassification.DEFAULT,
                5
        );
        RoadSegment upperBranch = segment(
                60,
                id(RoadId::new, 6),
                upperPort,
                point(-24, 10),
                true,
                RoadClassification.DEFAULT,
                5
        );
        List<RoadSegment> incident = List.of(
                trunk,
                lowerBranch,
                upperBranch
        );

        assertTrue(RoadForkJunction.isFork(node, incident));
        var result = assertInstanceOf(
                JunctionSolveResult.Success.class,
                new JunctionSolver().solve(
                        node,
                        incident,
                        JunctionDefinition.automatic(NODE_ID)
                )
        );

        assertTrue(result.approaches().stream().allMatch(approach ->
                approach.primary()
                        && approach.control() == IntersectionControl.FREE
                        && !approach.crosswalk()
                        && approach.trimDistance() <= 12.0));
        assertEquals(lowerPort, result.approaches().stream()
                .filter(approach -> approach.segment().id().equals(
                        lowerBranch.id()
                ))
                .findFirst()
                .orElseThrow()
                .boundaryPosition());
        assertEquals(upperPort, result.approaches().stream()
                .filter(approach -> approach.segment().id().equals(
                        upperBranch.id()
                ))
                .findFirst()
                .orElseThrow()
                .boundaryPosition());
    }

    @Test
    void terminalNodeSupportsAnExplicitCrosswalk() {
        RoadNode node = new RoadNode(NODE_ID, point(0, 0));
        RoadSegment arm = segment(
                70,
                MAIN_ROAD,
                node.position(),
                point(16, 0),
                true,
                RoadClassification.DEFAULT
        );
        ApproachKey key = ApproachKey.at(arm, NODE_ID);
        JunctionDefinition definition = new JunctionDefinition(
                NODE_ID,
                JunctionStrategy.AUTO,
                JunctionPriorityPolicy.RIGHT_OF_WAY,
                List.of(),
                JunctionCrossingPolicy.AUTO,
                Map.of(key, new JunctionApproachOverride(
                        IntersectionControl.FREE,
                        true
                )),
                1
        );

        var result = assertInstanceOf(
                JunctionSolveResult.Success.class,
                new JunctionSolver().solve(node, List.of(arm), definition)
        );

        assertEquals(1, result.approaches().size());
        assertTrue(result.approaches().get(0).crosswalk());
    }

    @Test
    void explicitCrosswalkOverrideIsHonoredAtAFreeFlowFork() {
        RoadNode node = new RoadNode(NODE_ID, point(0, 0));
        RoadSegment trunk = segment(
                80,
                MAIN_ROAD,
                node.position(),
                point(24, 0),
                true,
                RoadClassification.DEFAULT,
                12
        );
        RoadSegment lower = segment(
                90,
                id(RoadId::new, 9),
                point(0, -3.5),
                point(-24, -10),
                true,
                RoadClassification.DEFAULT,
                5
        );
        RoadSegment upper = segment(
                100,
                id(RoadId::new, 10),
                point(0, 3.5),
                point(-24, 10),
                true,
                RoadClassification.DEFAULT,
                5
        );
        List<RoadSegment> incident = List.of(trunk, lower, upper);
        ApproachKey selected = ApproachKey.at(trunk, NODE_ID);
        JunctionDefinition definition = new JunctionDefinition(
                NODE_ID,
                JunctionStrategy.AUTO,
                JunctionPriorityPolicy.RIGHT_OF_WAY,
                List.of(),
                JunctionCrossingPolicy.AUTO,
                Map.of(selected, new JunctionApproachOverride(
                        IntersectionControl.FREE,
                        true
                )),
                1
        );

        var result = assertInstanceOf(
                JunctionSolveResult.Success.class,
                new JunctionSolver().solve(node, incident, definition)
        );

        assertTrue(result.approaches().stream()
                .filter(approach -> approach.key().equals(selected))
                .findFirst()
                .orElseThrow()
                .crosswalk());
        assertTrue(result.approaches().stream()
                .filter(approach -> !approach.key().equals(selected))
                .noneMatch(approach -> approach.crosswalk()));
    }

    @Test
    void highwayApproachesDoNotGetAutomaticCrosswalks() {
        RoadNode node = new RoadNode(NODE_ID, point(0, 0));
        RoadSegment west = segment(
                110,
                MAIN_ROAD,
                point(-12, 0),
                node.position(),
                false,
                RoadClassification.DEFAULT
        );
        RoadSegment east = segment(
                120,
                MAIN_ROAD,
                node.position(),
                point(12, 0),
                true,
                RoadClassification.DEFAULT
        );
        RoadSegment stem = segment(
                130,
                id(RoadId::new, 13),
                node.position(),
                point(0, 12),
                true,
                new RoadClassification(RoadClass.HIGHWAY, 15, 120),
                5,
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
                        true,
                        IntersectionControl.FREE
                )
        );

        var result = assertInstanceOf(
                JunctionSolveResult.Success.class,
                new JunctionSolver().solve(
                        node,
                        List.of(stem, east, west),
                        JunctionDefinition.automatic(NODE_ID)
                )
        );

        assertTrue(result.approaches().stream()
                .filter(approach -> approach.segment().id().equals(stem.id()))
                .findFirst().orElseThrow().primary());
        assertFalse(result.approaches().stream()
                .filter(approach -> approach.segment().id().equals(stem.id()))
                .findFirst().orElseThrow().crosswalk());
        for (JunctionCrossingPolicy policy : JunctionCrossingPolicy.values()) {
            var definition = new JunctionDefinition(NODE_ID, JunctionStrategy.AUTO,
                    JunctionPriorityPolicy.RIGHT_OF_WAY, List.of(), policy,
                    Map.of(ApproachKey.at(stem, NODE_ID),
                            new JunctionApproachOverride(IntersectionControl.FREE, true)), 1);
            var overridden = assertInstanceOf(JunctionSolveResult.Success.class,
                    new JunctionSolver().solve(node, List.of(stem, east, west), definition));
            assertFalse(overridden.approaches().stream()
                    .filter(approach -> approach.segment().id().equals(stem.id()))
                    .findFirst().orElseThrow().crosswalk(), "highway crosswalk under " + policy);
            if (policy == JunctionCrossingPolicy.ALL_APPROACHES) {
                assertTrue(overridden.approaches().stream()
                        .filter(approach -> approach.segment().id().equals(west.id()))
                        .findFirst().orElseThrow().crosswalk());
            }
        }
    }

    private static RoadSegment segment(
            int seed,
            RoadId roadId,
            RoadPoint start,
            RoadPoint end,
            boolean startsAtNode,
            RoadClassification classification
    ) {
        return segment(
                seed,
                roadId,
                start,
                end,
                startsAtNode,
                classification,
                5
        );
    }

    private static RoadSegment segment(
            int seed,
            RoadId roadId,
            RoadPoint start,
            RoadPoint end,
            boolean startsAtNode,
            RoadClassification classification,
            int surfaceWidth
    ) {
        return segment(
                seed,
                roadId,
                start,
                end,
                startsAtNode,
                classification,
                surfaceWidth,
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
    }

    private static RoadSegment segment(
            int seed,
            RoadId roadId,
            RoadPoint start,
            RoadPoint end,
            boolean startsAtNode,
            RoadClassification classification,
            int surfaceWidth,
            RoadIntersectionStyle style
    ) {
        RoadPresetSnapshot snapshot = new RoadPresetSnapshot(
                surfaceWidth,
                MinecraftIds.id("minecraft", "gray_concrete"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        3,
                        0,
                        LaneTravelDirection.FORWARD
                )),
                List.of(),
                classification,
                style
        );
        return RoadSegment.straight(
                id(RoadSegmentId::new, seed),
                roadId,
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

package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.PathSample;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadId;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadSnapResolverTest {
    @Test
    void guidelinesReachBothSidesAndBothEndsOfRotatedRoads() {
        RoadPoint anchor = new RoadPoint(10, 64, 10);
        RoadVector forward = new RoadVector(1, 0, 1).normalize();
        RoadSegment road = segment(1, anchor.subtract(forward.scale(10)), anchor,
                8, List.of(lane(0, LaneTravelDirection.FORWARD)));
        RoadSnapSettings settings = new RoadSnapSettings(2.5, 1,
                RoadSnapPriority.TANGENT_ANGLE_GRID, false, true, 60);
        for (RoadVector direction : List.of(forward, forward.scale(-1),
                new RoadVector(-forward.z(), 0, forward.x()),
                new RoadVector(forward.z(), 0, -forward.x()))) {
            RoadPoint expected = anchor.add(direction.scale(30));
            RoadVector lateral = new RoadVector(-direction.z(), 0, direction.x());
            RoadPoint near = expected.add(lateral.scale(0.1));
            RoadSnapResult result = RoadSnapResolver.resolve(
                    target(near.x(), 64, near.z()), null, List.of(), List.of(road),
                    RoadSnappingMode.GUIDELINES, settings, false);
            assertEquals(expected.x(), result.target().roadPoint().x(), 1e-9);
            assertEquals(expected.z(), result.target().roadPoint().z(), 1e-9);
            assertFalse(result.guides().isEmpty());
            RoadVector guide = result.guides().get(0).start().vectorTo(result.guides().get(0).end());
            assertTrue(guide.dot(direction) > 0);
        }
    }

    @Test
    void complexCurveAngleSnapsCoverTheWholeCircle() {
        RoadPoint pivot = new RoadPoint(10, 64, 10);
        RoadPoint reference = new RoadPoint(0, 64, 0);
        for (int degrees = 0; degrees < 360; degrees += 45) {
            double angle = Math.toRadians(degrees);
            CursorTarget raw = target(10 + 30 * Math.cos(angle + 0.005), 64,
                    10 + 30 * Math.sin(angle + 0.005));
            RoadSnapResult result = RoadSnapResolver.resolve(raw, null, List.of(), List.of(),
                    RoadSnappingMode.ANGLES, snappingWithoutOptionalGuides(), false,
                    new RoadSnapResolver.CurveAngleContext(reference, pivot, false));
            assertEquals(10 + 30 * Math.cos(angle), result.target().roadPoint().x(), 1e-9);
            assertEquals(10 + 30 * Math.sin(angle), result.target().roadPoint().z(), 1e-9);
            assertFalse(result.guides().isEmpty());
        }
    }

    @Test
    void twoHandleAnglesPreserveLengthAndElevationInRotatedFrames() {
        RoadPoint pivot = new RoadPoint(20, 64, 20);
        // Entry points northeast; test perpendicular and parallel exit tangents.
        RoadPoint reference = new RoadPoint(10, 64, 10);
        for (RoadVector direction : List.of(
                new RoadVector(1, 0, 1).normalize(),
                new RoadVector(-1, 0, 1).normalize())) {
            RoadPoint desired = pivot.add(direction.scale(30));
            CursorTarget raw = target(desired.x() + 0.1, 70, desired.z());
            RoadSnapResult result = RoadSnapResolver.resolve(
                    raw, target(0, 64, 0), List.of(), List.of(),
                    RoadSnappingMode.ANGLES, snappingWithoutOptionalGuides(), false,
                    new RoadSnapResolver.CurveAngleContext(reference, pivot, false)
            );
            RoadVector actual = pivot.vectorTo(result.target().roadPoint());
            assertEquals(0, actual.x() * direction.z() - actual.z() * direction.x(), 1e-9);
            assertEquals(pivot.vectorTo(raw.roadPoint()).horizontalLength(),
                    actual.horizontalLength(), 1e-9);
            assertEquals(70, result.target().roadPoint().y());
            assertFalse(result.guides().isEmpty());
            assertEquals(raw, RoadSnapResolver.resolve(
                    raw, null, List.of(), List.of(), RoadSnappingMode.ANGLES,
                    snappingWithoutOptionalGuides(), true,
                    new RoadSnapResolver.CurveAngleContext(reference, pivot, false)
            ).target());
        }
    }

    @Test
    void nodeSnappingTakesPriorityAndSkipsTheActiveStartNode() {
        CursorTarget start = target(0.5, 64.0, 0.5);
        RoadNode startNode = node(1, start.roadPoint());
        RoadNode endpoint = node(2, new RoadPoint(10.5, 64.0, 0.5));
        CursorTarget raw = target(9.2, 64.0, 1.0);

        CursorTarget snapped = RoadSnapResolver.snap(
                raw,
                start,
                List.of(startNode, endpoint),
                RoadSnappingMode.NODES_AND_ANGLES
        );

        assertEquals(endpoint.position(), snapped.roadPoint());
    }

    @Test
    void angleModeRoundsToFortyFiveDegreesWhenNoNodeIsNear() {
        CursorTarget start = target(0.5, 64.0, 0.5);
        CursorTarget raw = target(10.5, 64.0, 1.5);

        CursorTarget snapped = RoadSnapResolver.snap(
                raw,
                start,
                List.of(),
                RoadSnappingMode.NODES_AND_ANGLES
        );

        assertTrue(Math.abs(snapped.roadPoint().z() - start.roadPoint().z()) < 1.0e-9);
        assertEquals(
                Math.hypot(10.0, 1.0),
                Math.hypot(
                        snapped.roadPoint().x() - start.roadPoint().x(),
                        snapped.roadPoint().z() - start.roadPoint().z()
                ),
                1.0e-9
        );
    }

    @Test
    void angleModeIncludesDiagonalRightAngleAndStraightDirections() {
        CursorTarget start = target(0.5, 64.0, 0.5);
        assertSnappedAngle(start, 40.0, 45.0);
        assertSnappedAngle(start, 82.0, 90.0);
        assertSnappedAngle(start, 174.0, 180.0);
    }

    @Test
    void curveAngleSnappingAlwaysKeepsEqualLegsAroundTheControlPoint() {
        RoadPoint startPoint = new RoadPoint(0.5, 64.0, 0.5);
        RoadPoint controlPoint = new RoadPoint(10.5, 64.0, 0.5);

        RoadSnapResult result = RoadSnapResolver.resolve(
                target(11.1, 64.0, 30.1),
                target(startPoint.x(), startPoint.y(), startPoint.z()),
                List.of(),
                List.of(),
                RoadSnappingMode.ANGLES,
                snappingWithoutOptionalGuides(),
                false,
                new RoadSnapResolver.CurveAngleContext(
                        startPoint,
                        controlPoint
                )
        );

        RoadPoint endpoint = result.target().roadPoint();
        assertEquals(new RoadPoint(10.5, 64.0, 10.5), endpoint);
        assertEquals(
                Math.hypot(
                        startPoint.x() - controlPoint.x(),
                        startPoint.z() - controlPoint.z()
                ),
                Math.hypot(
                        endpoint.x() - controlPoint.x(),
                        endpoint.z() - controlPoint.z()
                ),
                1.0e-9
        );
        RoadVector firstLeg = controlPoint.vectorTo(startPoint).normalize();
        RoadVector secondLeg = controlPoint.vectorTo(endpoint).normalize();
        assertEquals(90.0, Math.toDegrees(Math.acos(
                firstLeg.dot(secondLeg)
        )), 1.0e-9);
        assertEquals(RoadSnapGuide.Kind.NODE_ANGLE, result.guides().get(0).kind());
    }

    @Test
    void nodeModeSnapsGraphEndpoints() {
        RoadNode endpoint = node(1, new RoadPoint(3.5, 64.0, 4.5));
        CursorTarget snapped = RoadSnapResolver.snap(
                target(4.0, 64.0, 4.8),
                null,
                List.of(endpoint),
                RoadSnappingMode.NODES
        );

        assertEquals(endpoint.position(), snapped.roadPoint());
    }

    @Test
    void snappingCycleOnlyTogglesAngleGuides() {
        assertEquals(
                RoadSnappingMode.NODES_AND_ANGLES,
                RoadSnappingMode.NODES.next()
        );
        assertEquals(
                RoadSnappingMode.NODES,
                RoadSnappingMode.NODES_AND_ANGLES.next()
        );
    }

    @Test
    void nodeGuidelineAndAngleTogglesAreIndependent() {
        assertFalse(RoadSnappingMode.NONE.snapsNodes());
        assertFalse(RoadSnappingMode.NONE.snapsGuidelines());
        assertFalse(RoadSnappingMode.NONE.snapsAngles());
        assertTrue(RoadSnappingMode.NODES.snapsNodes());
        assertFalse(RoadSnappingMode.NODES.snapsGuidelines());
        assertFalse(RoadSnappingMode.NODES.snapsAngles());
        assertFalse(RoadSnappingMode.GUIDELINES.snapsNodes());
        assertTrue(RoadSnappingMode.GUIDELINES.snapsGuidelines());
        assertFalse(RoadSnappingMode.GUIDELINES.snapsAngles());
        assertTrue(RoadSnappingMode.NODES_AND_ANGLES.snapsNodes());
        assertFalse(RoadSnappingMode.NODES_AND_ANGLES.snapsGuidelines());
        assertTrue(RoadSnappingMode.NODES_AND_ANGLES.snapsAngles());
        assertTrue(RoadSnappingMode.ALL.snapsNodes());
        assertTrue(RoadSnappingMode.ALL.snapsGuidelines());
        assertTrue(RoadSnappingMode.ALL.snapsAngles());

        assertEquals(
                RoadSnappingMode.NONE,
                RoadSnappingMode.NODES.toggleNodes()
        );
        assertEquals(
                RoadSnappingMode.GUIDELINES,
                RoadSnappingMode.NODES_AND_GUIDELINES.toggleNodes()
        );
        assertEquals(
                RoadSnappingMode.GUIDELINES,
                RoadSnappingMode.NONE.toggleGuidelines()
        );
        assertEquals(
                RoadSnappingMode.NODES,
                RoadSnappingMode.NODES_AND_GUIDELINES.toggleGuidelines()
        );
        assertEquals(
                RoadSnappingMode.ANGLES,
                RoadSnappingMode.NONE.toggleAngles()
        );
        assertEquals(
                RoadSnappingMode.NODES,
                RoadSnappingMode.NODES_AND_ANGLES.toggleAngles()
        );
    }

    @Test
    void guidelineOnlyModeDoesNotSnapDirectlyToNodes() {
        RoadNode endpoint = node(1, new RoadPoint(3.5, 64.0, 4.5));
        CursorTarget raw = target(4.0, 64.0, 4.8);

        RoadSnapResult result = RoadSnapResolver.resolve(
                raw,
                null,
                List.of(endpoint),
                List.of(),
                RoadSnappingMode.GUIDELINES,
                snappingWithoutOptionalGuides(),
                false
        );

        assertEquals(raw, result.target());
        assertTrue(result.guides().isEmpty());
    }

    @Test
    void blockGridPreferenceIsIndependentFromTheTwoToolbarToggles() {
        RoadSnapSettings settings = new RoadSnapSettings(
                2.5,
                1.0,
                RoadSnapPriority.GRID_TANGENT_ANGLE,
                true,
                false
        );

        RoadSnapResult result = RoadSnapResolver.resolve(
                target(1.2, 64.0, 3.7),
                null,
                List.of(),
                List.of(),
                RoadSnappingMode.NONE,
                settings,
                false
        );

        assertEquals(
                new RoadPoint(1.5, 64.0, 3.5),
                result.target().roadPoint()
        );
    }

    @Test
    void roadSurfaceGuidelineSnappingSelectsTheNearestLaneOrEdge() {
        RoadSegment road = segment(
                1,
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(20.5, 64.0, 0.5),
                14,
                List.of(
                        lane(-4.5, LaneTravelDirection.BACKWARD),
                        lane(-1.5, LaneTravelDirection.BACKWARD),
                        lane(1.5, LaneTravelDirection.FORWARD),
                        lane(4.5, LaneTravelDirection.FORWARD)
                )
        );

        RoadSnapResult snapped = RoadSnapResolver.resolve(
                target(10.5, 64.0, 6.2),
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.GUIDELINES,
                snappingWithoutOptionalGuides(),
                false
        );

        assertEquals(new RoadPoint(10.5, 64.0, 5.0), snapped.target().roadPoint());
        assertEquals(1, snapped.guides().size());
        assertEquals(
                RoadSnapGuide.Kind.LANE_CONTINUATION,
                snapped.guides().get(0).kind()
        );
    }

    @Test
    void nodeModeSnapsInteriorRoadTargetsToTheRoadCenterline() {
        RoadSegment road = segment(
                1,
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(20.5, 64.0, 0.5),
                14,
                List.of(lane(0.0, LaneTravelDirection.FORWARD))
        );
        CursorTarget raw = target(10.5, 64.0, 6.2);

        RoadSnapResult snapped = RoadSnapResolver.resolve(
                raw,
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.NODES,
                snappingWithoutOptionalGuides(),
                false
        );

        assertEquals(
                new RoadPoint(10.5, 64.0, 0.5),
                snapped.target().roadPoint()
        );
        assertEquals(1, snapped.guides().size());
        assertEquals(
                RoadSnapGuide.Kind.LANE_CONTINUATION,
                snapped.guides().get(0).kind()
        );
    }

    @Test
    void roadSurfaceSnappingDoesNotJoinASeparatedOverpass() {
        RoadSegment road = segment(
                1,
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(20.5, 64.0, 0.5),
                14,
                List.of(lane(0.0, LaneTravelDirection.FORWARD))
        );
        CursorTarget raw = target(10.5, 72.0, 5.5);

        RoadSnapResult snapped = RoadSnapResolver.resolve(
                raw,
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.GUIDELINES,
                snappingWithoutOptionalGuides(),
                false
        );

        assertEquals(raw, snapped.target());
    }

    @Test
    void wideRoadSnapAreaStopsAtItsPhysicalEnd() {
        RoadSegment road = segment(
                1,
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(20.5, 64.0, 0.5),
                14,
                List.of(lane(0.0, LaneTravelDirection.FORWARD))
        );
        CursorTarget raw = target(24.0, 64.0, 0.5);

        RoadSnapResult snapped = RoadSnapResolver.resolve(
                raw,
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.GUIDELINES,
                snappingWithoutOptionalGuides(),
                false
        );

        assertEquals(raw, snapped.target());
    }

    @Test
    void roadSurfaceSnappingProjectsOntoCurvedRoads() {
        ReferenceGeometry curve = new QuadraticBezierGeometry(
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(10.5, 64.0, 10.5),
                new RoadPoint(20.5, 64.0, 0.5)
        );
        RoadPoint onCurve = curve.sample(curve.length() / 2.0).position();
        RoadPoint rawPoint = onCurve.add(
                curve.sample(curve.length() / 2.0).tangent()
                        .horizontalLeftUnit().scale(4.0)
        );
        RoadSegment road = segment(
                1,
                curve,
                12,
                List.of(lane(0.0, LaneTravelDirection.FORWARD))
        );

        RoadSnapResult snapped = RoadSnapResolver.resolve(
                new CursorTarget(
                        BlockPos.containing(
                                rawPoint.x(),
                                rawPoint.y(),
                                rawPoint.z()
                        ),
                        rawPoint
                ),
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.GUIDELINES,
                snappingWithoutOptionalGuides(),
                false
        );

        RoadPoint expectedEdge = curve.sample(curve.length() / 2.0)
                .offsetLaterally(6.0)
                .position();
        assertTrue(snapped.target().roadPoint().distanceTo(expectedEdge) < 1.0e-3);
    }

    @Test
    void laneContinuationGuidesIncludeCentersSpacesAndRoadEdges() {
        RoadSegment road = segment(
                1,
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(10.5, 64.0, 0.5),
                8,
                List.of(
                        lane(-1.5, LaneTravelDirection.BACKWARD),
                        lane(1.5, LaneTravelDirection.FORWARD)
                )
        );
        RoadSnapSettings settings = new RoadSnapSettings(
                2.5,
                1.0,
                RoadSnapPriority.TANGENT_ANGLE_GRID,
                false,
                true
        );

        RoadSnapResult lane = RoadSnapResolver.resolve(
                target(20.5, 64.0, 2.3),
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.GUIDELINES,
                settings,
                false
        );
        RoadSnapResult between = RoadSnapResolver.resolve(
                target(20.5, 64.0, 0.9),
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.GUIDELINES,
                settings,
                false
        );
        RoadSnapResult edge = RoadSnapResolver.resolve(
                target(20.5, 64.0, 4.2),
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.GUIDELINES,
                settings,
                false
        );

        assertEquals(2.0, lane.target().roadPoint().z(), 1.0e-9);
        assertEquals(0.5, between.target().roadPoint().z(), 1.0e-9);
        assertEquals(4.5, edge.target().roadPoint().z(), 1.0e-9);
        assertEquals(
                RoadSnapGuide.Kind.LANE_CONTINUATION,
                lane.guides().get(0).kind()
        );
    }

    @Test
    void tangentGuidelinesRespectTheConfiguredRange() {
        RoadSegment road = segment(
                1,
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(10.5, 64.0, 0.5),
                8,
                List.of(lane(1.5, LaneTravelDirection.FORWARD))
        );
        CursorTarget raw = target(20.5, 64.0, 2.3);

        RoadSnapResult limited = RoadSnapResolver.resolve(
                raw,
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.GUIDELINES,
                new RoadSnapSettings(
                        2.5,
                        1.0,
                        RoadSnapPriority.TANGENT_ANGLE_GRID,
                        false,
                        true,
                        5.0
                ),
                false
        );
        RoadSnapResult extended = RoadSnapResolver.resolve(
                raw,
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.GUIDELINES,
                new RoadSnapSettings(
                        2.5,
                        1.0,
                        RoadSnapPriority.TANGENT_ANGLE_GRID,
                        false,
                        true,
                        12.0
                ),
                false
        );

        assertEquals(raw, limited.target());
        assertTrue(limited.guides().isEmpty());
        assertEquals(2.0, extended.target().roadPoint().z(), 1.0e-9);
        assertTrue(extended.guides().get(0).start().distanceTo(
                extended.guides().get(0).end()
        ) <= 12.0);
    }

    @Test
    void disabledGuidelinesDoNotSnapAFirstPointToALaneExtension() {
        RoadSegment road = segment(
                1,
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(10.5, 64.0, 0.5),
                8,
                List.of(lane(1.5, LaneTravelDirection.FORWARD))
        );
        CursorTarget raw = target(20.5, 64.0, -0.7);
        RoadSnapSettings settings = new RoadSnapSettings(
                2.5,
                1.0,
                RoadSnapPriority.TANGENT_ANGLE_GRID,
                false,
                true
        );

        RoadSnapResult result = RoadSnapResolver.resolve(
                raw,
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.NODES,
                settings,
                false
        );

        assertEquals(raw, result.target());
        assertTrue(result.guides().isEmpty());
    }

    @Test
    void nodeAnglesAreRelativeToTheIncidentRoadInsteadOfTheWorldGrid() {
        double baseAngle = Math.toRadians(30.0);
        RoadPoint startPoint = new RoadPoint(
                0.5 + Math.cos(baseAngle) * 10.0,
                64.0,
                0.5 + Math.sin(baseAngle) * 10.0
        );
        RoadSegment road = segment(
                1,
                new RoadPoint(0.5, 64.0, 0.5),
                startPoint,
                6,
                List.of(lane(0.0, LaneTravelDirection.FORWARD))
        );
        double rawAngle = Math.toRadians(116.0);
        CursorTarget raw = target(
                startPoint.x() + Math.cos(rawAngle) * 10.0,
                64.0,
                startPoint.z() + Math.sin(rawAngle) * 10.0
        );
        RoadSnapSettings settings = new RoadSnapSettings(
                2.5,
                1.0,
                RoadSnapPriority.ANGLE_TANGENT_GRID,
                false,
                false
        );

        RoadSnapResult snapped = RoadSnapResolver.resolve(
                raw,
                new CursorTarget(
                        BlockPos.containing(
                                startPoint.x(),
                                startPoint.y(),
                                startPoint.z()
                        ),
                        startPoint
                ),
                List.of(),
                List.of(road),
                RoadSnappingMode.NODES_AND_ANGLES,
                settings,
                false
        );
        double snappedAngle = Math.toDegrees(Math.atan2(
                snapped.target().roadPoint().z() - startPoint.z(),
                snapped.target().roadPoint().x() - startPoint.x()
        ));

        assertEquals(120.0, snappedAngle, 1.0e-8);
        assertFalse(snapped.guides().isEmpty());
        assertEquals(
                RoadSnapGuide.Kind.NODE_ANGLE,
                snapped.guides().get(0).kind()
        );
    }

    @Test
    void nodeAngleGuidesCanSnapTheFirstPoint() {
        RoadNodeId nodeId = new RoadNodeId(new UUID(9, 2));
        RoadPoint center = new RoadPoint(0.5, 64.0, 0.5);
        RoadNode endpoint = new RoadNode(nodeId, center);
        RoadSegment road = sharedSegment(
                14,
                nodeId,
                center,
                new RoadPoint(20.5, 64.0, 0.5),
                8
        );
        double rawAngle = Math.toRadians(87.0);
        CursorTarget raw = target(
                center.x() + Math.cos(rawAngle) * 10.0,
                64.0,
                center.z() + Math.sin(rawAngle) * 10.0
        );
        RoadSnapSettings settings = new RoadSnapSettings(
                2.5,
                1.0,
                RoadSnapPriority.ANGLE_TANGENT_GRID,
                false,
                false
        );

        RoadSnapResult snapped = RoadSnapResolver.resolve(
                raw,
                null,
                List.of(endpoint),
                List.of(road),
                RoadSnappingMode.ANGLES,
                settings,
                false
        );
        double snappedAngle = Math.toDegrees(Math.atan2(
                snapped.target().roadPoint().z() - center.z(),
                snapped.target().roadPoint().x() - center.x()
        ));

        assertEquals(90.0, snappedAngle, 1.0e-8);
        assertFalse(snapped.guides().isEmpty());
        assertEquals(
                RoadSnapGuide.Kind.NODE_ANGLE,
                snapped.guides().get(0).kind()
        );
    }

    @Test
    void defaultPriorityLetsAnActiveAngleGuideWinOverTheBlockGrid() {
        CursorTarget start = target(0.5, 64.0, 0.5);
        double radians = Math.toRadians(40.0);

        RoadSnapResult snapped = RoadSnapResolver.resolve(
                target(
                        start.roadPoint().x() + Math.cos(radians) * 10.0,
                        64.0,
                        start.roadPoint().z() + Math.sin(radians) * 10.0
                ),
                start,
                List.of(),
                List.of(),
                RoadSnappingMode.NODES_AND_ANGLES,
                RoadSnapSettings.DEFAULTS,
                false
        );

        assertEquals(
                45.0,
                Math.toDegrees(Math.atan2(
                        snapped.target().roadPoint().z()
                                - start.roadPoint().z(),
                        snapped.target().roadPoint().x()
                                - start.roadPoint().x()
                )),
                1.0e-9
        );
        assertFalse(snapped.guides().isEmpty());
    }

    @Test
    void aMultiArmJunctionKeepsACompactSelectableHitTarget() {
        RoadNodeId junctionId = new RoadNodeId(new UUID(9, 1));
        RoadPoint center = new RoadPoint(0.5, 64.0, 0.5);
        RoadNode junction = new RoadNode(junctionId, center);
        List<RoadSegment> incident = List.of(
                sharedSegment(
                        11,
                        junctionId,
                        center,
                        new RoadPoint(20.5, 64.0, 0.5),
                        14
                ),
                sharedSegment(
                        12,
                        junctionId,
                        center,
                        new RoadPoint(-20.5, 64.0, 0.5),
                        14
                ),
                sharedSegment(
                        13,
                        junctionId,
                        center,
                        new RoadPoint(0.5, 64.0, 20.5),
                        8
                )
        );

        CursorTarget raw = target(0.5, 64.0, -9.2);
        RoadSnapResult snapped = RoadSnapResolver.resolve(
                raw,
                null,
                List.of(junction),
                incident,
                RoadSnappingMode.NODES,
                snappingWithoutOptionalGuides(),
                false
        );

        assertTrue(RoadSnapResolver.hasJunctionFootprint(junction, incident));
        assertTrue(RoadSnapResolver.junctionFootprintRadius(
                junction,
                incident
        ) > RoadSnapSettings.DEFAULTS.range());
        assertEquals(raw, snapped.target());
    }

    @Test
    void curvedSurfaceProjectionNeverSamplesPastItsFinalEndpoint() {
        double strictLength = 195.61927629757975;
        int chordCount = (int) Math.ceil(strictLength / 0.5);
        assertTrue(strictLength * chordCount / chordCount > strictLength);
        ReferenceGeometry strictGeometry = new ReferenceGeometry() {
            @Override
            public RoadPoint start() {
                return new RoadPoint(0.5, 64.0, 0.5);
            }

            @Override
            public RoadPoint end() {
                return new RoadPoint(0.5 + strictLength, 64.0, 0.5);
            }

            @Override
            public double length() {
                return strictLength;
            }

            @Override
            public PathSample sample(double distance) {
                if (distance < 0.0 || distance > strictLength) {
                    throw new IllegalArgumentException(
                            "distance exceeded strict curve length"
                    );
                }
                return new PathSample(
                        distance,
                        new RoadPoint(0.5 + distance, 64.0, 0.5),
                        new RoadVector(1.0, 0.0, 0.0)
                );
            }
        };
        RoadSegment road = segment(
                99,
                strictGeometry,
                7,
                List.of(lane(0.0, LaneTravelDirection.FORWARD))
        );

        assertDoesNotThrow(() -> RoadSnapResolver.resolve(
                target(strictLength, 64.0, 1.0),
                null,
                List.of(),
                List.of(road),
                RoadSnappingMode.GUIDELINES,
                snappingWithoutOptionalGuides(),
                false
        ));
    }

    private static CursorTarget target(double x, double y, double z) {
        RoadPoint point = new RoadPoint(x, y, z);
        return new CursorTarget(BlockPos.containing(x, y, z), point);
    }

    private static void assertSnappedAngle(
            CursorTarget start,
            double rawDegrees,
            double expectedDegrees
    ) {
        double radians = Math.toRadians(rawDegrees);
        CursorTarget raw = target(
                start.roadPoint().x() + Math.cos(radians) * 10.0,
                start.roadPoint().y(),
                start.roadPoint().z() + Math.sin(radians) * 10.0
        );
        CursorTarget snapped = RoadSnapResolver.snap(
                raw,
                start,
                List.of(),
                RoadSnappingMode.NODES_AND_ANGLES
        );
        double snappedDegrees = Math.toDegrees(Math.atan2(
                snapped.roadPoint().z() - start.roadPoint().z(),
                snapped.roadPoint().x() - start.roadPoint().x()
        ));
        assertEquals(expectedDegrees, snappedDegrees, 1.0e-9);
    }

    private static RoadNode node(long id, RoadPoint point) {
        return new RoadNode(new RoadNodeId(new UUID(0, id)), point);
    }

    private static RoadSnapSettings snappingWithoutOptionalGuides() {
        return new RoadSnapSettings(
                2.5,
                1.0,
                RoadSnapPriority.TANGENT_ANGLE_GRID,
                false,
                false
        );
    }

    private static LaneComponent lane(
            double offset,
            LaneTravelDirection direction
    ) {
        return new LaneComponent(LaneType.VEHICLE, 3.0, offset, direction);
    }

    private static RoadSegment segment(
            long id,
            RoadPoint start,
            RoadPoint end,
            int surfaceWidth,
            List<LaneComponent> lanes
    ) {
        return segment(
                id,
                new StraightGeometry(start, end),
                surfaceWidth,
                lanes
        );
    }

    private static RoadSegment sharedSegment(
            long id,
            RoadNodeId junctionId,
            RoadPoint junction,
            RoadPoint end,
            int surfaceWidth
    ) {
        LaneComponent lane = lane(0.0, LaneTravelDirection.FORWARD);
        return RoadSegment.path(
                new RoadSegmentId(new UUID(10, id)),
                new RoadId(new UUID(11, id)),
                junctionId,
                new RoadNodeId(new UUID(12, id)),
                new StraightGeometry(junction, end),
                new ResourceLocation("roadbuilder", "junction_snap_test"),
                1,
                new RoadPresetSnapshot(
                        surfaceWidth,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        List.of(lane)
                ),
                TrafficHandedness.RHT,
                List.of(new RoadLaneId(new UUID(13, id)))
        );
    }

    private static RoadSegment segment(
            long id,
            ReferenceGeometry geometry,
            int surfaceWidth,
            List<LaneComponent> lanes
    ) {
        RoadSegmentId segmentId = new RoadSegmentId(new UUID(0, id));
        return RoadSegment.path(
                segmentId,
                new RoadId(new UUID(1, id)),
                new RoadNodeId(new UUID(2, id)),
                new RoadNodeId(new UUID(3, id)),
                geometry,
                new ResourceLocation("roadbuilder", "snap_test"),
                1,
                new RoadPresetSnapshot(
                        surfaceWidth,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        lanes
                ),
                TrafficHandedness.RHT,
                java.util.stream.LongStream.range(0, lanes.size())
                        .mapToObj(index -> new RoadLaneId(new UUID(4 + index, id)))
                        .toList()
        );
    }
}

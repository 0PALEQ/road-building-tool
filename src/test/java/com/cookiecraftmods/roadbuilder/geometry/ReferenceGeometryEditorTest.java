package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ReferenceGeometryEditorTest {
    @Test
    void movesOnlyTheSelectedCubicHandleAndKeepsBoundaryPoints() {
        CubicBezierGeometry source = new CubicBezierGeometry(
                new RoadPoint(0.0, 64.0, 0.0),
                new RoadPoint(3.0, 64.0, 0.0),
                new RoadPoint(7.0, 64.0, 5.0),
                new RoadPoint(10.0, 64.0, 5.0)
        );
        RoadPoint moved = new RoadPoint(2.0, 65.0, -3.0);

        CubicBezierGeometry edited = assertInstanceOf(
                CubicBezierGeometry.class,
                ReferenceGeometryEditor.moveHandle(source, 0, moved)
        );

        assertEquals(source.start(), edited.start());
        assertEquals(source.end(), edited.end());
        assertEquals(moved, edited.startControl());
        assertEquals(source.endControl(), edited.endControl());
    }

    @Test
    void tangentSnappingRetainsHandleLengthAndElevation() {
        RoadPoint endpoint = new RoadPoint(0.0, 64.0, 0.0);
        RoadPoint raw = new RoadPoint(5.0, 66.0, 0.4);
        RoadPoint snapped = ReferenceGeometryEditor.snapTangentHandle(
                endpoint,
                raw,
                Math.toRadians(15.0)
        );

        assertEquals(raw.y(), snapped.y());
        assertEquals(
                Math.hypot(raw.x(), raw.z()),
                Math.hypot(snapped.x(), snapped.z()),
                1.0e-9
        );
        assertEquals(0.0, snapped.z(), 1.0e-9);
    }

    @Test
    void curvedSplitUsesCanonicalCubicChildrenAtSharedBoundary() {
        QuadraticBezierGeometry source = new QuadraticBezierGeometry(
                new RoadPoint(0.0, 64.0, 0.0),
                new RoadPoint(5.0, 64.0, 8.0),
                new RoadPoint(10.0, 64.0, 0.0)
        );
        RoadPoint junction = source.sample(source.length() * 0.5).position();

        List<ReferenceGeometry> children = ReferenceGeometryEditor.split(
                source,
                List.of(0.5),
                List.of(junction)
        );

        assertEquals(2, children.size());
        assertEquals(source.start(), children.get(0).start());
        assertEquals(junction, children.get(0).end());
        assertEquals(junction, children.get(1).start());
        assertEquals(source.end(), children.get(1).end());
        assertInstanceOf(CubicBezierGeometry.class, children.get(0));
    }
}

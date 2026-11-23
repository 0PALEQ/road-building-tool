package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.FreehandGeometry;
import com.cookiecraftmods.roadbuilder.geometry.MultiCurveGeometry;
import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoundaboutGeometry;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RawRoadPreviewTest {
    @Test
    void measurementRunExcludesElevationAndFollowsCurves() {
        RawRoadPreview slope = new RawRoadPreview(new StraightGeometry(
                new RoadPoint(0, 0, 0), new RoadPoint(3, 12, 4)), 6);
        assertEquals(5.0, slope.horizontalLength(), 1.0e-9);
        assertEquals(13.0, slope.geometry().length(), 1.0e-9);
        RawRoadPreview curve = new RawRoadPreview(new QuadraticBezierGeometry(
                new RoadPoint(0, 0, 0), new RoadPoint(0, 0, 20),
                new RoadPoint(20, 0, 20)), 6);
        assertTrue(curve.horizontalLength() > Math.hypot(20, 20));
        assertEquals(curve.geometry().length(), curve.horizontalLength(), 0.1);
    }

    @Test
    void longWideHighwayPreviewSucceedsBeyondOldBlockCap() {
        StraightGeometry highway = new StraightGeometry(
                new RoadPoint(0.0, 64.0, 0.0),
                new RoadPoint(10_000.0, 64.0, 0.0)
        );
        RawRoadPreview preview = new RawRoadPreview(highway, 20.0);
        assertEquals(2, preview.crossSections().size());
        assertEquals(20.0, preview.width());
    }

    @Test
    void straightRoadRequiresExactlyTwoSections() {
        RawRoadPreview preview = new RawRoadPreview(
                new StraightGeometry(
                        new RoadPoint(0.0, 0.0, 0.0),
                        new RoadPoint(12.0, 0.0, 0.0)
                ),
                6.0
        );
        assertEquals(2, preview.crossSections().size());
    }

    @Test
    void totalCurveSectionsBoundedAtHugeLengths() {
        MultiCurveGeometry curve = new MultiCurveGeometry(List.of(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(5_000.0, 0.0, 0.0),
                new RoadPoint(10_000.0, 0.0, 0.0)
        ));
        RawRoadPreview preview = new RawRoadPreview(curve, 8.0);
        assertTrue(
                preview.crossSections().size() <= 257,
                "curve preview must stay within the total interval cap"
        );
    }

    @Test
    void sameGeometryDifferentWidthsKeepSameSectionCount() {
        QuadraticBezierGeometry curve = new QuadraticBezierGeometry(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(5.0, 0.0, 5.0),
                new RoadPoint(10.0, 0.0, 0.0)
        );
        RawRoadPreview narrow = new RawRoadPreview(curve, 2.0);
        RawRoadPreview wide = new RawRoadPreview(curve, 24.0);
        assertEquals(
                narrow.crossSections().size(),
                wide.crossSections().size()
        );
    }

    @Test
    void terminalEndpointIsExactStableFloatingValue() {
        double reported = 56.04462507680822;
        StraightGeometry geometry = new StraightGeometry(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(reported, 0.0, 0.0)
        );
        RawRoadPreview preview = new RawRoadPreview(geometry, 4.0);
        RawRoadPreview.CrossSection terminal =
                preview.crossSections().get(1);
        assertEquals(
                reported,
                (terminal.left().x() + terminal.right().x()) / 2.0
        );
    }

    @Test
    void leftAndRightEdgesMatchWidthOnDiagonalsAndSlopes() {
        StraightGeometry diagonal = new StraightGeometry(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(10.0, 0.0, 10.0)
        );
        RawRoadPreview preview = new RawRoadPreview(diagonal, 6.0);
        RawRoadPreview.CrossSection section =
                preview.crossSections().get(0);
        assertEquals(
                6.0,
                section.left().distanceTo(section.right()),
                1.0e-9
        );
        assertEquals(0.0, section.left().y());
        assertEquals(0.0, section.right().y());

        StraightGeometry slope = new StraightGeometry(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(10.0, 5.0, 0.0)
        );
        RawRoadPreview sloped = new RawRoadPreview(slope, 6.0);
        RawRoadPreview.CrossSection slopedSection =
                sloped.crossSections().get(1);
        assertEquals(
                6.0,
                slopedSection.left().distanceTo(slopedSection.right()),
                1.0e-9
        );
        assertEquals(5.0, slopedSection.left().y());
        assertEquals(5.0, slopedSection.right().y());
    }

    @Test
    void quadraticMultiCurveFreehandAndRoundaboutSampleSafely() {
        assertDoesNotThrow(() -> new RawRoadPreview(
                new QuadraticBezierGeometry(
                        new RoadPoint(0.0, 0.0, 0.0),
                        new RoadPoint(5.0, 0.0, 5.0),
                        new RoadPoint(10.0, 0.0, 0.0)
                ),
                6.0
        ));
        assertDoesNotThrow(() -> new RawRoadPreview(
                new MultiCurveGeometry(List.of(
                        new RoadPoint(0.0, 0.0, 0.0),
                        new RoadPoint(4.0, 0.0, 4.0),
                        new RoadPoint(8.0, 0.0, 0.0)
                )),
                6.0
        ));
        assertDoesNotThrow(() -> new RawRoadPreview(
                new FreehandGeometry(List.of(
                        new RoadPoint(0.0, 0.0, 0.0),
                        new RoadPoint(2.0, 0.0, 1.0),
                        new RoadPoint(4.0, 0.0, 0.0)
                )),
                6.0
        ));
        RoundaboutGeometry roundabout = new RoundaboutGeometry(
                new RoadPoint(0.0, 0.0, 0.0),
                new RoadPoint(8.0, 0.0, 0.0)
        );
        RawRoadPreview ring = new RawRoadPreview(roundabout, 6.0);
        List<RawRoadPreview.CrossSection> sections = ring.crossSections();
        assertEquals(
                sections.get(0).left(),
                sections.get(sections.size() - 1).left()
        );
        assertEquals(
                sections.get(0).right(),
                sections.get(sections.size() - 1).right()
        );
    }
}

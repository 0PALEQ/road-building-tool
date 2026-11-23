package com.cookiecraftmods.roadbuilder.geometry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JunctionElevationProfileTest {
    @Test void spreadsTheClimbOverTheWholeRoadAndKeepsEndpointGrades() {
        for (double rise : new double[]{-12,12}) {
            var original=new StraightGeometry(new RoadPoint(0,70,0),new RoadPoint(120,70+rise,0));
            var fitted=JunctionElevationProfile.fit(original,16,0);
            assertInstanceOf(MultiCurveGeometry.class,fitted);
            assertEquals(original.start(),fitted.start()); assertEquals(original.end(),fitted.end());
            assertEquals(0,fitted.sample(0).tangent().y(),1e-9);
            var end=fitted.sample(fitted.length()).tangent();
            assertEquals(rise/120,end.y()/end.horizontalLength(),1e-8);
            assertEquals(70,fitted.sample(10).position().y(),1e-8);
            double previous=70;
            for(double d=0;d<fitted.length();d+=0.1) {
                var sample=fitted.sample(d);
                assertTrue(Math.abs(sample.tangent().y()/sample.tangent().horizontalLength())<0.14,
                        "the climb must not be squeezed into the old 48-block footprint");
                assertTrue((sample.position().y()-previous)*rise>=-1e-8);
                previous=sample.position().y();
            }
        }
    }

    @Test void doesNotReprofileAnAlreadyLevelBridgeEntrance() {
        var source=MultiCurveGeometry.fromControlPoints(java.util.List.of(
                new RoadPoint(0,64,0),new RoadPoint(10,64,0),new RoadPoint(20,64,0),new RoadPoint(30,64,0),
                new RoadPoint(50,64,0),new RoadPoint(70,80,0),new RoadPoint(90,80,0),
                new RoadPoint(110,80,0),new RoadPoint(130,64,0),new RoadPoint(150,64,0)));
        assertSame(source,JunctionElevationProfile.fit(source,12,0));
    }

    @Test void levelsBothJunctionsWithoutChangingTheHorizontalCurve() {
        var source=new CubicBezierGeometry(new RoadPoint(0,64,0),new RoadPoint(40,68,0),
                new RoadPoint(60,72,40),new RoadPoint(100,76,40));
        var fitted=JunctionElevationProfile.fit(source,10,10);
        assertEquals(0,fitted.sample(0).tangent().y(),1e-9);
        assertEquals(0,fitted.sample(fitted.length()).tangent().y(),1e-9);
        var planar=new CubicBezierGeometry(new RoadPoint(0,0,0),new RoadPoint(40,0,0),
                new RoadPoint(60,0,40),new RoadPoint(100,0,40));
        for(var span:((MultiCurveGeometry)fitted).spans()) for(int i=0;i<=8;i++) {
            var p=span.positionAt(i/8.0);
            double nearest=Double.POSITIVE_INFINITY;
            for(int j=0;j<=2000;j++) {
                var q=planar.positionAt(j/2000.0);
                nearest=Math.min(nearest,Math.hypot(p.x()-q.x(),p.z()-q.z()));
            }
            assertTrue(nearest<0.05,"horizontal alignment must be preserved");
        }
    }
}

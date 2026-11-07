package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RawRoadPreview {
    public static final int MINIMUM_INTERVALS = 8;
    public static final int MAXIMUM_INTERVALS = 256;
    private static final double MINIMUM_HORIZONTAL_LENGTH = 1.0e-6;

    private final List<CrossSection> crossSections;
    private final double width;
    private final ReferenceGeometry geometry;
    private final double horizontalLength;

    public RawRoadPreview(ReferenceGeometry geometry, double width) {
        Objects.requireNonNull(geometry, "geometry");
        if (!Double.isFinite(width) || width <= 0.0) {
            throw new IllegalArgumentException(
                    "width must be finite and positive"
            );
        }
        this.width = width;
        this.geometry = geometry;
        this.crossSections = Collections.unmodifiableList(
                build(geometry, width)
        );
        double run = 0.0;
        RoadPoint previous = geometry.start();
        for (int index = 1; index < crossSections.size(); index++) {
            CrossSection section = crossSections.get(index);
            RoadPoint next = new RoadPoint(
                    (section.left().x() + section.right().x()) / 2.0,
                    section.left().y(),
                    (section.left().z() + section.right().z()) / 2.0);
            run += Math.hypot(next.x() - previous.x(), next.z() - previous.z());
            previous = next;
        }
        this.horizontalLength = run;
    }

    public record CrossSection(RoadPoint left, RoadPoint right) {
    }

    public List<CrossSection> crossSections() {
        return crossSections;
    }

    public double width() {
        return width;
    }

    public ReferenceGeometry geometry() {
        return geometry;
    }

    /** Sampled horizontal travel along the preview, including curves. */
    public double horizontalLength() {
        return horizontalLength;
    }

    private static List<CrossSection> build(
            ReferenceGeometry geometry,
            double width
    ) {
        int intervalCount = intervalCount(geometry);
        double length = geometry.length();
        double halfWidth = width / 2.0;
        List<CrossSection> result = new ArrayList<>(intervalCount + 1);
        RoadVector previousHorizontal = null;
        for (int index = 0; index <= intervalCount; index++) {
            double distance = index == intervalCount
                    ? length
                    : length * index / intervalCount;
            var sample = geometry.sample(distance);
            RoadPoint position = sample.position();
            RoadVector horizontal = horizontalTangent(
                    sample.tangent(),
                    previousHorizontal
            );
            previousHorizontal = horizontal;
            RoadVector normal = new RoadVector(
                    horizontal.z(),
                    0.0,
                    -horizontal.x()
            );
            result.add(new CrossSection(
                    new RoadPoint(
                            position.x() + normal.x() * halfWidth,
                            position.y(),
                            position.z() + normal.z() * halfWidth
                    ),
                    new RoadPoint(
                            position.x() - normal.x() * halfWidth,
                            position.y(),
                            position.z() - normal.z() * halfWidth
                    )
            ));
        }
        return result;
    }

    static int intervalCount(ReferenceGeometry geometry) {
        if (geometry instanceof StraightGeometry) {
            return 1;
        }
        int count = (int) Math.ceil(geometry.length() / 2.0);
        return Math.max(
                MINIMUM_INTERVALS,
                Math.min(MAXIMUM_INTERVALS, count)
        );
    }

    private static RoadVector horizontalTangent(
            RoadVector tangent,
            RoadVector previous
    ) {
        double horizontalLength = tangent.horizontalLength();
        if (horizontalLength > MINIMUM_HORIZONTAL_LENGTH) {
            return new RoadVector(
                    tangent.x() / horizontalLength,
                    0.0,
                    tangent.z() / horizontalLength
            );
        }
        if (previous != null) {
            return previous;
        }
        return new RoadVector(1.0, 0.0, 0.0);
    }
}

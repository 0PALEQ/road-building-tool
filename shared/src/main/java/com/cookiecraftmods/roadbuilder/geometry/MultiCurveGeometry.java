package com.cookiecraftmods.roadbuilder.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class MultiCurveGeometry implements ReferenceGeometry {
    public static final int MAXIMUM_EXPLICIT_CONTROL_POINTS = 256;
    private final List<RoadPoint> anchors;
    private final List<CubicBezierGeometry> spans;
    private final double[] cumulativeLengths;
    private final double length;
    private final boolean explicitSpans;

    public static MultiCurveGeometry fromControlPoints(List<RoadPoint> points) {
        if(points.size()<7 || (points.size()-1)%3!=0) throw new IllegalArgumentException("joined cubics need 3n+1 control points");
        List<CubicBezierGeometry> spans=new ArrayList<>();
        for(int i=0;i<points.size()-1;i+=3)
            spans.add(new CubicBezierGeometry(points.get(i),points.get(i+1),points.get(i+2),points.get(i+3)));
        return new MultiCurveGeometry(spans,true);
    }

    private MultiCurveGeometry(List<CubicBezierGeometry> spans,boolean explicit) {
        this.explicitSpans=explicit;
        this.spans=List.copyOf(spans);
        List<RoadPoint> points=new ArrayList<>();
        points.add(spans.get(0).start());
        spans.forEach(span->points.add(span.end()));
        this.anchors=List.copyOf(points);
        this.cumulativeLengths=new double[spans.size()+1];
        double accumulated=0;
        for(int i=0;i<spans.size();i++) cumulativeLengths[i+1]=accumulated+=spans.get(i).length();
        this.length=accumulated;
    }

    public boolean hasExplicitSpans() { return explicitSpans; }

    public List<RoadPoint> controlPoints() {
        List<RoadPoint> points=new ArrayList<>();
        points.add(start());
        for(var span:spans) points.addAll(span.controlPoints().subList(1,4));
        return List.copyOf(points);
    }

    public MultiCurveGeometry(List<RoadPoint> anchors) {
        this.explicitSpans=false;
        Objects.requireNonNull(anchors, "anchors");
        if (anchors.size() < 3) {
            throw new IllegalArgumentException(
                    "a complex curve requires at least three anchors"
            );
        }
        ArrayList<RoadPoint> copied = new ArrayList<>(anchors.size());
        for (RoadPoint anchor : anchors) {
            RoadPoint point = Objects.requireNonNull(
                    anchor,
                    "anchors contains null"
            );
            if (!copied.isEmpty()
                    && copied.get(copied.size() - 1).equals(point)) {
                throw new IllegalArgumentException(
                        "adjacent anchors must be distinct"
                );
            }
            copied.add(point);
        }
        QuadraticBezierGeometry.requireDistinctHorizontalEndpoints(
                copied.get(0),
                copied.get(copied.size() - 1)
        );
        this.anchors = Collections.unmodifiableList(copied);
        this.spans = Collections.unmodifiableList(createSpans(copied));
        this.cumulativeLengths = new double[spans.size() + 1];
        double accumulated = 0.0;
        for (int index = 0; index < spans.size(); index++) {
            accumulated += spans.get(index).length();
            if (!Double.isFinite(accumulated)) {
                throw new IllegalArgumentException(
                        "complex curve length must be finite"
                );
            }
            cumulativeLengths[index + 1] = accumulated;
        }
        this.length = accumulated;
    }

    public List<RoadPoint> anchors() {
        return anchors;
    }

    public List<CubicBezierGeometry> spans() {
        return spans;
    }

    @Override
    public RoadPoint start() {
        return anchors.get(0);
    }

    @Override
    public RoadPoint end() {
        return anchors.get(anchors.size() - 1);
    }

    @Override
    public double length() {
        return length;
    }

    public int adaptiveSampleCount() {
        int count = 1;
        for (CubicBezierGeometry span : spans) {
            count += span.adaptiveSampleCount() - 1;
        }
        return count;
    }

    @Override
    public PathSample sample(double distance) {
        requireDistance(distance);
        if (distance == length) {
            CubicBezierGeometry finalSpan = spans.get(spans.size() - 1);
            PathSample local = finalSpan.sample(finalSpan.length());
            return new PathSample(distance, local.position(), local.tangent());
        }

        int spanIndex = spanAt(distance);
        double localDistance = distance - cumulativeLengths[spanIndex];
        PathSample local = spans.get(spanIndex).sample(localDistance);
        return new PathSample(distance, local.position(), local.tangent());
    }

    private static List<CubicBezierGeometry> createSpans(
            List<RoadPoint> anchors
    ) {
        ArrayList<RoadVector> tangents = new ArrayList<>(anchors.size());
        for (int index = 0; index < anchors.size(); index++) {
            tangents.add(tangentAt(anchors, index));
        }

        ArrayList<CubicBezierGeometry> result = new ArrayList<>(
                anchors.size() - 1
        );
        for (int index = 0; index < anchors.size() - 1; index++) {
            RoadPoint start = anchors.get(index);
            RoadPoint end = anchors.get(index + 1);
            double chordLength = start.distanceTo(end);
            if (start.vectorTo(end).horizontalLength() == 0.0) {
                throw new IllegalArgumentException(
                        "every complex-curve span needs a horizontal direction"
                );
            }
            RoadPoint startControl = start.add(
                    tangents.get(index).scale(chordLength / 3.0)
            );
            RoadPoint endControl = end.subtract(
                    tangents.get(index + 1).scale(chordLength / 3.0)
            );
            result.add(new CubicBezierGeometry(
                    start,
                    startControl,
                    endControl,
                    end
            ));
        }
        return result;
    }

    private static RoadVector tangentAt(
            List<RoadPoint> anchors,
            int index
    ) {
        RoadVector direction;
        if (index == 0) {
            direction = anchors.get(0).vectorTo(anchors.get(1));
        } else if (index == anchors.size() - 1) {
            direction = anchors.get(index - 1).vectorTo(anchors.get(index));
        } else {
            RoadVector incoming = anchors.get(index - 1)
                    .vectorTo(anchors.get(index));
            RoadVector outgoing = anchors.get(index)
                    .vectorTo(anchors.get(index + 1));
            // Estimate the derivative with respect to chord distance. Equal
            // weights create artificial sharp bends beside very short spans,
            // such as the endpoint samples of a parallel road.
            double incomingLength = incoming.length();
            double outgoingLength = outgoing.length();
            direction = incoming.normalize().scale(outgoingLength)
                    .add(outgoing.normalize().scale(incomingLength));
            if (direction.length() <= 1.0e-10
                    || direction.horizontalLength() <= 1.0e-10) {
                direction = outgoing;
            }
        }
        if (direction.horizontalLength() <= 1.0e-10) {
            throw new IllegalArgumentException(
                    "a complex curve tangent needs a horizontal direction"
            );
        }
        return direction.normalize();
    }

    private int spanAt(double distance) {
        int low = 0;
        int high = spans.size() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (cumulativeLengths[middle + 1] <= distance) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private void requireDistance(double distance) {
        if (!Double.isFinite(distance)) {
            throw new IllegalArgumentException("distance must be finite");
        }
        if (distance < 0.0 || distance > length) {
            throw new IllegalArgumentException(
                    "distance must be between 0 and " + length
            );
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof MultiCurveGeometry geometry
                && explicitSpans==geometry.explicitSpans && spans.equals(geometry.spans);
    }

    @Override
    public int hashCode() {
        return 31*spans.hashCode()+Boolean.hashCode(explicitSpans);
    }

    @Override
    public String toString() {
        return "MultiCurveGeometry[anchors=" + anchors + "]";
    }
}

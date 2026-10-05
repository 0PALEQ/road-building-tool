package com.cookiecraftmods.roadbuilder.client.render;

final class SegmentHighlightSampling {
    private SegmentHighlightSampling() {
    }

    static int sampleCount(double length) {
        if (!Double.isFinite(length) || length <= 0.0) {
            throw new IllegalArgumentException("length must be finite and positive");
        }
        return Math.max(1, (int) Math.ceil(length * 2.0));
    }

    static double distance(double length, int sampleCount, int index) {
        if (!Double.isFinite(length) || length <= 0.0) {
            throw new IllegalArgumentException("length must be finite and positive");
        }
        if (sampleCount < 1) {
            throw new IllegalArgumentException("sampleCount must be positive");
        }
        if (index < 0 || index > sampleCount) {
            throw new IllegalArgumentException("index must identify a generated sample");
        }
        if (index == sampleCount) {
            return length;
        }
        return Math.min(length, length * index / sampleCount);
    }
}

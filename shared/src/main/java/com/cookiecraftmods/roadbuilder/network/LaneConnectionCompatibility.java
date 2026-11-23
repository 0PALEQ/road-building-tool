package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.preset.LaneType;

import java.util.Objects;

/** Rules for deliberately connecting lanes with different access levels. */
public final class LaneConnectionCompatibility {
    private LaneConnectionCompatibility() {
    }

    public static boolean canTransition(LaneType from, LaneType to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return from == to
                || from == LaneType.ROUNDABOUT || to == LaneType.ROUNDABOUT
                || from == LaneType.BUS && to == LaneType.VEHICLE
                || from == LaneType.VEHICLE && to == LaneType.BUS;
    }
}

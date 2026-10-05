package com.cookiecraftmods.roadbuilder.preset;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaneComponentTest {
    @Test
    void parsesExplicitLaneTypeOffsetAndTravelDirection() {
        LaneComponent lane = LaneComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {
                  "type": "vehicle",
                  "width": 3.25,
                  "offset": -1.75,
                  "direction": "forward"
                }
                """))
                .result()
                .orElseThrow();

        assertAll(
                () -> assertEquals(LaneType.VEHICLE, lane.type()),
                () -> assertEquals(3.25, lane.width()),
                () -> assertEquals(-1.75, lane.lateralOffset()),
                () -> assertEquals(LaneTravelDirection.FORWARD, lane.direction()),
                () -> assertTrue(lane.direction().followsReferenceDirection())
        );
    }

    @Test
    void mirrorsOnlyTheOffsetForLeftHandTraffic() {
        LaneComponent lane = new LaneComponent(
                LaneType.VEHICLE,
                3.0,
                -1.75,
                LaneTravelDirection.FORWARD
        );

        LaneComponent rht = lane.forTrafficHandedness(TrafficHandedness.RHT);
        LaneComponent lht = lane.forTrafficHandedness(TrafficHandedness.LHT);

        assertAll(
                () -> assertSame(lane, rht),
                () -> assertNotSame(lane, lht),
                () -> assertEquals(1.75, lht.lateralOffset()),
                () -> assertEquals(lane.type(), lht.type()),
                () -> assertEquals(lane.width(), lht.width()),
                () -> assertEquals(LaneTravelDirection.FORWARD, lht.direction())
        );
    }

    @Test
    void rejectsInvalidLaneValuesThroughConstructorAndCodec() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new LaneComponent(
                        null, 3.0, 0.0, LaneTravelDirection.FORWARD)),
                () -> assertThrows(IllegalArgumentException.class, () -> new LaneComponent(
                        LaneType.VEHICLE, 0.0, 0.0, LaneTravelDirection.FORWARD)),
                () -> assertThrows(IllegalArgumentException.class, () -> new LaneComponent(
                        LaneType.VEHICLE, Double.NaN, 0.0, LaneTravelDirection.FORWARD)),
                () -> assertThrows(IllegalArgumentException.class, () -> new LaneComponent(
                        LaneType.VEHICLE, 3.0, Double.POSITIVE_INFINITY,
                        LaneTravelDirection.FORWARD)),
                () -> assertTrue(LaneComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                        {
                          "type": "vehicle",
                          "width": -1.0,
                          "offset": 0.0,
                          "direction": "forward"
                        }
                        """)).error().isPresent()),
                () -> assertTrue(LaneComponent.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                        {
                          "type": "vehicle",
                          "width": 3.0,
                          "offset": 0.0,
                          "direction": "sideways"
                        }
                        """)).error().isPresent())
        );
    }
}

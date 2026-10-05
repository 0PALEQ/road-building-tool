package com.cookiecraftmods.roadbuilder.network;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadNodePortResolverTest {
    private static final ResourceLocation PRESET_ID =
            new ResourceLocation("roadbuilder", "port_test");
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            1,
            7.0,
            new ResourceLocation("minecraft", "gray_concrete"),
            List.of(
                    new LaneComponent(
                            LaneType.VEHICLE,
                            3.0,
                            -1.75,
                            LaneTravelDirection.FORWARD
                    ),
                    new LaneComponent(
                            LaneType.VEHICLE,
                            3.0,
                            1.75,
                            LaneTravelDirection.BACKWARD
                    )
            )
    );
    private static final RoadPreset NARROW_PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            1,
            1.0,
            new ResourceLocation("minecraft", "gray_concrete"),
            List.of(
                    new LaneComponent(
                            LaneType.VEHICLE,
                            1.0,
                            0.0,
                            LaneTravelDirection.FORWARD
                    )
            )
    );

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void validClusterNeedsNoReanchor() {
        RoadNode node = node(1, 0.5, 0.5);
        RoadSegment trunk = straightSegment(
                2,
                3,
                node.id(),
                node(4, 10.5, 0.5).id(),
                new RoadPoint(0.5, 64.0, 0.5),
                new RoadPoint(10.5, 64.0, 0.5),
                PRESET
        );
        RoadSegment ramp = straightSegment(
                5,
                6,
                node.id(),
                node(7, 0.5, 12.5).id(),
                new RoadPoint(0.5, 64.0, 2.5),
                new RoadPoint(0.5, 64.0, 12.5),
                NARROW_PRESET
        );

        assertTrue(RoadNodePortResolver.isValidTopology(
                node,
                List.of(trunk, ramp)
        ));
        assertTrue(RoadNodePortResolver.reanchor(
                node,
                List.of(trunk, ramp)
        ).isEmpty());
    }

    @Test
    void boundedLostAnchorReanchorsToEndpointPreservingNodeId() {
        RoadNode node = node(1, 0.5, 0.5);
        RoadSegment ramp = straightSegment(
                2,
                3,
                node.id(),
                node(4, 0.5, 12.5).id(),
                new RoadPoint(0.5, 64.0, 2.5),
                new RoadPoint(0.5, 64.0, 12.5),
                NARROW_PRESET
        );

        assertFalse(RoadNodePortResolver.isValidTopology(
                node,
                List.of(ramp)
        ));
        RoadNode reanchored = RoadNodePortResolver.reanchor(
                node,
                List.of(ramp)
        ).orElseThrow();
        assertEquals(node.id(), reanchored.id());
        assertEquals(new RoadPoint(0.5, 64.0, 2.5), reanchored.position());
        assertTrue(RoadNodePortResolver.isValidTopology(
                reanchored,
                List.of(ramp)
        ));
    }

    @Test
    void distantEndpointIsNotReanchored() {
        RoadNode node = node(1, 0.5, 100.5);
        RoadSegment ramp = straightSegment(
                2,
                3,
                node.id(),
                node(4, 0.5, 12.5).id(),
                new RoadPoint(0.5, 64.0, 2.5),
                new RoadPoint(0.5, 64.0, 12.5),
                NARROW_PRESET
        );

        assertFalse(RoadNodePortResolver.isValidTopology(
                node,
                List.of(ramp)
        ));
        assertTrue(RoadNodePortResolver.reanchor(
                node,
                List.of(ramp)
        ).isEmpty());
    }

    @Test
    void disconnectedEndpointsAreNotReanchored() {
        RoadNode node = node(1, 0.5, 0.5);
        RoadSegment north = straightSegment(
                2,
                3,
                node.id(),
                node(4, 0.5, 12.5).id(),
                new RoadPoint(0.5, 64.0, 2.5),
                new RoadPoint(0.5, 64.0, 12.5),
                NARROW_PRESET
        );
        RoadSegment south = straightSegment(
                5,
                6,
                node.id(),
                node(7, 0.5, -12.5).id(),
                new RoadPoint(0.5, 64.0, -2.5),
                new RoadPoint(0.5, 64.0, -12.5),
                NARROW_PRESET
        );

        assertFalse(RoadNodePortResolver.isValidTopology(
                node,
                List.of(north, south)
        ));
        assertTrue(RoadNodePortResolver.reanchor(
                node,
                List.of(north, south)
        ).isEmpty());
    }

    private static RoadNode node(long id, double x, double z) {
        return new RoadNode(
                new RoadNodeId(new UUID(0, id)),
                new RoadPoint(x, 64.0, z)
        );
    }

    private static RoadSegment straightSegment(
            long segmentId,
            long laneId,
            RoadNodeId startNodeId,
            RoadNodeId endNodeId,
            RoadPoint start,
            RoadPoint end,
            RoadPreset preset
    ) {
        List<RoadLaneId> laneIds = new java.util.ArrayList<>();
        for (int index = 0; index < preset.lanes().size(); index++) {
            laneIds.add(new RoadLaneId(new UUID(0, laneId + index)));
        }
        return RoadSegment.straight(
                new RoadSegmentId(new UUID(0, segmentId)),
                new RoadId(new UUID(0, segmentId + 100)),
                startNodeId,
                endNodeId,
                new StraightGeometry(start, end),
                PRESET_ID,
                preset.revision(),
                RoadPresetSnapshot.from(preset),
                TrafficHandedness.RHT,
                laneIds
        );
    }
}

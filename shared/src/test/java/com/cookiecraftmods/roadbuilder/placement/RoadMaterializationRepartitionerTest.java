package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.network.RoadId;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadMaterializationRepartitionerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void unmatchedPositionCanBeAssignedToCurveWithEndpointRounding() {
        RoadSegment child = child(0.0);
        BlockPos unmatched = new BlockPos(50, 64, 10);
        PlacementOperation source = PlacementOperation.create(
                PlacementOperationId.create(), Level.OVERWORLD, List.of(unmatched)
        );

        var result = new RoadMaterializationRepartitioner(ConstructionLimits.defaults())
                .prepare(source, List.of(child), Level.OVERWORLD, Map.of());

        assertEquals(source.id(), result.sourceOperationId());
        assertEquals(List.of(unmatched), result.partitions().get(0).positions());
        assertEquals(RoadMaterializationOwner.segment(child.id()),
                result.partitions().get(0).owner());
    }

    @Test
    void emptyChildCanRetainClosestPositionWithEndpointRounding() {
        RoadSegment first = child(0.0);
        RoadSegment second = child(20.0);
        BlockPos retained = new BlockPos(50, 64, 0);
        BlockPos farther = new BlockPos(99, 64, 0);
        PlacementOperation source = PlacementOperation.create(
                PlacementOperationId.create(), Level.OVERWORLD, List.of(retained, farther)
        );

        var result = new RoadMaterializationRepartitioner(ConstructionLimits.defaults())
                .prepare(source, List.of(first, second), Level.OVERWORLD, Map.of());

        assertEquals(2, result.partitions().size());
        assertEquals(source.positions(), result.partitions().get(0).positions());
        assertEquals(List.of(retained), result.partitions().get(1).positions());
    }

    private static RoadSegment child(double z) {
        double reportedLength = 99.13584865422688;
        var geometry = new CubicBezierGeometry(
                new RoadPoint(0, 64, z),
                new RoadPoint(reportedLength / 3, 64, z),
                new RoadPoint(reportedLength * 2 / 3, 64, z),
                new RoadPoint(reportedLength, 64, z)
        );
        double length = geometry.length();
        int samples = (int) Math.ceil(length * 2);
        assertEquals(reportedLength, length);
        assertTrue(length * samples / samples > length,
                "fixture must reproduce the endpoint rounding overflow");
        return RoadSegment.path(
                new RoadSegmentId(UUID.randomUUID()), new RoadId(UUID.randomUUID()),
                new RoadNodeId(UUID.randomUUID()), new RoadNodeId(UUID.randomUUID()),
                geometry, MinecraftIds.id("roadbuilder", "test"), 1,
                new RoadPresetSnapshot(1, MinecraftIds.id("minecraft", "stone"),
                        List.of(new LaneComponent(LaneType.VEHICLE, 1, 0,
                                LaneTravelDirection.FORWARD))),
                TrafficHandedness.RHT, List.of(new RoadLaneId(UUID.randomUUID()))
        );
    }
}

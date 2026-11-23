package com.cookiecraftmods.roadbuilder.client.tool;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoadVector;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphLimits;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadSegmentHoverSelectorTest {
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            1,
            7,
            MinecraftIds.id("minecraft", "gray_concrete"),
            List.of(new LaneComponent(
                    LaneType.VEHICLE,
                    3,
                    0,
                    LaneTravelDirection.FORWARD
            ))
    );

    @Test
    void selectsTheNearestSynchronizedVirtualSegmentWithoutBlockScanning() {
        AtomicLong nextId = new AtomicLong(1);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, nextId.getAndIncrement()),
                () -> { }
        );
        RoadSegment near = addSegment(graph, 0.5);
        addSegment(graph, 12.5);

        var selected = RoadSegmentHoverSelector.select(
                graph.snapshot(),
                new CursorRay(
                        new RoadPoint(5.5, 80, 0.5),
                        new RoadVector(0, -1, 0),
                        128
                )
        );

        assertEquals(near.id(), selected.orElseThrow().id());
    }

    @Test
    void returnsEmptyWhenEveryVirtualPathIsOutsideItsPickRadius() {
        AtomicLong nextId = new AtomicLong(1);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                RoadGraphLimits.defaults(),
                () -> new UUID(0, nextId.getAndIncrement()),
                () -> { }
        );
        addSegment(graph, 0.5);

        assertTrue(RoadSegmentHoverSelector.select(
                graph.snapshot(),
                new CursorRay(
                        new RoadPoint(5.5, 80, 30.5),
                        new RoadVector(0, -1, 0),
                        128
                )
        ).isEmpty());
    }

    private static RoadSegment addSegment(RoadGraph graph, double z) {
        var addition = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64, z),
                        new RoadPoint(10.5, 64, z)
                ),
                MinecraftIds.id("roadbuilder", "test"),
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(addition);
        return addition.segment();
    }
}

package com.cookiecraftmods.roadbuilder.client.networking;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRoadGraphCacheTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void keepsNewestSnapshotPerDimensionAndClearsWithTheConnection() {
        ClientRoadGraphCache cache = new ClientRoadGraphCache();
        RoadGraphSnapshot revisionTwo = emptySnapshot(Level.OVERWORLD, 2);
        cache.replace(revisionTwo);
        cache.replace(emptySnapshot(Level.OVERWORLD, 1));
        cache.replace(emptySnapshot(Level.NETHER, 4));

        assertEquals(revisionTwo, cache.snapshot(Level.OVERWORLD).orElseThrow());
        assertEquals(2, cache.knownRevision(Level.OVERWORLD));
        assertEquals(4, cache.knownRevision(Level.NETHER));
        assertEquals(-1, cache.knownRevision(Level.END));

        cache.clear();

        assertTrue(cache.snapshot(Level.OVERWORLD).isEmpty());
        assertTrue(cache.snapshot(Level.NETHER).isEmpty());
    }

    @Test
    void appliesExactNextDeltaAndRejectsMissingStaleOrGappedBases() {
        ClientRoadGraphCache cache = new ClientRoadGraphCache();
        RoadGraph graph = populatedGraph();
        RoadGraphSnapshot populated = graph.snapshot();
        RoadGraphSnapshot empty = emptySnapshot(Level.OVERWORLD, 0);
        RoadGraphDelta addition = RoadGraphDelta.between(empty, populated);

        assertEquals(
                ClientRoadGraphCache.DeltaResult.MISSING_BASE,
                cache.apply(addition)
        );
        cache.replace(empty);
        assertEquals(ClientRoadGraphCache.DeltaResult.APPLIED, cache.apply(addition));
        assertEquals(populated, cache.snapshot(Level.OVERWORLD).orElseThrow());
        assertEquals(ClientRoadGraphCache.DeltaResult.STALE, cache.apply(addition));

        RoadGraphDelta artificialGap = new RoadGraphDelta(
                Level.OVERWORLD,
                populated.revision() + 2,
                populated.revision() + 3,
                Map.of(),
                List.of(),
                Map.of(),
                List.of(),
                Map.of(),
                List.of(),
                Map.of(),
                List.of()
        );
        assertEquals(
                ClientRoadGraphCache.DeltaResult.REVISION_GAP,
                cache.apply(artificialGap)
        );
        assertEquals(populated, cache.snapshot(Level.OVERWORLD).orElseThrow());
    }

    @Test
    void removalDeltaDeletesTheFinalSegmentsBoundaryNodesFromTheClientCache() {
        ClientRoadGraphCache cache = new ClientRoadGraphCache();
        RoadGraph graph = populatedGraph();
        RoadGraphSnapshot before = graph.snapshot();
        var segment = before.segments().values().stream().findFirst().orElseThrow();
        graph.applyRemoval(graph.prepareSegmentRemoval(segment.id()));
        RoadGraphSnapshot after = graph.snapshot();
        cache.replace(before);

        assertEquals(
                ClientRoadGraphCache.DeltaResult.APPLIED,
                cache.apply(RoadGraphDelta.between(before, after))
        );
        RoadGraphSnapshot cached = cache.snapshot(Level.OVERWORLD).orElseThrow();
        assertTrue(cached.segments().isEmpty());
        assertTrue(cached.nodes().isEmpty());
    }

    private static RoadGraphSnapshot emptySnapshot(
            net.minecraft.resources.ResourceKey<Level> dimension,
            long revision
    ) {
        return new RoadGraphSnapshot(
                dimension,
                revision,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );
    }

    private static RoadGraph populatedGraph() {
        AtomicLong ids = new AtomicLong(1);
        RoadGraph graph = new RoadGraph(
                Level.OVERWORLD,
                com.cookiecraftmods.roadbuilder.network.RoadGraphLimits.defaults(),
                () -> new UUID(0, ids.getAndIncrement()),
                () -> {
                }
        );
        RoadPreset preset = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                3.0,
                new ResourceLocation("minecraft", "gray_concrete"),
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        3.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                ))
        );
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(5.5, 64.0, 0.5)
                ),
                new ResourceLocation("roadbuilder", "test"),
                preset,
                TrafficHandedness.RHT
        ));
        return graph;
    }
}

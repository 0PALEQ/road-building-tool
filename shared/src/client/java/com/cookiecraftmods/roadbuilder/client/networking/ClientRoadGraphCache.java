package com.cookiecraftmods.roadbuilder.client.networking;

import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ClientRoadGraphCache {
    public enum DeltaResult {
        APPLIED,
        STALE,
        MISSING_BASE,
        REVISION_GAP,
        INVALID
    }

    private final Map<ResourceKey<Level>, RoadGraphSnapshot> snapshots = new HashMap<>();

    public synchronized void replace(RoadGraphSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        RoadGraphSnapshot current = snapshots.get(snapshot.dimension());
        if (current != null && snapshot.revision() < current.revision()) {
            return;
        }
        snapshots.put(snapshot.dimension(), snapshot);
    }

    public synchronized Optional<RoadGraphSnapshot> snapshot(ResourceKey<Level> dimension) {
        return Optional.ofNullable(snapshots.get(Objects.requireNonNull(dimension, "dimension")));
    }

        public synchronized DeltaResult apply(RoadGraphDelta delta) {
        Objects.requireNonNull(delta, "delta");
        RoadGraphSnapshot current = snapshots.get(delta.dimension());
        if (current == null) {
            return DeltaResult.MISSING_BASE;
        }
        if (delta.targetRevision() <= current.revision()) {
            return DeltaResult.STALE;
        }
        if (delta.baseRevision() != current.revision()) {
            return DeltaResult.REVISION_GAP;
        }

        try {
            snapshots.put(delta.dimension(), delta.applyTo(current));
            return DeltaResult.APPLIED;
        } catch (IllegalArgumentException exception) {
            return DeltaResult.INVALID;
        }
    }

    public synchronized long knownRevision(ResourceKey<Level> dimension) {
        return snapshot(dimension)
                .map(RoadGraphSnapshot::revision)
                .orElse(-1L);
    }

    public synchronized void clear() {
        snapshots.clear();
    }
}

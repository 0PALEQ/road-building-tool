package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;

import java.util.Objects;
import java.util.Optional;

public final class RoadGraphMaterializationConsistency {
    private RoadGraphMaterializationConsistency() {
    }

    public static Optional<String> validate(
            RoadGraph graph,
            PlacementOwnershipLedger ledger
    ) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(ledger, "ledger");
        if (!graph.dimension().equals(ledger.dimension())) {
            return Optional.of("graph and placement ownership dimensions differ");
        }

        RoadGraphSnapshot snapshot = graph.snapshot();
        for (PlacementOperation operation : ledger.operations()) {
            Optional<RoadMaterializationOwner> optionalOwner = operation.materializationOwner();
            if (optionalOwner.isEmpty()) {
                continue;
            }
            RoadMaterializationOwner owner = optionalOwner.orElseThrow();
            boolean ownerExists = switch (owner.type()) {
                case SEGMENT -> snapshot.segments().containsKey(owner.segmentId());
                case NODE -> snapshot.nodes().containsKey(owner.nodeId());
            };
            if (!ownerExists) {
                return Optional.of(
                        "operation " + operation.id() + " references missing "
                                + owner.type().serializedName() + " " + owner.ownerId()
                );
            }
        }

        for (RoadSegment segment : snapshot.segments().values()) {
            RoadMaterializationOwner owner = RoadMaterializationOwner.segment(segment.id());
            if (ledger.operation(owner).isEmpty()) {
                return Optional.of(
                        "segment " + segment.id() + " has no placement materialization"
                );
            }
        }
        return Optional.empty();
    }
}

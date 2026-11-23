package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RoadMaterializationResolver {
    private final ConstructionLimits limits;

    public RoadMaterializationResolver(ConstructionLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public Optional<PlacementOperation> resolve(
            RoadSegment segment,
            ResourceKey<Level> dimension,
            PlacementOwnershipLedger ledger
    ) {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(ledger, "ledger");
        RoadMaterializationOwner owner = RoadMaterializationOwner.segment(
                segment.id()
        );
        Optional<PlacementOperation> existing = ledger.operation(owner);
        if (existing.isPresent()) {
            return existing;
        }

        BlockPlanResult result = new StraightRoadSegmentMaterializer().plan(
                dimension,
                segment,
                limits
        );
        if (!(result instanceof BlockPlanResult.Success success)) {
            return Optional.empty();
        }
        Set<BlockPos> expected = new HashSet<>(success.plan().positions());
        Optional<Match> best = ledger.operations().stream()
                .filter(operation -> operation.materializationOwner().isEmpty())
                .map(operation -> new Match(
                        operation,
                        overlap(operation, expected)
                ))
                .filter(match -> match.overlap() > 0)
                .sorted(Comparator
                        .comparingInt(Match::overlap)
                        .reversed()
                        .thenComparing(match -> match.operation().id().toString()))
                .findFirst();
        if (best.isEmpty()) {
            return Optional.empty();
        }
        Match match = best.orElseThrow();
        int minimumOverlap = Math.max(
                1,
                Math.min(expected.size(), match.operation().positions().size()) / 4
        );
        if (match.overlap() < minimumOverlap) {
            return Optional.empty();
        }
        return Optional.of(ledger.assignMaterializationOwner(
                match.operation().id(),
                owner
        ));
    }

    private static int overlap(
            PlacementOperation operation,
            Set<BlockPos> expected
    ) {
        int count = 0;
        for (BlockPos position : operation.positions()) {
            if (expected.contains(position)) {
                count++;
            }
        }
        return count;
    }

    private record Match(PlacementOperation operation, int overlap) {
    }
}

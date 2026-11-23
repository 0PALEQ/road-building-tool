package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlacementOwnershipRepartitionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void overlappingChildrenRemainVisibleUntilTheLastChildIsRemoved() {
        PlacementOperationId sourceId = id(1);
        PlacementOperationId secondId = id(2);
        BlockPos first = new BlockPos(0, 64, 0);
        BlockPos shared = new BlockPos(1, 64, 0);
        BlockPos second = new BlockPos(2, 64, 0);
        BlockChangeLayer sourceLayer = new BlockChangeLayer(
                sourceId,
                Blocks.DIRT.defaultBlockState(),
                Blocks.STONE.defaultBlockState()
        );
        PlacementOwnershipLedger ledger = PlacementOwnershipLedger.restore(
                Level.OVERWORLD,
                Map.of(
                        first, PositionChangeStack.of(sourceLayer),
                        shared, PositionChangeStack.of(sourceLayer),
                        second, PositionChangeStack.of(sourceLayer)
                ),
                Map.of(
                        sourceId,
                        RoadMaterializationOwner.segment(segmentId(10))
                ),
                () -> { }
        );

        ledger.repartitionMaterializations(List.of(
                new OperationMaterializationRepartition(
                        sourceId,
                        List.of(
                                new MaterializationPartition(
                                        sourceId,
                                        RoadMaterializationOwner.segment(segmentId(10)),
                                        List.of(first, shared)
                                ),
                                new MaterializationPartition(
                                        secondId,
                                        RoadMaterializationOwner.segment(segmentId(11)),
                                        List.of(shared, second)
                                )
                        )
                )
        ));

        assertEquals(2, ledger.operations().size());
        assertEquals(2, ledger.stack(shared).orElseThrow().layers().size());
        ledger.removeOperation(sourceId, ledger.revision());
        BlockChangeLayer remaining = ledger.stack(shared).orElseThrow().top();
        assertEquals(Blocks.DIRT.defaultBlockState(), remaining.beforeState());
        assertEquals(Blocks.STONE.defaultBlockState(), remaining.afterState());
        ledger.removeOperation(secondId, ledger.revision());
        assertTrue(ledger.stack(shared).isEmpty());
    }

    @Test
    void junctionRebuildPublishesReplacementAndDropsCleanupAtomically() {
        PlacementOperationId segmentOperation = id(20);
        PlacementOperationId overlayOperation = id(21);
        BlockPos retained = new BlockPos(0, 64, 0);
        BlockPos cleanup = new BlockPos(1, 64, 0);
        BlockChangeLayer segmentLayer = new BlockChangeLayer(
                segmentOperation,
                Blocks.DIRT.defaultBlockState(),
                Blocks.STONE.defaultBlockState()
        );
        PlacementOwnershipLedger ledger = PlacementOwnershipLedger.restore(
                Level.OVERWORLD,
                Map.of(
                        retained,
                        PositionChangeStack.of(segmentLayer).push(
                                new BlockChangeLayer(
                                        overlayOperation,
                                        Blocks.STONE.defaultBlockState(),
                                        Blocks.POLISHED_ANDESITE.defaultBlockState()
                                )
                        ),
                        cleanup,
                        PositionChangeStack.of(segmentLayer).push(
                                new BlockChangeLayer(
                                        overlayOperation,
                                        Blocks.STONE.defaultBlockState(),
                                        Blocks.SAND.defaultBlockState()
                                )
                        )
                ),
                Map.of(
                        segmentOperation,
                        RoadMaterializationOwner.segment(segmentId(20))
                ),
                () -> { }
        );
        RoadMaterializationOwner retainedOwner = RoadMaterializationOwner.node(
                new RoadNodeId(new UUID(0, 30))
        );

        ledger.publishMaterializationRebuild(
                List.of(segmentOperation),
                overlayOperation,
                List.of(new MaterializationPartition(
                        overlayOperation,
                        retainedOwner,
                        List.of(retained)
                ))
        );

        assertTrue(ledger.operation(segmentOperation).isEmpty());
        assertEquals(
                overlayOperation,
                ledger.operation(retainedOwner).orElseThrow().id()
        );
        assertTrue(ledger.stack(cleanup).isEmpty());
        BlockChangeLayer retainedLayer = ledger.stack(retained)
                .orElseThrow()
                .top();
        assertEquals(Blocks.DIRT.defaultBlockState(), retainedLayer.beforeState());
        assertEquals(
                Blocks.POLISHED_ANDESITE.defaultBlockState(),
                retainedLayer.afterState()
        );
    }

    @Test
    void materializationRemovalPublishesWithoutRetainedPartitions() {
        PlacementOperationId segmentOperation = id(30);
        PlacementOperationId overlayOperation = id(31);
        BlockPos position = new BlockPos(0, 64, 0);
        PlacementOwnershipLedger ledger = PlacementOwnershipLedger.restore(
                Level.OVERWORLD,
                Map.of(
                        position,
                        PositionChangeStack.of(new BlockChangeLayer(
                                segmentOperation,
                                Blocks.DIRT.defaultBlockState(),
                                Blocks.STONE.defaultBlockState()
                        )).push(new BlockChangeLayer(
                                overlayOperation,
                                Blocks.STONE.defaultBlockState(),
                                Blocks.DIRT.defaultBlockState()
                        ))
                ),
                Map.of(
                        segmentOperation,
                        RoadMaterializationOwner.segment(segmentId(30))
                ),
                () -> { }
        );

        ledger.publishMaterializationRebuild(
                List.of(segmentOperation),
                overlayOperation,
                List.of()
        );

        assertTrue(ledger.operations().isEmpty());
        assertTrue(ledger.stack(position).isEmpty());
    }

    @Test
    void rejectedJunctionRebuildLeavesLedgerUntouched() {
        PlacementOperationId segmentOperation = id(40);
        PlacementOperationId overlayOperation = id(41);
        BlockPos position = new BlockPos(0, 64, 0);
        BlockChangeLayer segmentLayer = new BlockChangeLayer(
                segmentOperation,
                Blocks.DIRT.defaultBlockState(),
                Blocks.STONE.defaultBlockState()
        );
        PlacementOwnershipLedger ledger = PlacementOwnershipLedger.restore(
                Level.OVERWORLD,
                Map.of(
                        position,
                        PositionChangeStack.of(segmentLayer).push(
                                new BlockChangeLayer(
                                        overlayOperation,
                                        Blocks.STONE.defaultBlockState(),
                                        Blocks.DIRT.defaultBlockState()
                                )
                        )
                ),
                Map.of(
                        segmentOperation,
                        RoadMaterializationOwner.segment(segmentId(40))
                ),
                () -> { }
        );
        Map<BlockPos, PositionChangeStack> before = ledger.positionStacks();

        assertThrows(
                IllegalArgumentException.class,
                () -> ledger.publishMaterializationRebuild(
                        List.of(segmentOperation),
                        overlayOperation,
                        List.of(new MaterializationPartition(
                                id(42),
                                RoadMaterializationOwner.node(
                                        new RoadNodeId(new UUID(0, 42))
                                ),
                                List.of(position.east())
                        ))
                )
        );

        assertEquals(before, ledger.positionStacks());
        assertTrue(ledger.operation(segmentOperation).isPresent());
        assertTrue(ledger.operation(overlayOperation).isPresent());
    }

    private static PlacementOperationId id(long id) {
        return new PlacementOperationId(new UUID(0, id));
    }

    private static RoadSegmentId segmentId(long id) {
        return new RoadSegmentId(new UUID(0, id));
    }
}

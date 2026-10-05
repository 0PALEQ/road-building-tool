package com.cookiecraftmods.roadbuilder.persistence;

import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlan;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanCommitter;
import com.cookiecraftmods.roadbuilder.placement.PlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperationId;
import com.cookiecraftmods.roadbuilder.placement.PlannedBlock;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadBuilderPlacementSavedDataTest {
    private static final BlockPos WEST = new BlockPos(-4, 64, 2);
    private static final BlockPos EAST = new BlockPos(7, 64, 2);
    private static final PlacementOperationId OPERATION_A = operationId(1);
    private static final PlacementOperationId OPERATION_B = operationId(2);

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void roundTripsSortedStacksAndRebuildsOperationIndex() {
        CompoundTag raw = root(
                position(EAST, layer(
                        OPERATION_A,
                        Blocks.DIRT.defaultBlockState(),
                        Blocks.STONE.defaultBlockState()
                ), layer(
                        OPERATION_B,
                        Blocks.STONE.defaultBlockState(),
                        Blocks.COBBLESTONE.defaultBlockState()
                )),
                position(WEST, layer(
                        OPERATION_A,
                        Blocks.GRASS_BLOCK.defaultBlockState(),
                        Blocks.STONE.defaultBlockState()
                ))
        );

        RoadBuilderPlacementSavedData data = RoadBuilderPlacementSavedData.load(Level.OVERWORLD, raw);

        assertTrue(data.isCompatible());
        assertFalse(data.isDirty());
        assertEquals(2, data.ledger().positionStacks().size());
        assertEquals(List.of(WEST, EAST), data.ledger().operation(OPERATION_A).orElseThrow().positions());
        assertEquals(List.of(EAST), data.ledger().operation(OPERATION_B).orElseThrow().positions());
        assertEquals(OPERATION_B, data.ledger().stack(EAST).orElseThrow().top().operationId());

        CompoundTag saved = data.save(new CompoundTag());
        ListTag savedOwnership = saved.getList("ownership", 10);
        assertEquals(WEST, BlockPos.of(savedOwnership.getCompound(0).getLong("position")));
        assertEquals(EAST, BlockPos.of(savedOwnership.getCompound(1).getLong("position")));

        RoadBuilderPlacementSavedData reloaded = RoadBuilderPlacementSavedData.load(
                Level.OVERWORLD,
                saved
        );
        assertTrue(reloaded.isCompatible());
        assertEquals(
                data.ledger().stack(WEST).orElseThrow().layers(),
                reloaded.ledger().stack(WEST).orElseThrow().layers()
        );
        assertEquals(
                data.ledger().stack(EAST).orElseThrow().layers(),
                reloaded.ledger().stack(EAST).orElseThrow().layers()
        );
        assertEquals(saved, reloaded.save(new CompoundTag()));
    }

    @Test
    void ledgerMutationMarksCompatibleDataDirty() {
        RoadBuilderPlacementSavedData data = RoadBuilderPlacementSavedData.create(Level.OVERWORLD);
        MapWorld world = new MapWorld(Level.OVERWORLD);
        world.states.put(WEST, Blocks.DIRT.defaultBlockState());
        BlockPlacementPlan plan = BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                new PlannedBlock(WEST, Blocks.STONE.defaultBlockState())
        ));

        assertFalse(data.isDirty());
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                new BlockPlanCommitter().commit(OPERATION_A, plan, world, data.ledger())
        );
        assertTrue(data.isDirty());
    }

    @Test
    void roundTripsTypedGraphMaterializationOwnersAndMigratesLegacyV1() {
        RoadBuilderPlacementSavedData data = RoadBuilderPlacementSavedData.create(Level.OVERWORLD);
        MapWorld world = new MapWorld(Level.OVERWORLD);
        world.states.put(WEST, Blocks.DIRT.defaultBlockState());
        RoadMaterializationOwner owner = RoadMaterializationOwner.segment(
                new RoadSegmentId(new UUID(0, 91))
        );
        BlockPlacementPlan plan = BlockPlacementPlan.create(Level.OVERWORLD, List.of(
                new PlannedBlock(WEST, Blocks.STONE.defaultBlockState())
        ));
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                new BlockPlanCommitter().commit(OPERATION_A, owner, plan, world, data.ledger())
        );

        CompoundTag saved = data.save(new CompoundTag());
        assertEquals(2, saved.getInt("schema_version"));
        assertEquals(1, saved.getList("materializations", 10).size());
        RoadBuilderPlacementSavedData reloaded = RoadBuilderPlacementSavedData.load(
                Level.OVERWORLD,
                saved
        );
        assertEquals(
                owner,
                reloaded.ledger().operation(OPERATION_A).orElseThrow()
                        .materializationOwner().orElseThrow()
        );
        assertEquals(OPERATION_A, reloaded.ledger().operation(owner).orElseThrow().id());

        CompoundTag legacy = root(position(WEST, layerA()));
        legacy.putInt("schema_version", 1);
        legacy.remove("materializations");
        RoadBuilderPlacementSavedData migrated = RoadBuilderPlacementSavedData.load(
                Level.OVERWORLD,
                legacy
        );
        assertTrue(migrated.isCompatible());
        assertTrue(
                migrated.ledger().operation(OPERATION_A).orElseThrow()
                        .materializationOwner().isEmpty()
        );
        CompoundTag migratedSave = migrated.save(new CompoundTag());
        assertEquals(RoadBuilderPlacementSavedData.CURRENT_SCHEMA_VERSION,
                migratedSave.getInt("schema_version"));
        assertTrue(migratedSave.getList("materializations", 10).isEmpty());
    }

    @Test
    void newerSchemaBecomesReadOnlyAndPreservesItsRawPayload() {
        CompoundTag newer = root();
        newer.putInt("schema_version", RoadBuilderPlacementSavedData.CURRENT_SCHEMA_VERSION + 1);
        CompoundTag futureField = new CompoundTag();
        futureField.putString("future", "payload");
        newer.put("future_data", futureField);
        CompoundTag retained = newer.copy();

        RoadBuilderPlacementSavedData data = assertDoesNotThrow(
                () -> RoadBuilderPlacementSavedData.load(Level.OVERWORLD, newer)
        );
        newer.remove("future_data");

        assertFalse(data.isCompatible());
        assertTrue(data.incompatibilityReason().orElseThrow().contains("schema")
                || data.incompatibilityReason().orElseThrow().contains("exactly"));
        assertThrows(IllegalStateException.class, data::ledger);
        assertEquals(retained, data.incompatibleRawTag().orElseThrow());
        assertEquals(retained, data.save(new CompoundTag()));
    }

    @Test
    void malformedTypesAndExactKeyCountsFailClosed() {
        CompoundTag wrongVersionType = root();
        wrongVersionType.putByte("schema_version", (byte) 1);
        assertIncompatible(wrongVersionType, "schema_version must be an int");

        CompoundTag wrongOwnershipType = new CompoundTag();
        wrongOwnershipType.putInt("schema_version", 1);
        wrongOwnershipType.put("ownership", new CompoundTag());
        assertIncompatible(wrongOwnershipType, "ownership must be a list");

        CompoundTag extraRootKey = root();
        extraRootKey.putString("unexpected", "value");
        assertIncompatible(extraRootKey, "must contain exactly");

        CompoundTag wrongEntryElementType = root();
        ListTag stringEntries = new ListTag();
        stringEntries.add(StringTag.valueOf("not a position"));
        wrongEntryElementType.put("ownership", stringEntries);
        assertIncompatible(wrongEntryElementType, "entries must be compounds");

        CompoundTag wrongPositionType = root(position(WEST, layerA()));
        wrongPositionType.getList("ownership", 10).getCompound(0).putInt("position", 4);
        assertIncompatible(wrongPositionType, "position must be a long");

        CompoundTag extraLayerKey = root(position(WEST, layerA()));
        firstLayer(extraLayerKey).putString("unexpected", "value");
        assertIncompatible(extraLayerKey, "must contain exactly");
    }

    @Test
    void invalidUuidAndBlockStateFailClosed() {
        CompoundTag invalidUuid = root(position(WEST, layerA()));
        firstLayer(invalidUuid).putIntArray("operation", new int[]{1, 2, 3});
        assertIncompatible(invalidUuid, "four-int UUID");

        CompoundTag wrongStateType = root(position(WEST, layerA()));
        firstLayer(wrongStateType).putString("before_state", "minecraft:dirt");
        assertIncompatible(wrongStateType, "before_state must be a compound");

        CompoundTag unknownState = root(position(WEST, layerA()));
        CompoundTag missingBlock = new CompoundTag();
        missingBlock.putString("Name", "roadbuilder:not_registered");
        firstLayer(unknownState).put("after_state", missingBlock);
        assertIncompatible(unknownState, "unknown block state Name");
    }

    @Test
    void duplicatePositionAndOperationIdsFailClosed() {
        assertIncompatible(
                root(position(WEST, layerA()), position(WEST, layer(
                        OPERATION_B,
                        Blocks.STONE.defaultBlockState(),
                        Blocks.COBBLESTONE.defaultBlockState()
                ))),
                "duplicate ownership position"
        );
        assertIncompatible(
                root(position(WEST, layerA(), layerA())),
                "operation appears more than once"
        );
    }

    @Test
    void crossStackOperationOrderingCycleFailsClosed() {
        CompoundTag cycle = root(
                position(WEST, layerA(), layer(
                        OPERATION_B,
                        Blocks.STONE.defaultBlockState(),
                        Blocks.COBBLESTONE.defaultBlockState()
                )),
                position(EAST, layer(
                        OPERATION_B,
                        Blocks.DIRT.defaultBlockState(),
                        Blocks.COBBLESTONE.defaultBlockState()
                ), layer(
                        OPERATION_A,
                        Blocks.COBBLESTONE.defaultBlockState(),
                        Blocks.STONE.defaultBlockState()
                ))
        );

        assertIncompatible(cycle, "cross-stack cycle");
    }

    @Test
    void positionLayerAndDepthBoundsAreEnforced() {
        CompoundTag twoPositions = root(position(WEST, layerA()), position(EAST, layerA()));
        assertIncompatible(
                twoPositions,
                new RoadBuilderPlacementSavedData.ReadLimits(1, 10, 10),
                "position count exceeds"
        );

        CompoundTag twoLayers = root(position(WEST, layerA(), layer(
                OPERATION_B,
                Blocks.STONE.defaultBlockState(),
                Blocks.COBBLESTONE.defaultBlockState()
        )));
        assertIncompatible(
                twoLayers,
                new RoadBuilderPlacementSavedData.ReadLimits(10, 1, 10),
                "total layer count exceeds"
        );
        assertIncompatible(
                twoLayers,
                new RoadBuilderPlacementSavedData.ReadLimits(10, 10, 1),
                "stack depth exceeds"
        );
    }

    private static CompoundTag root(CompoundTag... positions) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", RoadBuilderPlacementSavedData.CURRENT_SCHEMA_VERSION);
        ListTag ownership = new ListTag();
        for (CompoundTag position : positions) {
            ownership.add(position);
        }
        root.put("ownership", ownership);
        root.put("materializations", new ListTag());
        return root;
    }

    private static CompoundTag position(BlockPos position, CompoundTag... layers) {
        CompoundTag positionTag = new CompoundTag();
        positionTag.putLong("position", position.asLong());
        ListTag layerList = new ListTag();
        for (CompoundTag layer : layers) {
            layerList.add(layer);
        }
        positionTag.put("layers", layerList);
        return positionTag;
    }

    private static CompoundTag layerA() {
        return layer(
                OPERATION_A,
                Blocks.DIRT.defaultBlockState(),
                Blocks.STONE.defaultBlockState()
        );
    }

    private static CompoundTag layer(
            PlacementOperationId operationId,
            BlockState beforeState,
            BlockState afterState
    ) {
        CompoundTag layer = new CompoundTag();
        layer.putUUID("operation", operationId.value());
        layer.put("before_state", StrictBlockStateNbtCodec.write(beforeState));
        layer.put("after_state", StrictBlockStateNbtCodec.write(afterState));
        return layer;
    }

    private static CompoundTag firstLayer(CompoundTag root) {
        return root.getList("ownership", 10)
                .getCompound(0)
                .getList("layers", 10)
                .getCompound(0);
    }

    private static PlacementOperationId operationId(long lowBits) {
        return new PlacementOperationId(new UUID(0, lowBits));
    }

    private static void assertIncompatible(CompoundTag raw, String expectedReasonPart) {
        assertIncompatible(raw, null, expectedReasonPart);
    }

    private static void assertIncompatible(
            CompoundTag raw,
            RoadBuilderPlacementSavedData.ReadLimits limits,
            String expectedReasonPart
    ) {
        CompoundTag retained = raw.copy();
        RoadBuilderPlacementSavedData data = assertDoesNotThrow(() -> limits == null
                ? RoadBuilderPlacementSavedData.load(Level.OVERWORLD, raw)
                : RoadBuilderPlacementSavedData.load(Level.OVERWORLD, raw, limits));

        assertFalse(data.isCompatible());
        assertTrue(
                data.incompatibilityReason().orElseThrow().contains(expectedReasonPart),
                data.incompatibilityReason()::orElseThrow
        );
        assertThrows(IllegalStateException.class, data::ledger);
        assertEquals(retained, data.save(new CompoundTag()));
    }

    private static final class MapWorld implements PlacementBlockWorld {
        private final ResourceKey<Level> dimension;
        private final Map<BlockPos, BlockState> states = new HashMap<>();

        private MapWorld(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        @Override
        public ResourceKey<Level> dimension() {
            return dimension;
        }

        @Override
        public BlockState blockState(BlockPos position) {
            return states.getOrDefault(position, Blocks.AIR.defaultBlockState());
        }

        @Override
        public boolean hasBlockEntity(BlockPos position) {
            return false;
        }

        @Override
        public boolean setBlockState(BlockPos position, BlockState state) {
            states.put(position.immutable(), state);
            return true;
        }
    }
}

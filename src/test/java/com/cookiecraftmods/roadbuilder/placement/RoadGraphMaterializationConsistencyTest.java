package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadAddition;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadGraphMaterializationConsistencyTest {
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            1,
            1.0,
            new ResourceLocation("minecraft", "stone"),
            List.of(new LaneComponent(
                    LaneType.VEHICLE,
                    1.0,
                    0.0,
                    LaneTravelDirection.FORWARD
            ))
    );

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void detectsCrossStoreMismatchAndAcceptsTheOwnedMaterialization() {
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        PreparedRoadAddition addition = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(4.5, 64.0, 0.5)
                ),
                new ResourceLocation("roadbuilder", "consistency"),
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(addition);
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(Level.OVERWORLD);

        assertTrue(
                RoadGraphMaterializationConsistency.validate(graph, ledger)
                        .orElseThrow().contains("has no placement materialization")
        );

        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadSegmentMaterializer().plan(
                        Level.OVERWORLD,
                        addition.segment(),
                        ConstructionLimits.defaults()
                )
        ).plan();
        MapWorld world = new MapWorld();
        PlacementOperationId operationId = new PlacementOperationId(new UUID(0, 1));
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                new BlockPlanCommitter().commit(
                        operationId,
                        RoadMaterializationOwner.segment(addition.segment().id()),
                        plan,
                        world,
                        ledger
                )
        );
        assertTrue(RoadGraphMaterializationConsistency.validate(graph, ledger).isEmpty());

        assertInstanceOf(
                PlacementRemovalResult.Success.class,
                new PlacementRemovalService().remove(
                        operationId,
                        RemovalMode.SAFE,
                        world,
                        ledger
                )
        );
        assertTrue(
                RoadGraphMaterializationConsistency.validate(graph, ledger)
                        .orElseThrow().contains("has no placement materialization")
        );
    }

    @Test
    void recoversLegacyOwnerlessMaterializationBySpatialOverlap() {
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        PreparedRoadAddition addition = graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(4.5, 64.0, 0.5)
                ),
                new ResourceLocation("roadbuilder", "legacy"),
                PRESET,
                TrafficHandedness.RHT
        );
        graph.applyAddition(addition);
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(
                Level.OVERWORLD
        );
        BlockPlacementPlan plan = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadSegmentMaterializer().plan(
                        Level.OVERWORLD,
                        addition.segment(),
                        ConstructionLimits.defaults()
                )
        ).plan();
        assertInstanceOf(
                PlacementCommitResult.Success.class,
                new BlockPlanCommitter().commit(
                        new PlacementOperationId(new UUID(0, 2)),
                        plan,
                        new MapWorld(),
                        ledger
                )
        );

        assertTrue(new RoadMaterializationResolver(ConstructionLimits.defaults())
                .resolve(addition.segment(), Level.OVERWORLD, ledger)
                .isPresent());
        assertTrue(RoadGraphMaterializationConsistency.validate(graph, ledger)
                .isEmpty());
    }

    private static final class MapWorld implements PlacementBlockWorld {
        private final Map<BlockPos, BlockState> states = new HashMap<>();

        @Override
        public ResourceKey<Level> dimension() {
            return Level.OVERWORLD;
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

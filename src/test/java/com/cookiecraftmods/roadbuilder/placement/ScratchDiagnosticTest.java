package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.JunctionConnectorPlanner;
import com.cookiecraftmods.roadbuilder.network.JunctionDefinition;
import com.cookiecraftmods.roadbuilder.network.LaneConnector;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorId;
import com.cookiecraftmods.roadbuilder.network.LaneConnectorMovement;
import com.cookiecraftmods.roadbuilder.network.LaneMovementType;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadNetworkMutation;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ScratchDiagnosticTest {
    private static final ResourceLocation PRESET_ID =
            new ResourceLocation("roadbuilder", "intersection_test");

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void diagnostic() {
        ResourceLocation gray = new ResourceLocation(
                "minecraft",
                "gray_concrete"
        );
        ResourceLocation stone = new ResourceLocation(
                "minecraft",
                "smooth_stone"
        );
        RoadPreset styledPreset = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                7.0,
                gray,
                List.of(new LaneComponent(
                        LaneType.VEHICLE,
                        3.0,
                        0.0,
                        LaneTravelDirection.FORWARD
                )),
                List.of(
                        new RoadSurfaceBand(
                                RoadSurfaceRole.SIDEWALK,
                                1,
                                stone
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                2,
                                gray
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.MARKING,
                                1,
                                new ResourceLocation(
                                        "minecraft",
                                        "yellow_concrete"
                                )
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.PAVEMENT,
                                2,
                                gray
                        ),
                        new RoadSurfaceBand(
                                RoadSurfaceRole.SIDEWALK,
                                1,
                                stone
                        )
                )
        );
        RoadGraph graph = new RoadGraph(Level.OVERWORLD);
        graph.applyAddition(graph.prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(10.5, 64.0, 0.5)
                ),
                PRESET_ID,
                styledPreset,
                TrafficHandedness.RHT
        ));
        PreparedRoadNetworkMutation crossing = graph.prepareStraightRoadNetwork(
                new StraightGeometry(
                        new RoadPoint(5.5, 64.0, -5.5),
                        new RoadPoint(5.5, 64.0, 5.5)
                ),
                PRESET_ID,
                styledPreset,
                TrafficHandedness.RHT
        );
        RoadNode node = crossing.addedNodes().stream()
                .filter(candidate -> crossing.allUpsertedSegments().stream()
                        .filter(segment -> segment.startNodeId().equals(candidate.id())
                                || segment.endNodeId().equals(candidate.id()))
                        .count() == 4)
                .findFirst()
                .orElseThrow();
        List<RoadSegment> incident = crossing.allUpsertedSegments().stream()
                .filter(segment -> segment.startNodeId().equals(node.id())
                        || segment.endNodeId().equals(node.id()))
                .toList();
        var automatic = new JunctionConnectorPlanner().plan(
                node.id(), incident, List.of(),
                () -> new LaneConnectorId(UUID.randomUUID()));
        System.out.println("=== automatic connectors ===");
        for (LaneConnector connector : automatic) {
            System.out.println(connector.movement().type() + " " + connector.fromLaneId() + " -> " + connector.toLaneId());
        }
        LaneConnector turn = automatic.stream()
                .filter(connector -> connector.movement().type() == LaneMovementType.LEFT
                        || connector.movement().type() == LaneMovementType.RIGHT)
                .findFirst()
                .orElseThrow();
        System.out.println("=== chosen turn: " + turn.movement().type() + " " + turn.fromLaneId() + " -> " + turn.toLaneId());
        BlockPlacementPlan baseline = assertInstanceOf(
                BlockPlanResult.Success.class,
                new StraightRoadIntersectionMaterializer().plan(
                        Level.OVERWORLD, node, incident,
                        new ResourceLocation("minecraft", "gray_concrete"),
                        new ResourceLocation("minecraft", "smooth_stone"),
                        100, 10_000,
                        JunctionDefinition.automatic(node.id()),
                        List.of()
                )
        ).plan();
        var baselineBlocks = baseline.blocks().stream().collect(java.util.stream.Collectors.toMap(
                PlannedBlock::position, PlannedBlock::targetState));
        for (LaneConnector candidate : automatic) {
            LaneConnector disabled = new LaneConnector(
                    candidate.id(), candidate.fromLaneId(), candidate.toLaneId(),
                    new LaneConnectorMovement(candidate.movement().type(), false, true, 0));
            BlockPlacementPlan disabledPlan = assertInstanceOf(
                    BlockPlanResult.Success.class,
                    new StraightRoadIntersectionMaterializer().plan(
                            Level.OVERWORLD, node, incident,
                            new ResourceLocation("minecraft", "gray_concrete"),
                            new ResourceLocation("minecraft", "smooth_stone"),
                            100, 10_000,
                            JunctionDefinition.automatic(node.id()),
                            List.of(disabled)
                    )
            ).plan();
            var disabledBlocks = disabledPlan.blocks().stream().collect(java.util.stream.Collectors.toMap(
                    PlannedBlock::position, PlannedBlock::targetState));
            int changed = 0;
            for (var entry : baselineBlocks.entrySet()) {
                BlockState other = disabledBlocks.get(entry.getKey());
                if (other != null && !other.equals(entry.getValue())) {
                    changed++;
                }
            }
            System.out.println("turn " + candidate.movement().type()
                    + " " + candidate.fromLaneId() + " -> " + candidate.toLaneId()
                    + " changed cells: " + changed);
        }
    }
}

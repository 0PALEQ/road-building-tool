package com.cookiecraftmods.roadbuilder.gametest;

import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadAddition;
import com.cookiecraftmods.roadbuilder.network.RoadGraph;
import com.cookiecraftmods.roadbuilder.network.RoadGraphDelta;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.networking.PlacementResultCode;
import com.cookiecraftmods.roadbuilder.networking.PlacementResultS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.PlacementRemovalEndpoint;
import com.cookiecraftmods.roadbuilder.networking.PlayerRequestSessionState;
import com.cookiecraftmods.roadbuilder.networking.RemovalIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.RemovalRequestAction;
import com.cookiecraftmods.roadbuilder.networking.RemovalResultCode;
import com.cookiecraftmods.roadbuilder.networking.RemovalResultS2CPacket;
import com.cookiecraftmods.roadbuilder.networking.SegmentRemovalIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.StraightRoadIntentC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.StraightRoadPlacementEndpoint;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderPlacementSavedData;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderConstructionJobSavedData;
import com.cookiecraftmods.roadbuilder.persistence.RoadBuilderNetworkSavedData;
import com.cookiecraftmods.roadbuilder.placement.BlockPlacementPlan;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanCommitter;
import com.cookiecraftmods.roadbuilder.placement.BlockPlanResult;
import com.cookiecraftmods.roadbuilder.placement.ConstructionAuthorizer;
import com.cookiecraftmods.roadbuilder.placement.ConstructionLimits;
import com.cookiecraftmods.roadbuilder.placement.ConstructionValidationCode;
import com.cookiecraftmods.roadbuilder.placement.ConstructionValidationResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitFailureCode;
import com.cookiecraftmods.roadbuilder.placement.PlacementCommitResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementOperationId;
import com.cookiecraftmods.roadbuilder.placement.PlacementOwnershipLedger;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalFailureCode;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalResult;
import com.cookiecraftmods.roadbuilder.placement.PlacementRemovalService;
import com.cookiecraftmods.roadbuilder.placement.PlannedBlock;
import com.cookiecraftmods.roadbuilder.placement.RemovalMode;
import com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner;
import com.cookiecraftmods.roadbuilder.placement.RoadGraphMaterializationConsistency;
import com.cookiecraftmods.roadbuilder.placement.ServerLevelPlacementBlockWorld;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadIntent;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadPlanner;
import com.cookiecraftmods.roadbuilder.placement.StraightRoadPlanningService;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetResources;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RoadBuilderPlacementGameTests implements FabricGameTest {
    private static final int UPDATE_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300, batch = "slopingJunctionRebuild")
    public void slopingIntersectionRebuildsExistingRoadGeometryAndBlocks(GameTestHelper helper) {
        var level=helper.getLevel();
        var origin=helper.absolutePos(new BlockPos(384,0,384));
        var material=new ResourceLocation("minecraft","gray_concrete");
        var preset=new com.cookiecraftmods.roadbuilder.preset.RoadPreset(
                com.cookiecraftmods.roadbuilder.preset.RoadPreset.CURRENT_SCHEMA_VERSION,1,11,material,List.of(
                new com.cookiecraftmods.roadbuilder.preset.LaneComponent(com.cookiecraftmods.roadbuilder.preset.LaneType.VEHICLE,
                        3,0,com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection.FORWARD)));
        for(int distance=-85;distance<=85;distance++) for(int lateral=-8;lateral<=8;lateral++) {
            level.setBlock(new BlockPos(origin.getX()+distance,63,origin.getZ()+lateral),Blocks.STONE.defaultBlockState(),UPDATE_FLAGS);
            level.setBlock(new BlockPos(origin.getX()+lateral,63,origin.getZ()+distance),Blocks.STONE.defaultBlockState(),UPDATE_FLAGS);
        }
        var player=testPlayer(level,true); player.setPos(origin.getX(),75,origin.getZ());
        var graph=new RoadGraph(level.dimension());
        var ledger=new PlacementOwnershipLedger(level.dimension());
        var endpoint=new StraightRoadPlacementEndpoint(new StraightRoadPlanningService(id -> java.util.Optional.of(preset),
                new StraightRoadPlanner()),ConstructionAuthorizer.creativeOnly(),ConstructionLimits.defaults(),new BlockPlanCommitter());
        var centre=new RoadPoint(origin.getX()+0.5,72,origin.getZ()+0.5);
        var host = endpoint.handle(StraightRoadIntentC2SPacket.current(
                UUID.randomUUID(), level.dimension().location(), material, 1,
                centre.x() - 80, 64, centre.z(), centre.x() + 80, 80, centre.z()), player, ledger, graph);
        helper.assertTrue(host.resultCode() == PlacementResultCode.ACCEPTED, "sloping road must build: " + host.resultCode());
        var branchEndpoint = new com.cookiecraftmods.roadbuilder.networking.RoadPathPlacementEndpoint(
                new com.cookiecraftmods.roadbuilder.placement.RoadPathPlanningService(
                        id -> java.util.Optional.of(preset), new com.cookiecraftmods.roadbuilder.placement.RoadPathPlanner()),
                ConstructionAuthorizer.creativeOnly(), ConstructionLimits.defaults(), new BlockPlanCommitter(),
                com.cookiecraftmods.roadbuilder.preset.TrafficHandedness.RHT);
        var branch = branchEndpoint.handle(com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket.current(
                UUID.randomUUID(), level.dimension().location(), material, 1, graph.snapshot().revision(),
                com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket.PathType.STRAIGHT,
                List.of(new RoadPoint(centre.x(), 72, centre.z() - 80),
                        new RoadPoint(centre.x(), 72, centre.z() + 80))).withSunkenRoad(true), player, ledger, graph, null);
        helper.assertTrue(branch.resultCode() == PlacementResultCode.ACCEPTED,
                "sunken road must connect to a sloping road: " + branch.resultCode());
        helper.assertTrue(RoadGraphMaterializationConsistency.validate(graph,ledger).isEmpty(),"rebuilt owners must match graph");
        for(var road:graph.snapshot().segments().values()) {
            var geometry=road.referenceGeometry();
            double junctionDistance=geometry.start().distanceTo(centre)<0.01?0:geometry.length();
            helper.assertTrue(Math.abs(geometry.sample(junctionDistance).tangent().y())<1e-7,"junction tangent must be level");
            for(double distance=20;distance<geometry.length()-20;distance+=5) {
                var sample=geometry.sample(distance);
                helper.assertTrue(Math.abs(sample.tangent().y()/sample.tangent().horizontalLength())<0.15,"approach must be gentle");

            }
            var surface=requireType(helper,new com.cookiecraftmods.roadbuilder.placement.StraightRoadSegmentMaterializer()
                    .plan(level.dimension(),road,ConstructionLimits.defaults()),BlockPlanResult.Success.class,"refitted surface must rasterize").plan();
            for(var block:surface.blocks()) if(Math.hypot(block.position().getX()-centre.x(),block.position().getZ()-centre.z())>20)
                helper.assertTrue(level.getBlockState(block.position()).equals(block.targetState()),
                        "world pavement must follow refreshed geometry at "+block.position());

        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void decorationsRestoreAsStableTerrainAfterSavedDataReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        List<BlockState> sources = List.of(Blocks.WHEAT.defaultBlockState(),
                Blocks.SEA_PICKLE.defaultBlockState().setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true),
                Blocks.FARMLAND.defaultBlockState());
        List<BlockState> expected = List.of(Blocks.AIR.defaultBlockState(),
                Blocks.WATER.defaultBlockState(), Blocks.DIRT.defaultBlockState());
        List<PlannedBlock> targets = new ArrayList<>();
        for (int index = 0; index < sources.size(); index++) {
            BlockPos pos = origin.offset(index, 0, 0);
            level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(pos, sources.get(index), Block.UPDATE_CLIENTS);
            targets.add(new PlannedBlock(pos, Blocks.GRAY_CONCRETE.defaultBlockState()));
        }
        var data = RoadBuilderPlacementSavedData.create(level.dimension());
        var world = new ServerLevelPlacementBlockWorld(level);
        var id = PlacementOperationId.create();
        var result = new BlockPlanCommitter().commit(id,
                BlockPlacementPlan.create(level.dimension(), targets), world, data.ledger());
        helper.assertTrue(result instanceof PlacementCommitResult.Success, "decorations must allow construction: " + result);
        var reloaded = RoadBuilderPlacementSavedData.load(level.dimension(), data.save(new CompoundTag()));
        var removal = new PlacementRemovalService().remove(id, RemovalMode.SAFE, world, reloaded.ledger());
        helper.assertTrue(removal instanceof PlacementRemovalResult.Success, "demolition must succeed: " + removal);
        for (int index = 0; index < expected.size(); index++) {
            helper.assertTrue(level.getBlockState(origin.offset(index, 0, 0)).equals(expected.get(index)),
                    "incorrect restored terrain at index " + index);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void convertsIntersectionIntoRoundaboutWithConsistentSurfaceHistory(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(14000, 0, 14000));
        var presetId = new ResourceLocation("roadbuilder", "roundabout_conversion_test");
        var preset = new com.cookiecraftmods.roadbuilder.preset.RoadPreset(
                com.cookiecraftmods.roadbuilder.preset.RoadPreset.CURRENT_SCHEMA_VERSION, 1, 7,
                new ResourceLocation("minecraft", "gray_concrete"), List.of(
                new com.cookiecraftmods.roadbuilder.preset.LaneComponent(com.cookiecraftmods.roadbuilder.preset.LaneType.VEHICLE,
                        3, -1.75, com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection.FORWARD),
                new com.cookiecraftmods.roadbuilder.preset.LaneComponent(com.cookiecraftmods.roadbuilder.preset.LaneType.BUS,
                        3, 1.75, com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection.BACKWARD)));
        com.cookiecraftmods.roadbuilder.preset.RoadPresetLookup presets = id -> java.util.Optional.of(preset);
        for (int x = -35; x <= 35; x++) for (int z = -35; z <= 35; z++) {
            level.setBlock(new BlockPos(origin.getX() + x, 63, origin.getZ() + z), Blocks.STONE.defaultBlockState(), UPDATE_FLAGS);
        }
        var graph = new RoadGraph(level.dimension());
        var ledger = new PlacementOwnershipLedger(level.dimension());
        var player = testPlayer(level, true);
        player.setPos(origin.getX(), 66, origin.getZ());
        var straight = new StraightRoadPlacementEndpoint(new StraightRoadPlanningService(presets, new StraightRoadPlanner()),
                ConstructionAuthorizer.creativeOnly(), ConstructionLimits.defaults(), new BlockPlanCommitter());
        var center = new RoadPoint(origin.getX() + 0.5, 64, origin.getZ() + 0.5);
        for (int[] offset : List.of(new int[]{30, 0}, new int[]{-30, 0}, new int[]{0, 30}, new int[]{0, -30})) {
            var result = straight.handle(StraightRoadIntentC2SPacket.current(UUID.randomUUID(), level.dimension().location(),
                    presetId, 1, center.x(), center.y(), center.z(), center.x() + offset[0], center.y(), center.z() + offset[1]),
                    player, ledger, graph);
            helper.assertTrue(result.resultCode() == PlacementResultCode.ACCEPTED, "approach must build: " + result.resultCode());
        }
        var endpoint = new com.cookiecraftmods.roadbuilder.networking.RoadPathPlacementEndpoint(
                new com.cookiecraftmods.roadbuilder.placement.RoadPathPlanningService(presets,
                        new com.cookiecraftmods.roadbuilder.placement.RoadPathPlanner()),
                ConstructionAuthorizer.creativeOnly(), ConstructionLimits.defaults(), new BlockPlanCommitter(), TrafficHandedness.RHT);
        var result = endpoint.handle(com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket.current(
                UUID.randomUUID(), level.dimension().location(), presetId, 1, graph.revision(),
                com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket.PathType.ROUNDABOUT,
                List.of(center, new RoadPoint(center.x() + 12, center.y(), center.z()))), player, ledger, graph, null);
        helper.assertTrue(result.resultCode() == PlacementResultCode.ACCEPTED, "conversion must build: " + result.resultCode());
        helper.assertTrue(RoadGraphMaterializationConsistency.validate(graph, ledger).isEmpty(),
                "converted surface history must reference surviving owners: " + RoadGraphMaterializationConsistency.validate(graph, ledger));
        helper.assertTrue(level.getBlockState(BlockPos.containing(center.x(), center.y(), center.z())).is(Blocks.GRASS_BLOCK),
                "old intersection center must become an island");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 200)
    public void complexCurveConnectsToLaneAtSharedNodeAndPreservesThroughRoad(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(12000, 0, 12000));
        var pavement = new ResourceLocation("minecraft", "gray_concrete");
        var mainId = new ResourceLocation("roadbuilder", "port_main_test");
        var rampId = new ResourceLocation("roadbuilder", "port_ramp_test");
        var main = new com.cookiecraftmods.roadbuilder.preset.RoadPreset(
                com.cookiecraftmods.roadbuilder.preset.RoadPreset.CURRENT_SCHEMA_VERSION, 1, 9, pavement,
                List.of(new com.cookiecraftmods.roadbuilder.preset.LaneComponent(
                        com.cookiecraftmods.roadbuilder.preset.LaneType.VEHICLE, 3, -2,
                        com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection.FORWARD),
                        new com.cookiecraftmods.roadbuilder.preset.LaneComponent(
                        com.cookiecraftmods.roadbuilder.preset.LaneType.VEHICLE, 3, 2,
                        com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection.FORWARD)));
        var ramp = new com.cookiecraftmods.roadbuilder.preset.RoadPreset(
                com.cookiecraftmods.roadbuilder.preset.RoadPreset.CURRENT_SCHEMA_VERSION, 1, 3, pavement,
                List.of(new com.cookiecraftmods.roadbuilder.preset.LaneComponent(
                        com.cookiecraftmods.roadbuilder.preset.LaneType.VEHICLE, 3, 0,
                        com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection.FORWARD)));
        com.cookiecraftmods.roadbuilder.preset.RoadPresetLookup presets = id ->
                java.util.Optional.of(id.equals(mainId) ? main : ramp);
        for (int x = -25; x <= 35; x++) {
            for (int z = -8; z <= 25; z++) {
                level.setBlock(new BlockPos(origin.getX() + x, 63, origin.getZ() + z),
                        Blocks.STONE.defaultBlockState(), UPDATE_FLAGS);
            }
        }
        var graph = new RoadGraph(level.dimension());
        var ledger = new PlacementOwnershipLedger(level.dimension());
        var player = testPlayer(level, true);
        player.setPos(origin.getX(), 66, origin.getZ());
        var straight = new StraightRoadPlacementEndpoint(new StraightRoadPlanningService(presets,
                new StraightRoadPlanner()), ConstructionAuthorizer.creativeOnly(),
                ConstructionLimits.defaults(), new BlockPlanCommitter());
        for (int i = 0; i < 2; i++) {
            var result = straight.handle(StraightRoadIntentC2SPacket.current(UUID.randomUUID(),
                    level.dimension().location(), mainId, 1,
                    origin.getX() - 20 + 20 * i + 0.5, 64, origin.getZ() + 0.5,
                    origin.getX() + 20 * i + 0.5, 64, origin.getZ() + 0.5), player, ledger, graph);
            helper.assertTrue(result.resultCode() == PlacementResultCode.ACCEPTED,
                    "main road fixture must build: " + result.resultCode());
        }
        var before = graph.snapshot();
        var port = com.cookiecraftmods.roadbuilder.network.AuxiliaryLanePorts.ports(before.segments().values())
                .stream().filter(candidate -> candidate.incoming()
                        && Math.abs(candidate.position().x() - origin.getX() - 0.5) < 0.001)
                .max(java.util.Comparator.comparingDouble(candidate -> candidate.position().z())).orElseThrow();
        var end = new RoadPoint(origin.getX() + 24.5, 64, origin.getZ() + 20.5);
        var geometry = (com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry)
                com.cookiecraftmods.roadbuilder.geometry.LaneConnectionGeometry.align(
                        new com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry(port.position(),
                                port.position().add(port.direction().scale(9)),
                                end.subtract(port.direction().scale(9)), end), port.direction(), null);
        var endpoint = new com.cookiecraftmods.roadbuilder.networking.RoadPathPlacementEndpoint(
                new com.cookiecraftmods.roadbuilder.placement.RoadPathPlanningService(presets,
                        new com.cookiecraftmods.roadbuilder.placement.RoadPathPlanner()),
                ConstructionAuthorizer.creativeOnly(), ConstructionLimits.defaults(),
                new BlockPlanCommitter(), TrafficHandedness.RHT);
        var packet = com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket.current(
                UUID.randomUUID(), level.dimension().location(), rampId, 1, graph.revision(),
                com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket.PathType.CUBIC,
                geometry.controlPoints());
        var result = endpoint.handle(packet, player, ledger, graph, null);
        helper.assertTrue(result.resultCode() == PlacementResultCode.ACCEPTED,
                "lane-attached ramp must build: " + result.resultCode());
        before.segments().forEach((id, segment) -> helper.assertTrue(
                segment.equals(graph.snapshot().segments().get(id)), "main road geometry must stay unchanged"));
        before.connectors().forEach((id, connector) -> helper.assertTrue(
                connector.equals(graph.snapshot().connectors().get(id)), "through lane connectors must survive"));
        for (int x = -18; x <= 18; x++) {
            helper.assertTrue(level.getBlockState(new BlockPos(origin.getX() + x, 64, origin.getZ() - 2))
                    .is(Blocks.GRAY_CONCRETE), "the other main-road lane must remain paved");
        }
        helper.assertTrue(RoadGraphMaterializationConsistency.validate(graph, ledger).isEmpty(),
                "ramp placement must preserve graph/materialization ownership");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void replacingCurvedBridgeRepeatedlyKeepsTheCompleteGeometry(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(7000, 0, 7000));
        // The test server generates terrain independently of this fixture. Clear it
        // explicitly so ground scanning cannot find a higher surface (or an old road).
        int roadY = 67;
        int groundY = roadY - com.cookiecraftmods.roadbuilder.placement.RoadStructurePlanner
                .MAXIMUM_SOLID_BASE_HEIGHT - 3;
        for (int x = -12; x <= 40; x++) {
            for (int z = -12; z <= 30; z++) {
                level.setBlock(new BlockPos(origin.getX() + x, groundY, origin.getZ() + z),
                        Blocks.STONE.defaultBlockState(), UPDATE_FLAGS);
                for (int y = groundY + 1; y <= roadY + 12; y++) {
                    level.setBlock(new BlockPos(origin.getX() + x, y, origin.getZ() + z),
                            Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                }
            }
        }
        var geometry = new com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry(
                new RoadPoint(origin.getX() + 0.5, 67, origin.getZ() + 0.5),
                new RoadPoint(origin.getX() + 12.5, 67, origin.getZ() + 20.5),
                new RoadPoint(origin.getX() + 24.5, 67, origin.getZ() + 0.5));
        var presetId = new ResourceLocation("roadbuilder", "replace_curve_test");
        var preset = new com.cookiecraftmods.roadbuilder.preset.RoadPreset(
                com.cookiecraftmods.roadbuilder.preset.RoadPreset.CURRENT_SCHEMA_VERSION, 1, 5,
                new ResourceLocation("minecraft", "gray_concrete"),
                List.of(new com.cookiecraftmods.roadbuilder.preset.LaneComponent(
                        com.cookiecraftmods.roadbuilder.preset.LaneType.VEHICLE, 5, 0,
                        com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection.FORWARD)));
        var graph = new RoadGraph(level.dimension());
        var ledger = new PlacementOwnershipLedger(level.dimension());
        var addition = graph.prepareStraightRoad(geometry, presetId, preset, TrafficHandedness.RHT);
        graph.applyAddition(addition);
        var world = new ServerLevelPlacementBlockWorld(level);
        var plan = requireType(helper, new com.cookiecraftmods.roadbuilder.placement.RoadMaterializationPlanner()
                .plan(level.dimension(), List.of(addition.segment()), world, ledger,
                        ConstructionLimits.defaults()), BlockPlanResult.Success.class,
                "initial curved bridge should plan").plan();
        var committer = new BlockPlanCommitter();
        requireType(helper, committer.commitOwned(
                com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner.segment(addition.segment().id()),
                plan, world, ledger), PlacementCommitResult.Success.class, "initial bridge should commit");
        var expected = new java.util.HashMap<BlockPos, BlockState>();
        plan.positions().forEach(pos -> expected.put(pos, world.blockState(pos)));
        var pillar = com.cookiecraftmods.roadbuilder.placement.RoadStructureMaterialSet.standard().pillar();
        helper.assertTrue(expected.entrySet().stream().anyMatch(entry ->
                        entry.getKey().getY() < roadY
                                - com.cookiecraftmods.roadbuilder.placement.RoadStructurePlanner.DECK_THICKNESS
                                && entry.getValue().equals(pillar)),
                "fixture must include bridge supports");
        var endpoint = new com.cookiecraftmods.roadbuilder.networking.SegmentReplacementEndpoint(
                id -> java.util.Optional.of(preset), ConstructionAuthorizer.creativeOnly(),
                ConstructionLimits.defaults(), committer);
        var player = testPlayer(level, true);
        player.setPos(origin.getX() + 12, 69, origin.getZ() + 8);
        var crosswalk = new com.cookiecraftmods.roadbuilder.networking.CrosswalkToggleEndpoint(
                ConstructionAuthorizer.creativeOnly(), ConstructionLimits.defaults(), committer);
        var toggled = crosswalk.handle(
                com.cookiecraftmods.roadbuilder.networking.CrosswalkToggleIntentC2SPacket.current(
                        UUID.randomUUID(), level.dimension().location(), addition.segment().id().value(),
                        addition.segment().startNodeId().value(), graph.revision()), player, ledger, graph);
        helper.assertTrue(toggled.resultCode() == PlacementResultCode.ACCEPTED,
                "terminal crosswalk must be enabled before replacement");
        var crosswalkOwner = com.cookiecraftmods.roadbuilder.placement.RoadMaterializationOwner.node(
                addition.segment().startNodeId());
        ledger.operation(crosswalkOwner).orElseThrow().positions()
                .forEach(pos -> expected.put(pos, world.blockState(pos)));
        var repairPosition = plan.blocks().stream().filter(block ->
                block.position().getY() == 67 && block.targetState().is(Blocks.GRAY_CONCRETE))
                .findFirst().orElseThrow().position();
        for (int click = 1; click <= 3; click++) {
            // Even an unchanged preset must actually rematerialize the surface.
            world.setBlockState(repairPosition, Blocks.GOLD_BLOCK.defaultBlockState());
            var response = endpoint.handle(
                    com.cookiecraftmods.roadbuilder.networking.SegmentReplaceIntentC2SPacket.current(
                            UUID.randomUUID(), level.dimension().location(), addition.segment().id().value(),
                            graph.revision(), presetId, preset.revision()), player, ledger, graph);
            helper.assertTrue(response.resultCode() == PlacementResultCode.ACCEPTED,
                    "replace click " + click + " must succeed: " + response.resultCode());
            helper.assertTrue(ledger.operation(crosswalkOwner).isPresent(),
                    "replacement must rebuild the enabled endpoint crosswalk");
            helper.assertTrue(graph.segment(addition.segment().id()).orElseThrow().referenceGeometry() == geometry,
                    "replacement must retain the exact curve and elevation");
            expected.forEach((pos, state) -> helper.assertTrue(world.blockState(pos).equals(state),
                    "replacement must retain the bridge at " + pos + "; expected " + state
                            + ", got " + world.blockState(pos)));
            helper.assertTrue(RoadGraphMaterializationConsistency.validate(graph, ledger).isEmpty(),
                    "replacement must keep graph and materialization ownership consistent");
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void dividedHighwayBranchesConnectToWiderOneWayRoadsAndRamp(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = helper.absolutePos(new BlockPos(4096, 0, 4096));
        int y = 64;
        for (int x = -70; x <= 170; x++) {
            for (int z = -45; z <= 30; z++) {
                level.setBlock(new BlockPos(origin.getX() + x, y - 1, origin.getZ() + z),
                        Blocks.STONE.defaultBlockState(), UPDATE_FLAGS);
            }
        }
        var presets = new java.util.HashMap<ResourceLocation, com.cookiecraftmods.roadbuilder.preset.RoadPreset>();
        presets.put(new ResourceLocation("roadbuilder", "test_four_lane"), interchangePreset(4, true, false));
        presets.put(new ResourceLocation("roadbuilder", "test_two_out"), interchangePreset(2, false, false));
        presets.put(new ResourceLocation("roadbuilder", "test_two_in"), interchangePreset(2, false, true));
        presets.put(new ResourceLocation("roadbuilder", "test_three_out"), interchangePreset(3, false, false));
        presets.put(new ResourceLocation("roadbuilder", "test_three_in"), interchangePreset(3, false, true));
        presets.put(new ResourceLocation("roadbuilder", "test_ramp"), interchangePreset(1, false, false));
        var endpoint = new StraightRoadPlacementEndpoint(
                new StraightRoadPlanningService(id -> java.util.Optional.ofNullable(presets.get(id)),
                        new StraightRoadPlanner()), ConstructionAuthorizer.creativeOnly(),
                ConstructionLimits.defaults(), new BlockPlanCommitter());
        var graph = new RoadGraph(level.dimension());
        var ledger = new PlacementOwnershipLedger(level.dimension());
        var player = testPlayer(level, true);
        player.setPos(origin.getX() + 40, y + 2, origin.getZ());
        String[] names = {"four_lane", "two_out", "two_in", "three_out", "three_in",
                "two_out", "two_in", "ramp"};
        // Ports are deliberately half a block inside the end caps, as can
        // happen when connecting an offset branch using the road tool.
        double[][] ends = {
                {-60, 0, 0, 0}, {0, -5, 20, -12}, {0, 5, 20, 12},
                {100, -12, 160, -12}, {100, 12, 160, 12},
                {19.8, -12, 100.2, -12}, {19.8, 12, 100.2, 12},
                {60, -35, 100, -17}
        };
        for (int index = 0; index < names.length; index++) {
            var id = new ResourceLocation("roadbuilder", "test_" + names[index]);
            var points = ends[index];
            var result = endpoint.handle(StraightRoadIntentC2SPacket.current(UUID.randomUUID(),
                    level.dimension().location(), id, 1,
                    origin.getX() + points[0] + 0.5, y, origin.getZ() + points[1] + 0.5,
                    origin.getX() + points[2] + 0.5, y, origin.getZ() + points[3] + 0.5),
                    player, ledger, graph);
            helper.assertTrue(result.resultCode() == PlacementResultCode.ACCEPTED,
                    "interchange road " + index + " (" + names[index] + ") failed: " + result.resultCode());
        }
        helper.assertTrue(graph.snapshot().segments().size() >= names.length,
                "all interchange segments should remain in the graph");
        helper.assertTrue(!graph.snapshot().connectors().isEmpty(), "interchange needs directed lane connections");
        for (int x = 25; x < 95; x++) {
            for (int z : new int[]{-12, 12}) {
                helper.assertTrue(level.getBlockState(new BlockPos(origin.getX() + x, y, origin.getZ() + z))
                        .is(Blocks.GRAY_CONCRETE), "connecting highways must have continuous pavement");
            }
        }
        helper.succeed();
    }

    private static com.cookiecraftmods.roadbuilder.preset.RoadPreset interchangePreset(
            int count, boolean twoWay, boolean reverse) {
        var pavement = new ResourceLocation("minecraft", "gray_concrete");
        var lanes = new ArrayList<com.cookiecraftmods.roadbuilder.preset.LaneComponent>();
        for (int index = 0; index < count; index++) {
            boolean backward = twoWay ? index >= count / 2 : reverse;
            lanes.add(new com.cookiecraftmods.roadbuilder.preset.LaneComponent(
                    com.cookiecraftmods.roadbuilder.preset.LaneType.VEHICLE, 3,
                    (index - (count - 1) / 2.0) * 4,
                    backward ? com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection.BACKWARD
                            : com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection.FORWARD));
        }
        int width = count * 4 + 3;
        return new com.cookiecraftmods.roadbuilder.preset.RoadPreset(
                com.cookiecraftmods.roadbuilder.preset.RoadPreset.CURRENT_SCHEMA_VERSION, 1, width,
                pavement, lanes, List.of(
                        new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                                com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.RAILING, 1,
                                new ResourceLocation("minecraft", "smooth_stone")),
                        new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                                com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.PAVEMENT, width - 2, pavement),
                        new com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand(
                                com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole.RAILING, 1,
                                new ResourceLocation("minecraft", "smooth_stone"))),
                com.cookiecraftmods.roadbuilder.preset.RoadPresetDisplay.DEFAULT,
                new com.cookiecraftmods.roadbuilder.preset.RoadClassification(
                        com.cookiecraftmods.roadbuilder.preset.RoadClass.HIGHWAY, 15, 120),
                com.cookiecraftmods.roadbuilder.preset.RoadIntersectionStyle.defaults(pavement));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void parallelTogglePlacesBothRoadsInOneOperation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 2));
        BlockPos end = helper.absolutePos(new BlockPos(4, 2, 5));
        ResourceLocation presetId = new ResourceLocation("roadbuilder", "two_way");
        var source = new StraightGeometry(pointAtBlockCenter(start), pointAtBlockCenter(end));
        var parallel = com.cookiecraftmods.roadbuilder.geometry.ParallelRoadGeometry.create(source, 12);
        var limits = ConstructionLimits.defaults();
        var planner = new com.cookiecraftmods.roadbuilder.placement.RoadPathPlanningService(
                RoadPresetResources.repository(), new com.cookiecraftmods.roadbuilder.placement.RoadPathPlanner());
        List<BlockPlacementPlan> surfaces = new ArrayList<>();
        for (var geometry : List.of(source,
                com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometryReverser.reverse(parallel))) {
            var result = planner.resolve(new com.cookiecraftmods.roadbuilder.placement.RoadPathIntent(
                    level.dimension(), geometry, presetId, testPresetRevision(presetId)), limits);
            var planned = requireType(helper, result,
                    com.cookiecraftmods.roadbuilder.placement.RoadPathPlanningResult.Success.class,
                    "both parallel surfaces should plan");
            surfaces.add(planned.roadPlan().blockPlan());
            for (PlannedBlock block : planned.roadPlan().blockPlan().blocks()) {
                level.setBlock(block.position(), Blocks.DIRT.defaultBlockState(), UPDATE_FLAGS);
            }
        }
        var ledger = new PlacementOwnershipLedger(level.dimension());
        var graph = new RoadGraph(level.dimension());
        ServerPlayer player = testPlayer(level, true);
        player.setPos(start.getX() + 0.5, start.getY() + 1, start.getZ() + 0.5);
        var endpoint = new com.cookiecraftmods.roadbuilder.networking.RoadPathPlacementEndpoint(
                planner, ConstructionAuthorizer.creativeOnly(), limits, new BlockPlanCommitter(), TrafficHandedness.RHT);
        var packet = com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket.current(
                UUID.randomUUID(), level.dimension().location(), presetId, testPresetRevision(presetId),
                com.cookiecraftmods.roadbuilder.networking.RoadPathIntentC2SPacket.PathType.STRAIGHT,
                List.of(source.start(), source.end())).withParallelOffset(12);
        var result = endpoint.handle(packet, player, ledger, graph, null);
        helper.assertTrue(result.resultCode() == PlacementResultCode.ACCEPTED, "parallel pair should be accepted: " + result.resultCode());
        helper.assertTrue(graph.revision() == 1 && graph.snapshot().segments().size() == 2,
                "one placement should publish both road segments at one revision");
        helper.assertTrue(graph.snapshot().segments().values().stream().anyMatch(segment ->
                        segment.referenceGeometry().start().equals(parallel.end())
                                && segment.referenceGeometry().end().equals(parallel.start())),
                "the server must reverse the second parallel road");
        for (BlockPlacementPlan surface : surfaces) {
            for (PlannedBlock block : surface.blocks()) {
                helper.assertTrue(level.getBlockState(block.position()).equals(block.targetState()),
                        "both road surfaces must be materialized");
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void authoritativeEndpointPlacesAndRemovesTestPreset(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 2));
        BlockPos end = helper.absolutePos(new BlockPos(4, 2, 5));
        ResourceLocation presetId = new ResourceLocation("roadbuilder", "two_way");
        ConstructionLimits limits = ConstructionLimits.defaults();
        StraightRoadPlanningService planningService = new StraightRoadPlanningService(
                RoadPresetResources.repository(),
                new StraightRoadPlanner()
        );
        StraightRoadIntent intent = new StraightRoadIntent(
                level.dimension(),
                pointAtBlockCenter(start),
                pointAtBlockCenter(end),
                presetId,
                testPresetRevision(presetId)
        );
        BlockPlanResult.Success planned = requireType(
                helper,
                planningService.plan(intent, limits),
                BlockPlanResult.Success.class,
                "the built-in straight intent should plan successfully"
        );
        for (PlannedBlock block : planned.plan().blocks()) {
            level.setBlock(block.position(), Blocks.DIRT.defaultBlockState(), UPDATE_FLAGS);
        }

        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(level.dimension());
        RoadGraph graph = new RoadGraph(level.dimension());
        ServerPlayer player = testPlayer(level, true);
        player.setPos(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
        StraightRoadPlacementEndpoint endpoint = new StraightRoadPlacementEndpoint(
                planningService,
                ConstructionAuthorizer.creativeOnly(),
                limits,
                new BlockPlanCommitter()
        );
        StraightRoadIntentC2SPacket packet = StraightRoadIntentC2SPacket.current(
                UUID.randomUUID(),
                level.dimension().location(),
                presetId,
                intent.expectedPresetRevision(),
                intent.start().x(), intent.start().y(), intent.start().z(),
                intent.end().x(), intent.end().y(), intent.end().z()
        );

        PlacementResultS2CPacket result = endpoint.handle(packet, player, ledger, graph);

        helper.assertTrue(result.resultCode() == PlacementResultCode.ACCEPTED, "endpoint should accept the road");
        helper.assertTrue(
                result.acceptedPlacement().orElseThrow().affectedBlockCount()
                        >= planned.plan().affectedBlockCount(),
                "accepted materialization should contain the canonical surface"
        );
        for (PlannedBlock block : planned.plan().blocks()) {
            helper.assertTrue(
                    level.getBlockState(block.position()).equals(block.targetState()),
                    "the endpoint should place the preset surface at " + block.position().toShortString()
            );
        }

        PlacementOperationId operationId = new PlacementOperationId(
                result.acceptedPlacement().orElseThrow().operationId()
        );
        helper.assertTrue(
                graph.snapshot().segments().size() == 1,
                "accepted placement should publish one canonical graph segment"
        );
        helper.assertTrue(
                ledger.operation(operationId).orElseThrow().materializationOwner().isPresent(),
                "accepted placement operation should be owned by that graph segment"
        );
        var segmentId = graph.snapshot().segments().keySet().iterator().next();
        PlacementRemovalEndpoint removalEndpoint = new PlacementRemovalEndpoint(
                ConstructionAuthorizer.creativeOnly(),
                limits,
                new PlacementRemovalService()
        );
        RemovalResultS2CPacket staleRemoval = removalEndpoint.handle(
                segmentRemovalPacket(
                        level,
                        segmentId.value(),
                        graph.revision() - 1,
                        RemovalRequestAction.EXECUTE,
                        RemovalMode.SAFE
                ),
                player,
                ledger,
                graph
        );
        helper.assertTrue(
                staleRemoval.resultCode() == RemovalResultCode.STALE_GRAPH,
                "segment removal should reject a stale synchronized graph revision"
        );
        RemovalResultS2CPacket removal = removalEndpoint.handle(
                segmentRemovalPacket(
                        level,
                        segmentId.value(),
                        graph.revision(),
                        RemovalRequestAction.EXECUTE,
                        RemovalMode.SAFE
                ),
                player,
                ledger,
                graph
        );
        helper.assertTrue(
                removal.resultCode() == RemovalResultCode.SUCCESS,
                "accepted endpoint graph segment should be removable"
        );
        helper.assertTrue(
                graph.snapshot().segments().isEmpty()
                        && graph.snapshot().nodes().isEmpty()
                        && graph.snapshot().roads().isEmpty(),
                "segment removal should clean up its orphaned graph records"
        );
        for (PlannedBlock block : planned.plan().blocks()) {
            helper.assertTrue(
                    level.getBlockState(block.position()).is(Blocks.DIRT),
                    "safe removal should restore dirt at " + block.position().toShortString()
            );
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void roadPlacementAdoptsVegetationBrokenByItsOwnSurfaceUpdate(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 8));
        BlockPos end = helper.absolutePos(new BlockPos(10, 2, 8));
        BlockPos reactiveSurface =
                helper.absolutePos(new BlockPos(6, 2, 8));
        ResourceLocation presetId = new ResourceLocation(
                "roadbuilder",
                "two_way"
        );
        ConstructionLimits limits = ConstructionLimits.defaults();
        StraightRoadPlanningService planningService =
                new StraightRoadPlanningService(
                        RoadPresetResources.repository(),
                        new StraightRoadPlanner()
                );
        StraightRoadIntent intent = new StraightRoadIntent(
                level.dimension(),
                pointAtBlockCenter(start),
                pointAtBlockCenter(end),
                presetId,
                testPresetRevision(presetId)
        );
        BlockPlacementPlan surfacePlan = requireType(
                helper,
                planningService.plan(intent, limits),
                BlockPlanResult.Success.class,
                "reactive-block road fixture should plan"
        ).plan();
        for (PlannedBlock block : surfacePlan.blocks()) {
            level.setBlock(
                    block.position(),
                    Blocks.DIRT.defaultBlockState(),
                    UPDATE_FLAGS
            );
        }
        level.setBlock(
                reactiveSurface.above(),
                Blocks.GRASS.defaultBlockState(),
                UPDATE_FLAGS
        );
        helper.assertTrue(
                level.getBlockState(reactiveSurface.above()).is(Blocks.GRASS),
                "fixture grass should initially be supported by dirt"
        );

        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(
                level.dimension()
        );
        RoadGraph graph = new RoadGraph(level.dimension());
        ServerPlayer player = testPlayer(level, true);
        player.setPos(
                start.getX() + 0.5,
                start.getY() + 1.0,
                start.getZ() + 0.5
        );
        PlacementResultS2CPacket result =
                new StraightRoadPlacementEndpoint(
                        planningService,
                        ConstructionAuthorizer.creativeOnly(),
                        limits,
                        new BlockPlanCommitter()
                ).handle(
                        placementPacket(level, presetId, intent),
                        player,
                        ledger,
                        graph
                );

        helper.assertTrue(
                result.resultCode() == PlacementResultCode.ACCEPTED,
                "one request should place a road through update-sensitive vegetation"
        );
        helper.assertTrue(
                level.getBlockState(reactiveSurface).equals(
                        surfacePlan.blocks().stream()
                                .filter(block -> block.position().equals(
                                        reactiveSurface
                                ))
                                .findFirst()
                                .orElseThrow()
                                .targetState()
                )
                        && level.getBlockState(reactiveSurface.above()).isAir(),
                "the road should replace its surface and clear the updated vegetation"
        );
        helper.assertTrue(
                ledger.stack(reactiveSurface.above())
                        .orElseThrow()
                        .top()
                        .beforeState()
                        .isAir(),
                "demolition should leave cleared vegetation as air"
        );
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 150)
    public void largeRemovalQueuesAndCompletesThroughServerTicks(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 2));
        ResourceLocation presetId = new ResourceLocation("roadbuilder", "two_way");
        ConstructionLimits limits = ConstructionLimits.defaults();
        List<PlannedBlock> blocks = new ArrayList<>();
        BlockPos plantLower = start.offset(9, 0, 0);
        BlockPos plantUpper = plantLower.above();
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 8; z++) {
                    blocks.add(new PlannedBlock(
                            start.offset(x, y, z),
                            Blocks.GRAY_CONCRETE.defaultBlockState()
                    ));
                }
            }
        }
        blocks.add(new PlannedBlock(
                plantUpper,
                Blocks.AIR.defaultBlockState()
        ));
        BlockPlacementPlan planned = BlockPlacementPlan.create(
                level.dimension(),
                blocks
        );
        helper.assertTrue(
                planned.affectedBlockCount() > 256,
                "fixture must exceed one construction-job batch"
        );
        for (PlannedBlock block : planned.blocks()) {
            level.setBlock(
                    block.position(),
                    Blocks.DIRT.defaultBlockState(),
                    UPDATE_FLAGS
            );
        }
        int setupFlags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
        level.setBlock(
                plantLower.below(),
                Blocks.DIRT.defaultBlockState(),
                setupFlags
        );
        level.setBlock(
                plantLower,
                Blocks.TALL_GRASS.defaultBlockState().setValue(
                        DoublePlantBlock.HALF,
                        DoubleBlockHalf.LOWER
                ),
                setupFlags
        );
        level.setBlock(
                plantUpper,
                Blocks.TALL_GRASS.defaultBlockState().setValue(
                        DoublePlantBlock.HALF,
                        DoubleBlockHalf.UPPER
                ),
                setupFlags
        );

        RoadBuilderPlacementSavedData placementData =
                RoadBuilderPlacementSavedData.get(level);
        RoadBuilderNetworkSavedData networkData =
                RoadBuilderNetworkSavedData.get(level);
        RoadBuilderConstructionJobSavedData jobData =
                RoadBuilderConstructionJobSavedData.get(level);
        PreparedRoadAddition graphAddition = networkData.graph().prepareStraightRoad(
                new StraightGeometry(
                        pointAtBlockCenter(start),
                        pointAtBlockCenter(start.offset(1, 0, 0))
                ),
                presetId,
                RoadPresetResources.repository().find(presetId).orElseThrow(),
                TrafficHandedness.RHT
        );
        networkData.graph().applyAddition(graphAddition);
        PlacementCommitResult.Success placement = requireType(
                helper,
                new BlockPlanCommitter().commitOwned(
                        RoadMaterializationOwner.segment(
                                graphAddition.segment().id()
                        ),
                        planned,
                        new ServerLevelPlacementBlockWorld(level),
                        placementData.ledger()
                ),
                PlacementCommitResult.Success.class,
                "large graph-owned fixture should commit"
        );
        level.setBlock(
                plantLower,
                Blocks.AIR.defaultBlockState(),
                UPDATE_FLAGS
        );
        ServerPlayer player = testPlayer(level, true);
        player.setPos(
                start.getX() + 0.5,
                start.getY() + 1.0,
                start.getZ() + 0.5
        );
        UUID operationId = placement.operation().id().value();
        var owner = placementData.ledger()
                .operation(new PlacementOperationId(operationId))
                .orElseThrow()
                .materializationOwner()
                .orElseThrow();
        PlacementRemovalEndpoint removalEndpoint = new PlacementRemovalEndpoint(
                ConstructionAuthorizer.creativeOnly(),
                limits,
                new PlacementRemovalService()
        );
        RemovalResultS2CPacket removal = removalEndpoint.handle(
                segmentRemovalPacket(
                        level,
                        owner.segmentId().value(),
                        networkData.graph().revision(),
                        RemovalRequestAction.EXECUTE,
                        RemovalMode.SAFE
                ),
                player,
                placementData.ledger(),
                networkData.graph(),
                jobData
        );
        helper.assertTrue(
                removal.resultCode() == RemovalResultCode.QUEUED,
                "large removal should be queued instead of rejected"
        );
        helper.assertTrue(
                jobData.job(operationId).isPresent(),
                "queued removal should be persisted before its first world batch"
        );
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    jobData.job(operationId).isEmpty(),
                    "completed removal job should be cleaned up"
            );
            helper.assertTrue(
                    placementData.ledger()
                            .operation(new PlacementOperationId(operationId))
                            .isEmpty(),
                    "completion should release placement ownership"
            );
            helper.assertTrue(
                    networkData.graph().segment(owner.segmentId()).isEmpty(),
                    "completion should publish graph removal"
            );
            for (PlannedBlock block : planned.blocks()) {
                if (block.position().equals(plantUpper)) {
                    BlockState restoredPlant = level.getBlockState(plantUpper);
                    helper.assertTrue(
                            restoredPlant.is(Blocks.TALL_GRASS)
                                    || restoredPlant.isAir(),
                            "restored vegetation may remain until its next "
                                    + "neighbor update or update away immediately"
                    );
                } else {
                    helper.assertTrue(
                            level.getBlockState(block.position()).is(Blocks.DIRT),
                            "incremental removal should restore dirt at "
                                    + block.position().toShortString()
                    );
                }
            }
        });
    }

    @GameTest(
            template = FabricGameTest.EMPTY_STRUCTURE,
            batch = "roadbuilderPlacementJobs",
            timeoutTicks = 150
    )
    public void largePlacementQueuesAndPublishesThroughServerTicks(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos end = start.offset(0, 0, 48);
        ResourceLocation presetId = new ResourceLocation("roadbuilder", "two_way");
        ConstructionLimits limits = ConstructionLimits.defaults();
        StraightRoadPlanningService planningService =
                new StraightRoadPlanningService(
                        RoadPresetResources.repository(),
                        new StraightRoadPlanner()
                );
        StraightRoadIntent intent = new StraightRoadIntent(
                level.dimension(),
                pointAtBlockCenter(start),
                pointAtBlockCenter(end),
                presetId,
                testPresetRevision(presetId)
        );
        BlockPlanResult.Success planned = requireType(
                helper,
                planningService.plan(intent, limits),
                BlockPlanResult.Success.class,
                "large straight intent should plan successfully"
        );
        helper.assertTrue(
                planned.plan().affectedBlockCount() > 256,
                "fixture must exceed one construction-job batch"
        );
        for (PlannedBlock block : planned.plan().blocks()) {
            level.setBlock(
                    block.position(),
                    Blocks.DIRT.defaultBlockState(),
                    UPDATE_FLAGS
            );
        }
        BlockPos reactiveSurface = planned.plan().blocks().stream()
                .map(PlannedBlock::position)
                .filter(position -> position.getZ()
                        == (start.getZ() + end.getZ()) / 2)
                .findFirst()
                .orElseThrow();
        BlockPos updateDrivenLeaf = planned.plan().blocks().stream()
                .map(PlannedBlock::position)
                .filter(position -> !position.equals(reactiveSurface))
                .reduce((firstPosition, secondPosition) -> secondPosition)
                .orElseThrow();
        level.setBlock(
                reactiveSurface.below(),
                Blocks.DIRT.defaultBlockState(),
                UPDATE_FLAGS
        );
        DoublePlantBlock.placeAt(
                level,
                Blocks.TALL_GRASS.defaultBlockState(),
                reactiveSurface,
                UPDATE_FLAGS
        );
        level.setBlock(
                updateDrivenLeaf,
                Blocks.OAK_LEAVES.defaultBlockState(),
                UPDATE_FLAGS
        );
        helper.assertTrue(
                level.getBlockState(reactiveSurface).is(Blocks.TALL_GRASS)
                        && level.getBlockState(reactiveSurface.above())
                        .is(Blocks.TALL_GRASS),
                "queued placement fixture should contain two-block vegetation"
        );

        RoadBuilderPlacementSavedData placementData =
                RoadBuilderPlacementSavedData.get(level);
        RoadBuilderNetworkSavedData networkData =
                RoadBuilderNetworkSavedData.get(level);
        RoadBuilderConstructionJobSavedData jobData =
                RoadBuilderConstructionJobSavedData.get(level);
        long graphRevisionBefore = networkData.graph().revision();
        ServerPlayer player = testPlayer(level, true);
        player.setPos(
                start.getX() + 0.5,
                start.getY() + 1.0,
                start.getZ() + 0.5
        );
        StraightRoadPlacementEndpoint endpoint =
                new StraightRoadPlacementEndpoint(
                        planningService,
                        ConstructionAuthorizer.creativeOnly(),
                        limits,
                        new BlockPlanCommitter()
                );
        PlacementResultS2CPacket result = endpoint.handle(
                StraightRoadIntentC2SPacket.current(
                        UUID.randomUUID(),
                        level.dimension().location(),
                        presetId,
                        intent.expectedPresetRevision(),
                        intent.start().x(),
                        intent.start().y(),
                        intent.start().z(),
                        intent.end().x(),
                        intent.end().y(),
                        intent.end().z()
                ),
                player,
                placementData.ledger(),
                networkData.graph(),
                jobData
        );

        helper.assertTrue(
                result.resultCode() == PlacementResultCode.ACCEPTED,
                "large placement should be accepted into the job runner"
        );
        level.setBlock(
                updateDrivenLeaf,
                Blocks.AIR.defaultBlockState(),
                UPDATE_FLAGS
        );
        UUID operationUuid =
                result.acceptedPlacement().orElseThrow().operationId();
        PlacementOperationId operationId =
                new PlacementOperationId(operationUuid);
        helper.assertTrue(
                jobData.job(operationUuid).isPresent(),
                "placement job should persist before its first world batch"
        );
        helper.assertTrue(
                placementData.ledger().operation(operationId).isEmpty()
                        && networkData.graph().revision() == graphRevisionBefore,
                "ownership and graph metadata must remain unpublished while queued"
        );

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    jobData.job(operationUuid).isEmpty(),
                    "completed placement job should be cleaned up"
            );
            var operation = placementData.ledger()
                    .operation(operationId)
                    .orElseThrow();
            var owner = operation.materializationOwner().orElseThrow();
            helper.assertTrue(
                    networkData.graph().segment(owner.segmentId()).isPresent(),
                    "completion should publish the canonical graph segment"
            );
            helper.assertTrue(
                    operation.positions().contains(reactiveSurface)
                            && placementData.ledger().stack(reactiveSurface)
                            .orElseThrow()
                            .top()
                            .beforeState()
                            .isAir(),
                    "queued placement should record air for the update-removed lower plant"
            );
            helper.assertTrue(
                    placementData.ledger().stack(updateDrivenLeaf)
                            .orElseThrow()
                            .top()
                            .beforeState()
                            .is(Blocks.OAK_LEAVES),
                    "queued placement should retain a leaf removed by a world update"
            );
            for (PlannedBlock block : planned.plan().blocks()) {
                helper.assertTrue(
                        level.getBlockState(block.position()).equals(
                                block.targetState()
                        ),
                        "incremental placement should materialize "
                                + block.position().toShortString()
                );
            }

            PlacementRemovalResult cleanup = new PlacementRemovalService().remove(
                    operationId,
                    RemovalMode.FORCE,
                    new ServerLevelPlacementBlockWorld(level),
                    placementData.ledger()
            );
            helper.assertTrue(
                    cleanup instanceof PlacementRemovalResult.Success,
                    "large placement fixture should clean up its world ownership"
            );
            networkData.graph().applyRemoval(
                    networkData.graph().prepareSegmentRemoval(owner.segmentId())
            );
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void gradedEndpointPlacesPersistsTopologyAndRestoresTerrain(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 8));
        BlockPos end = helper.absolutePos(new BlockPos(10, 6, 8));
        ResourceLocation presetId = new ResourceLocation("roadbuilder", "two_way");
        ConstructionLimits limits = ConstructionLimits.defaults();
        StraightRoadPlanningService planningService = new StraightRoadPlanningService(
                RoadPresetResources.repository(),
                new StraightRoadPlanner()
        );
        StraightRoadIntent intent = new StraightRoadIntent(
                level.dimension(),
                pointAtBlockCenter(start),
                pointAtBlockCenter(end),
                presetId,
                testPresetRevision(presetId)
        );
        BlockPlanResult.Success planned = requireType(
                helper,
                planningService.plan(intent, limits),
                BlockPlanResult.Success.class,
                "the maximum-default-grade intent should plan successfully"
        );
        helper.assertTrue(
                planned.plan().bounds().minimum().getY() == start.getY()
                        && planned.plan().bounds().maximum().getY() == end.getY(),
                "the canonical plan should span both endpoint elevations"
        );
        for (PlannedBlock block : planned.plan().blocks()) {
            level.setBlock(block.position(), Blocks.DIRT.defaultBlockState(), UPDATE_FLAGS);
        }

        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(level.dimension());
        RoadGraph graph = new RoadGraph(level.dimension());
        ServerPlayer player = testPlayer(level, true);
        player.setPos(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
        StraightRoadPlacementEndpoint endpoint = new StraightRoadPlacementEndpoint(
                planningService,
                ConstructionAuthorizer.creativeOnly(),
                limits,
                new BlockPlanCommitter()
        );

        PlacementResultS2CPacket result = endpoint.handle(
                placementPacket(level, presetId, intent),
                player,
                ledger,
                graph
        );
        helper.assertTrue(
                result.resultCode() == PlacementResultCode.ACCEPTED,
                "the server should accept the graded road"
        );
        for (PlannedBlock block : planned.plan().blocks()) {
            helper.assertTrue(
                    level.getBlockState(block.position()).equals(block.targetState()),
                    "graded placement should write its surface at "
                            + block.position().toShortString()
            );
        }
        var segment = graph.snapshot().segments().values().iterator().next();
        helper.assertTrue(
                segment.referenceGeometry().start().y() == start.getY()
                        && segment.referenceGeometry().end().y() == end.getY()
                        && segment.lanes().stream().allMatch(lane ->
                        lane.geometry().start().y() == start.getY()
                                || lane.geometry().start().y() == end.getY()),
                "the graph and directed lanes should retain the graded geometry"
        );

        PlacementRemovalEndpoint removalEndpoint = new PlacementRemovalEndpoint(
                ConstructionAuthorizer.creativeOnly(),
                limits,
                new PlacementRemovalService()
        );
        RemovalResultS2CPacket removal = removalEndpoint.handle(
                segmentRemovalPacket(
                        level,
                        segment.id().value(),
                        graph.revision(),
                        RemovalRequestAction.EXECUTE,
                        RemovalMode.SAFE
                ),
                player,
                ledger,
                graph
        );
        helper.assertTrue(
                removal.resultCode() == RemovalResultCode.SUCCESS,
                "the graded segment should remove through the normal graph-backed workflow"
        );
        for (PlannedBlock block : planned.plan().blocks()) {
            helper.assertTrue(
                    level.getBlockState(block.position()).is(Blocks.DIRT),
                    "graded removal should restore dirt at "
                            + block.position().toShortString()
            );
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void exactEndpointPlacementPublishesJoinedTopologyAndConnectors(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 8));
        BlockPos middle = helper.absolutePos(new BlockPos(7, 2, 8));
        BlockPos end = helper.absolutePos(new BlockPos(12, 2, 8));
        ResourceLocation presetId = new ResourceLocation("roadbuilder", "two_way");
        ConstructionLimits limits = ConstructionLimits.defaults();
        StraightRoadPlanningService planningService = new StraightRoadPlanningService(
                RoadPresetResources.repository(),
                new StraightRoadPlanner()
        );
        StraightRoadIntent firstIntent = new StraightRoadIntent(
                level.dimension(),
                pointAtBlockCenter(start),
                pointAtBlockCenter(middle),
                presetId,
                testPresetRevision(presetId)
        );
        StraightRoadIntent secondIntent = new StraightRoadIntent(
                level.dimension(),
                pointAtBlockCenter(middle),
                pointAtBlockCenter(end),
                presetId,
                testPresetRevision(presetId)
        );
        BlockPlanResult.Success firstPlan = requireType(
                helper,
                planningService.plan(firstIntent, limits),
                BlockPlanResult.Success.class,
                "the first joined segment should plan successfully"
        );
        BlockPlanResult.Success secondPlan = requireType(
                helper,
                planningService.plan(secondIntent, limits),
                BlockPlanResult.Success.class,
                "the second joined segment should plan successfully"
        );
        for (PlannedBlock block : firstPlan.plan().blocks()) {
            level.setBlock(block.position(), Blocks.DIRT.defaultBlockState(), UPDATE_FLAGS);
        }
        for (PlannedBlock block : secondPlan.plan().blocks()) {
            level.setBlock(block.position(), Blocks.DIRT.defaultBlockState(), UPDATE_FLAGS);
        }

        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(level.dimension());
        RoadGraph graph = new RoadGraph(level.dimension());
        ServerPlayer player = testPlayer(level, true);
        player.setPos(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
        StraightRoadPlacementEndpoint endpoint = new StraightRoadPlacementEndpoint(
                planningService,
                ConstructionAuthorizer.creativeOnly(),
                limits,
                new BlockPlanCommitter()
        );

        PlacementResultS2CPacket firstResult = endpoint.handle(
                placementPacket(level, presetId, firstIntent),
                player,
                ledger,
                graph
        );
        helper.assertTrue(
                firstResult.resultCode() == PlacementResultCode.ACCEPTED,
                "the first segment should be accepted"
        );
        RoadGraphSnapshot beforeJoin = graph.snapshot();
        PlacementResultS2CPacket secondResult = endpoint.handle(
                placementPacket(level, presetId, secondIntent),
                player,
                ledger,
                graph
        );
        helper.assertTrue(
                secondResult.resultCode() == PlacementResultCode.ACCEPTED,
                "the exact endpoint continuation should be accepted"
        );

        RoadGraphSnapshot joined = graph.snapshot();
        helper.assertTrue(
                joined.roads().size() == 1
                        && joined.nodes().size() == 3
                        && joined.segments().size() == 2
                        && joined.lanes().size() == 4
                        && joined.connectors().size() == 2,
                "joined placement should share one node and publish two directed lane connectors"
        );
        helper.assertTrue(
                RoadGraphDelta.between(beforeJoin, joined).applyTo(beforeJoin).equals(joined),
                "an observer should reconstruct the joined topology from its graph delta"
        );
        helper.assertTrue(
                ledger.operations().size() == 2,
                "each independently removable segment should retain its own materialization"
        );
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 100)
    public void dedicatedServerTwoPlayerAuthorityReplayAndSynchronizationSmoke(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 7));
        BlockPos end = helper.absolutePos(new BlockPos(7, 2, 7));
        ResourceLocation presetId = new ResourceLocation("roadbuilder", "two_way");
        ConstructionLimits limits = ConstructionLimits.defaults();
        StraightRoadPlanningService planningService = new StraightRoadPlanningService(
                RoadPresetResources.repository(),
                new StraightRoadPlanner()
        );
        StraightRoadIntent intent = new StraightRoadIntent(
                level.dimension(),
                pointAtBlockCenter(start),
                pointAtBlockCenter(end),
                presetId,
                testPresetRevision(presetId)
        );
        BlockPlanResult.Success planned = requireType(
                helper,
                planningService.plan(intent, limits),
                BlockPlanResult.Success.class,
                "the smoke-test intent should plan"
        );
        for (PlannedBlock block : planned.plan().blocks()) {
            level.setBlock(block.position(), Blocks.DIRT.defaultBlockState(), UPDATE_FLAGS);
        }

        ServerPlayer creative = testPlayer(level, true);
        creative.setPos(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
        ServerPlayer survival = testPlayer(level, false);
        survival.setPos(start.getX() + 0.5, start.getY() + 1.0, start.getZ() + 0.5);
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(level.dimension());
        RoadGraph graph = new RoadGraph(level.dimension());
        RoadGraphSnapshot observerBefore = graph.snapshot();
        PlayerRequestSessionState sessions = new PlayerRequestSessionState();
        StraightRoadPlacementEndpoint endpoint = new StraightRoadPlacementEndpoint(
                planningService,
                ConstructionAuthorizer.creativeOnly(),
                limits,
                new BlockPlanCommitter()
        );
        UUID requestId = UUID.randomUUID();
        StraightRoadIntentC2SPacket packet = StraightRoadIntentC2SPacket.current(
                requestId,
                level.dimension().location(),
                presetId,
                intent.expectedPresetRevision(),
                intent.start().x(), intent.start().y(), intent.start().z(),
                intent.end().x(), intent.end().y(), intent.end().z()
        );

        helper.assertTrue(
                sessions.tryAcquire(creative.getUUID(), level.getServer().getTickCount()),
                "the creative connection should acquire its request budget"
        );
        PlacementResultS2CPacket accepted = endpoint.handle(packet, creative, ledger, graph);
        sessions.rememberPlacement(creative.getUUID(), accepted);
        helper.assertTrue(
                accepted.resultCode() == PlacementResultCode.ACCEPTED
                        && accepted.acceptedPlacement().isPresent(),
                "the creative connection should receive an accepted operation ID"
        );
        UUID operationId = accepted.acceptedPlacement().orElseThrow().operationId();
        helper.assertTrue(
                ledger.operation(new PlacementOperationId(operationId)).isPresent(),
                "the delivered operation ID should resolve to exactly one server operation"
        );

        PlacementResultS2CPacket replay = sessions.placementResult(
                creative.getUUID(),
                requestId
        ).orElseThrow();
        helper.assertTrue(replay.equals(accepted), "duplicate request should replay its exact result");
        helper.assertTrue(
                ledger.operations().size() == 1,
                "replaying the request must not place a second operation"
        );

        RoadGraphSnapshot authoritativeAfter = graph.snapshot();
        RoadGraphSnapshot observerAfter = RoadGraphDelta
                .between(observerBefore, authoritativeAfter)
                .applyTo(observerBefore);
        helper.assertTrue(
                observerAfter.equals(authoritativeAfter),
                "a second client view should reconstruct the accepted graph mutation"
        );

        StraightRoadIntentC2SPacket survivalPacket = StraightRoadIntentC2SPacket.current(
                UUID.randomUUID(),
                level.dimension().location(),
                presetId,
                intent.expectedPresetRevision(),
                intent.start().x(), intent.start().y(), intent.start().z(),
                intent.end().x(), intent.end().y(), intent.end().z()
        );
        PlacementResultS2CPacket denied = endpoint.handle(
                survivalPacket,
                survival,
                ledger,
                graph
        );
        helper.assertTrue(
                denied.resultCode() == PlacementResultCode.NOT_AUTHORIZED,
                "the survival connection should be rejected by server authority"
        );
        helper.assertTrue(
                graph.snapshot().equals(authoritativeAfter) && ledger.operations().size() == 1,
                "the rejected second connection must not mutate blocks or graph state"
        );

        sessions.disconnect(creative.getUUID());
        helper.assertTrue(
                sessions.placementResult(creative.getUUID(), requestId).isEmpty(),
                "disconnect should discard connection-local replay history"
        );
        helper.assertTrue(
                sessions.tryAcquire(creative.getUUID(), level.getServer().getTickCount()),
                "a reconnect should start with a fresh request budget"
        );
        helper.succeed();
    }

    @GameTest(template = "roadbuilder:crossing_bounds", batch = "roadbuilderCrossingBounds", timeoutTicks = 120)
    public void crossingPlacementPublishesSharedNodeAndIndependentChildOwnership(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        ResourceLocation presetId = new ResourceLocation(
                "roadbuilder",
                "marked_two_way"
        );
        int presetRevision = testPresetRevision(presetId);
        ConstructionLimits limits = ConstructionLimits.defaults();
        StraightRoadPlanningService planningService = new StraightRoadPlanningService(
                RoadPresetResources.repository(),
                new StraightRoadPlanner()
        );
        StraightRoadIntent eastWest = new StraightRoadIntent(
                level.dimension(),
                pointAtBlockCenter(helper.absolutePos(new BlockPos(3, 2, 8))),
                pointAtBlockCenter(helper.absolutePos(new BlockPos(13, 2, 8))),
                presetId,
                presetRevision
        );
        StraightRoadIntent northSouth = new StraightRoadIntent(
                level.dimension(),
                pointAtBlockCenter(helper.absolutePos(new BlockPos(8, 2, 3))),
                pointAtBlockCenter(helper.absolutePos(new BlockPos(8, 2, 13))),
                presetId,
                presetRevision
        );
        BlockPlanResult.Success eastPlan = requireType(
                helper,
                planningService.plan(eastWest, limits),
                BlockPlanResult.Success.class,
                "east-west crossing fixture should plan"
        );
        BlockPlanResult.Success northPlan = requireType(
                helper,
                planningService.plan(northSouth, limits),
                BlockPlanResult.Success.class,
                "north-south crossing fixture should plan"
        );
        java.util.stream.Stream.concat(
                eastPlan.plan().blocks().stream(),
                northPlan.plan().blocks().stream()
        ).forEach(block -> level.setBlock(
                block.position(),
                Blocks.DIRT.defaultBlockState(),
                UPDATE_FLAGS
        ));
        BlockPos eastClearanceColumn =
                helper.absolutePos(new BlockPos(4, 2, 8));
        level.setBlock(
                eastClearanceColumn.above(),
                Blocks.OAK_LOG.defaultBlockState(),
                UPDATE_FLAGS
        );
        level.setBlock(
                eastClearanceColumn.above(4),
                Blocks.OAK_LEAVES.defaultBlockState(),
                UPDATE_FLAGS
        );
        BlockPos intersectionSurface =
                helper.absolutePos(new BlockPos(8, 2, 8));

        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(level.dimension());
        RoadGraph graph = new RoadGraph(level.dimension());
        ServerPlayer player = testPlayer(level, true);
        player.setPos(eastWest.start().x(), eastWest.start().y() + 1.0, eastWest.start().z());
        StraightRoadPlacementEndpoint endpoint = new StraightRoadPlacementEndpoint(
                planningService,
                ConstructionAuthorizer.creativeOnly(),
                limits,
                new BlockPlanCommitter()
        );
        PlacementResultS2CPacket first = endpoint.handle(
                placementPacket(level, presetId, eastWest),
                player,
                ledger,
                graph
        );
        helper.assertTrue(
                level.getBlockState(eastClearanceColumn.above()).isAir()
                        && level.getBlockState(eastClearanceColumn.above(4)).isAir(),
                "road placement should clear tree blocks throughout its headroom"
        );
        level.setBlock(
                intersectionSurface.above(),
                Blocks.GRASS_BLOCK.defaultBlockState(),
                UPDATE_FLAGS
        );
        level.setBlock(
                intersectionSurface.above(2),
                Blocks.OAK_LOG.defaultBlockState(),
                UPDATE_FLAGS
        );
        PlacementResultS2CPacket crossing = endpoint.handle(
                placementPacket(level, presetId, northSouth),
                player,
                ledger,
                graph
        );

        helper.assertTrue(
                first.resultCode() == PlacementResultCode.ACCEPTED
                        && crossing.resultCode() == PlacementResultCode.ACCEPTED,
                "both crossing roads should be accepted"
        );
        RoadGraphSnapshot snapshot = graph.snapshot();
        helper.assertTrue(
                snapshot.segments().size() == 4
                        && snapshot.nodes().size() == 5
                        && ledger.operations().size() == 5,
                "crossing should publish four segment children and one node surface"
        );
        helper.assertTrue(
                level.getBlockState(intersectionSurface).is(Blocks.GRAY_CONCRETE)
                        && level.getBlockState(intersectionSurface.above()).isAir()
                        && level.getBlockState(intersectionSurface.above(2)).isAir(),
                "junction pavement should mask crossing markings and clear overhead blocks"
        );
        helper.assertTrue(
                RoadGraphMaterializationConsistency.validate(graph, ledger).isEmpty(),
                "split graph and ownership must agree"
        );
        var crossingNode = snapshot.nodes().values().stream()
                .filter(node -> snapshot.segments().values().stream()
                        .filter(segment -> segment.startNodeId().equals(node.id())
                                || segment.endNodeId().equals(node.id()))
                        .count() == 4)
                .findFirst()
                .orElseThrow();
        boolean foundJunctionMarking = false;
        for (int xOffset = -4; xOffset <= 4; xOffset++) {
            for (int zOffset = -4; zOffset <= 4; zOffset++) {
                BlockPos position = intersectionSurface.offset(
                        xOffset,
                        0,
                        zOffset
                );
                boolean marking = level.getBlockState(position).is(
                        Blocks.YELLOW_CONCRETE
                ) || level.getBlockState(position).is(
                        Blocks.WHITE_CONCRETE
                );
                foundJunctionMarking |= marking;
                int absoluteX = Math.abs(xOffset);
                int absoluteZ = Math.abs(zOffset);
                boolean roundedCorner = (Math.max(absoluteX, absoluteZ) == 4
                        && Math.min(absoluteX, absoluteZ) >= 1)
                        || Math.min(absoluteX, absoluteZ) >= 3;
                helper.assertTrue(
                        marking || level.getBlockState(position)
                                .is(Blocks.GRAY_CONCRETE)
                                || roundedCorner
                                && level.getBlockState(position)
                                .is(Blocks.SMOOTH_STONE),
                        "junction cell " + position.toShortString()
                                + " was " + level.getBlockState(position)
                                + "; the conflict center should remain paved and markings should use the road material"
                );
            }
        }
        helper.assertTrue(
                level.getBlockState(intersectionSurface.offset(4, 0, 4))
                        .is(Blocks.GRAY_CONCRETE),
                "a one-block curb must not close the intersection corner"
        );
        helper.assertTrue(
                !level.getBlockState(intersectionSurface.offset(2, 0, 2))
                        .is(Blocks.SMOOTH_STONE),
                "the rounded curb should leave the inner conflict area asphalt"
        );
        for (int xDirection : List.of(-1, 1)) {
            for (int zDirection : List.of(-1, 1)) {
                // Each arm is only five blocks long: the old curb-return
                // assertions required geometry beyond both road end caps.
                for (BlockPos offset : List.of(new BlockPos(5, 0, 6),
                        new BlockPos(6, 0, 5), new BlockPos(5, 0, 5))) {
                    BlockPos position = intersectionSurface.offset(
                            xDirection * offset.getX(), 0, zDirection * offset.getZ());
                    BlockState state = level.getBlockState(position);
                    // Retaining walls now begin above a two-block fill gap.
                    // Their one-block outer guardrail ledge is not junction pavement.
                    boolean supportedGuardrail = state.is(Blocks.SMOOTH_STONE)
                            && level.getBlockState(position.above()).is(Blocks.IRON_BARS)
                            && level.getBlockState(position.below()).is(Blocks.SMOOTH_STONE);
                    helper.assertTrue((!state.is(Blocks.SMOOTH_STONE) || supportedGuardrail)
                                    && !state.is(Blocks.GRAY_CONCRETE),
                            "short junction must not leak past road ends at " + position
                                    + ": " + state + ", above=" + level.getBlockState(position.above())
                                    + ", below=" + level.getBlockState(position.below()));
                }
            }
        }
        helper.assertTrue(
                foundJunctionMarking,
                "junction should materialize preset-driven crosswalk or stop-line markings"
        );
        helper.assertTrue(
                snapshot.connectors().size() == 12,
                "crossing node should publish straight, left, and right movements"
        );
        helper.assertTrue(
                ledger.operation(RoadMaterializationOwner.node(crossingNode.id()))
                        .isPresent(),
                "the shared crossing node should own its pavement materialization"
        );
        helper.assertTrue(
                snapshot.segments().values().stream()
                        .allMatch(segment -> segment.startNodeId().equals(crossingNode.id())
                                || segment.endNodeId().equals(crossingNode.id())),
                "every child should terminate at the shared crossing node"
        );

        var removedChild = snapshot.segments().values().stream()
                .sorted(java.util.Comparator.comparing(
                        com.cookiecraftmods.roadbuilder.network.RoadSegment::id
                ))
                .findFirst()
                .orElseThrow();
        PlacementRemovalEndpoint removalEndpoint = new PlacementRemovalEndpoint(
                ConstructionAuthorizer.creativeOnly(),
                limits,
                new PlacementRemovalService()
        );
        RemovalResultS2CPacket removal = removalEndpoint.handle(
                segmentRemovalPacket(
                        level,
                        removedChild.id().value(),
                        graph.revision(),
                        RemovalRequestAction.EXECUTE,
                        RemovalMode.SAFE
                ),
                player,
                ledger,
                graph
        );
        helper.assertTrue(
                removal.resultCode() == RemovalResultCode.SUCCESS
                        && graph.snapshot().segments().size() == 3
                        && ledger.operations().size() == 4,
                "one crossing child should demolish without removing its three neighbors"
        );
        helper.assertTrue(
                RoadGraphMaterializationConsistency.validate(graph, ledger).isEmpty(),
                "graph and ownership must remain consistent after child demolition"
        );
        for (var remaining : graph.snapshot().segments().values().stream()
                .sorted(java.util.Comparator.comparing(
                        com.cookiecraftmods.roadbuilder.network.RoadSegment::id
                ))
                .toList()) {
            RemovalResultS2CPacket remainingRemoval = removalEndpoint.handle(
                    segmentRemovalPacket(
                            level,
                            remaining.id().value(),
                            graph.revision(),
                            RemovalRequestAction.EXECUTE,
                            RemovalMode.SAFE
                    ),
                    player,
                    ledger,
                    graph
            );
            helper.assertTrue(
                    remainingRemoval.resultCode() == RemovalResultCode.SUCCESS,
                    "every remaining crossing child should demolish safely"
            );
        }
        helper.assertTrue(
                graph.snapshot().segments().isEmpty()
                        && graph.snapshot().nodes().isEmpty()
                        && ledger.operations().isEmpty(),
                "the final child should remove the orphan junction surface and node"
        );
        helper.assertTrue(
                level.getBlockState(eastClearanceColumn.above()).is(Blocks.OAK_LOG)
                        && level.getBlockState(eastClearanceColumn.above(4))
                        .is(Blocks.OAK_LEAVES)
                        && level.getBlockState(intersectionSurface.above())
                        .is(Blocks.GRASS_BLOCK)
                        && level.getBlockState(intersectionSurface.above(2))
                        .is(Blocks.OAK_LOG),
                "full demolition should restore cleared overhead blocks; log="
                        + level.getBlockState(eastClearanceColumn.above())
                        + ", leaves="
                        + level.getBlockState(eastClearanceColumn.above(4))
                        + ", intersection-1="
                        + level.getBlockState(intersectionSurface.above())
                        + ", intersection-2="
                        + level.getBlockState(intersectionSurface.above(2))
        );
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void realWorldCommitPersistsAndSafelyRestores(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(2, 2, 2);
        BlockPos position = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.DIRT);
        RoadBuilderPlacementSavedData savedData = RoadBuilderPlacementSavedData.get(level);
        PlacementOperationId operationId = PlacementOperationId.create();
        BlockPlacementPlan plan = plan(
                level,
                new PlannedBlock(position, Blocks.STONE.defaultBlockState())
        );

        PlacementCommitResult.Success committed = requireType(
                helper,
                new BlockPlanCommitter().commit(
                        operationId,
                        plan,
                        new ServerLevelPlacementBlockWorld(level),
                        savedData.ledger()
                ),
                PlacementCommitResult.Success.class,
                "real ServerLevel commit should succeed"
        );

        helper.assertTrue(committed.changedBlockCount() == 1, "one real block should change");
        helper.assertBlockPresent(Blocks.STONE, relative);
        helper.assertTrue(savedData.isDirty(), "ledger publication should mark SavedData dirty");
        RoadBuilderPlacementSavedData decoded = RoadBuilderPlacementSavedData.load(
                level.dimension(),
                savedData.save(new CompoundTag())
        );
        helper.assertTrue(decoded.isCompatible(), "serialized real-world ownership should decode");
        helper.assertTrue(
                decoded.ledger().operation(operationId).isPresent(),
                "decoded ownership should rebuild the operation index"
        );

        requireType(
                helper,
                new PlacementRemovalService().remove(
                        operationId,
                        RemovalMode.SAFE,
                        new ServerLevelPlacementBlockWorld(level),
                        savedData.ledger()
                ),
                PlacementRemovalResult.Success.class,
                "persisted operation should remove successfully"
        );
        helper.assertBlockPresent(Blocks.DIRT, relative);
        helper.assertTrue(savedData.ledger().operation(operationId).isEmpty(), "operation should be released");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void overlappingOperationsRestoreInEitherRemovalOrder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevelPlacementBlockWorld world = new ServerLevelPlacementBlockWorld(level);
        PlacementRemovalService removalService = new PlacementRemovalService();
        BlockPos olderFirstRelative = new BlockPos(2, 2, 2);
        BlockPos newerFirstRelative = new BlockPos(5, 2, 2);
        BlockPos olderFirst = helper.absolutePos(olderFirstRelative);
        BlockPos newerFirst = helper.absolutePos(newerFirstRelative);
        helper.setBlock(olderFirstRelative, Blocks.DIRT);
        helper.setBlock(newerFirstRelative, Blocks.DIRT);

        PlacementOwnershipLedger olderFirstLedger = new PlacementOwnershipLedger(level.dimension());
        PlacementOperationId firstA = operationId(10);
        PlacementOperationId firstB = operationId(11);
        commitSingle(helper, world, olderFirstLedger, firstA, olderFirst, Blocks.STONE.defaultBlockState());
        commitSingle(helper, world, olderFirstLedger, firstB, olderFirst, Blocks.COBBLESTONE.defaultBlockState());
        PlacementRemovalResult.Success hiddenRemoval = requireType(
                helper,
                removalService.remove(firstA, RemovalMode.SAFE, world, olderFirstLedger),
                PlacementRemovalResult.Success.class,
                "hidden older operation should remove"
        );
        helper.assertTrue(hiddenRemoval.summary().shadowedCount() == 1, "older layer should be shadowed");
        helper.assertBlockPresent(Blocks.COBBLESTONE, olderFirstRelative);
        requireType(
                helper,
                removalService.remove(firstB, RemovalMode.SAFE, world, olderFirstLedger),
                PlacementRemovalResult.Success.class,
                "newer operation should restore the rebased base"
        );
        helper.assertBlockPresent(Blocks.DIRT, olderFirstRelative);

        PlacementOwnershipLedger newerFirstLedger = new PlacementOwnershipLedger(level.dimension());
        PlacementOperationId secondA = operationId(12);
        PlacementOperationId secondB = operationId(13);
        commitSingle(helper, world, newerFirstLedger, secondA, newerFirst, Blocks.STONE.defaultBlockState());
        commitSingle(helper, world, newerFirstLedger, secondB, newerFirst, Blocks.COBBLESTONE.defaultBlockState());
        requireType(
                helper,
                removalService.remove(secondB, RemovalMode.SAFE, world, newerFirstLedger),
                PlacementRemovalResult.Success.class,
                "newer operation should restore the older surface"
        );
        helper.assertBlockPresent(Blocks.STONE, newerFirstRelative);
        requireType(
                helper,
                removalService.remove(secondA, RemovalMode.SAFE, world, newerFirstLedger),
                PlacementRemovalResult.Success.class,
                "older operation should then restore terrain"
        );
        helper.assertBlockPresent(Blocks.DIRT, newerFirstRelative);
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void safeAndForceRemovalHandleExternalEdits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevelPlacementBlockWorld world = new ServerLevelPlacementBlockWorld(level);
        PlacementRemovalService removalService = new PlacementRemovalService();
        BlockPos safeRelative = new BlockPos(2, 2, 2);
        BlockPos forceRelative = new BlockPos(5, 2, 2);
        BlockPos safePosition = helper.absolutePos(safeRelative);
        BlockPos forcePosition = helper.absolutePos(forceRelative);
        helper.setBlock(safeRelative, Blocks.DIRT);
        helper.setBlock(forceRelative, Blocks.DIRT);

        PlacementOwnershipLedger safeLedger = new PlacementOwnershipLedger(level.dimension());
        PlacementOperationId safeId = operationId(20);
        commitSingle(helper, world, safeLedger, safeId, safePosition, Blocks.STONE.defaultBlockState());
        helper.setBlock(safeRelative, Blocks.GOLD_BLOCK);
        PlacementRemovalResult.Success safe = requireType(
                helper,
                removalService.remove(safeId, RemovalMode.SAFE, world, safeLedger),
                PlacementRemovalResult.Success.class,
                "safe removal should complete with a conflict"
        );
        helper.assertTrue(safe.summary().conflictCount() == 1, "safe removal should report one conflict");
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, safeRelative);
        helper.assertTrue(safeLedger.operation(safeId).isEmpty(), "safe conflict should release ownership");

        PlacementOwnershipLedger forceLedger = new PlacementOwnershipLedger(level.dimension());
        PlacementOperationId forceId = operationId(21);
        commitSingle(helper, world, forceLedger, forceId, forcePosition, Blocks.STONE.defaultBlockState());
        helper.setBlock(forceRelative, Blocks.GOLD_BLOCK);
        PlacementRemovalResult.Success forced = requireType(
                helper,
                removalService.remove(forceId, RemovalMode.FORCE, world, forceLedger),
                PlacementRemovalResult.Success.class,
                "force removal should restore a supported edit"
        );
        helper.assertTrue(forced.summary().forcedConflictCount() == 1, "force should count the overwrite");
        helper.assertBlockPresent(Blocks.DIRT, forceRelative);
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void placementSupportsRealFluidsAndRejectsBlockEntities(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        ServerLevelPlacementBlockWorld world = new ServerLevelPlacementBlockWorld(level);
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(level.dimension());
        BlockPos waterRelative = new BlockPos(1, 2, 1);
        BlockPos chestRelative = new BlockPos(3, 2, 1);
        BlockPos waterTargetRelative = new BlockPos(1, 2, 4);
        BlockPos chestTargetRelative = new BlockPos(3, 2, 4);
        helper.setBlock(waterRelative, Blocks.WATER);
        helper.setBlock(chestRelative, Blocks.CHEST);
        helper.setBlock(waterTargetRelative, Blocks.DIRT);
        helper.setBlock(chestTargetRelative, Blocks.DIRT);
        helper.assertTrue(helper.getBlockEntity(chestRelative) != null, "test chest should have a block entity");

        PlacementOperationId waterSourceId = operationId(30);
        commitSingle(
                helper,
                world,
                ledger,
                waterSourceId,
                helper.absolutePos(waterRelative),
                Blocks.STONE.defaultBlockState()
        );
        assertCommitFailure(
                helper,
                world,
                ledger,
                operationId(31),
                helper.absolutePos(chestRelative),
                Blocks.STONE.defaultBlockState(),
                PlacementCommitFailureCode.CURRENT_BLOCK_ENTITY_UNSUPPORTED
        );
        PlacementOperationId waterTargetId = operationId(32);
        commitSingle(
                helper,
                world,
                ledger,
                waterTargetId,
                helper.absolutePos(waterTargetRelative),
                Blocks.WATER.defaultBlockState()
        );
        assertCommitFailure(
                helper,
                world,
                ledger,
                operationId(33),
                helper.absolutePos(chestTargetRelative),
                Blocks.CHEST.defaultBlockState(),
                PlacementCommitFailureCode.TARGET_BLOCK_ENTITY_UNSUPPORTED
        );

        helper.assertBlockPresent(Blocks.STONE, waterRelative);
        helper.assertBlockPresent(Blocks.CHEST, chestRelative);
        helper.assertBlockPresent(Blocks.WATER, waterTargetRelative);
        helper.assertBlockPresent(Blocks.DIRT, chestTargetRelative);
        PlacementRemovalService removalService = new PlacementRemovalService();
        requireType(
                helper,
                removalService.remove(
                        waterSourceId,
                        RemovalMode.SAFE,
                        world,
                        ledger
                ),
                PlacementRemovalResult.Success.class,
                "water source should restore"
        );
        requireType(
                helper,
                removalService.remove(
                        waterTargetId,
                        RemovalMode.SAFE,
                        world,
                        ledger
                ),
                PlacementRemovalResult.Success.class,
                "water target should restore"
        );
        helper.assertBlockPresent(Blocks.WATER, waterRelative);
        helper.assertBlockPresent(Blocks.DIRT, waterTargetRelative);
        helper.assertTrue(ledger.operations().isEmpty(), "unsupported transitions must publish no ownership");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void safePreservesAndForceRejectsExternalFluidOrBlockEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerLevelPlacementBlockWorld world = new ServerLevelPlacementBlockWorld(level);
        PlacementRemovalService removalService = new PlacementRemovalService();
        BlockPos safeChestRelative = new BlockPos(1, 2, 2);
        BlockPos forceWaterRelative = new BlockPos(3, 2, 2);
        BlockPos forceChestRelative = new BlockPos(5, 2, 2);
        helper.setBlock(safeChestRelative, Blocks.DIRT);
        helper.setBlock(forceWaterRelative, Blocks.DIRT);
        helper.setBlock(forceChestRelative, Blocks.DIRT);

        PlacementOwnershipLedger safeLedger = new PlacementOwnershipLedger(level.dimension());
        PlacementOperationId safeId = operationId(40);
        commitSingle(
                helper, world, safeLedger, safeId,
                helper.absolutePos(safeChestRelative), Blocks.STONE.defaultBlockState()
        );
        helper.setBlock(safeChestRelative, Blocks.CHEST);
        PlacementRemovalResult.Success safe = requireType(
                helper,
                removalService.remove(safeId, RemovalMode.SAFE, world, safeLedger),
                PlacementRemovalResult.Success.class,
                "safe removal should preserve an external chest"
        );
        helper.assertTrue(safe.summary().conflictCount() == 1, "chest should be a safe conflict");
        helper.assertBlockPresent(Blocks.CHEST, safeChestRelative);
        helper.assertTrue(helper.getBlockEntity(safeChestRelative) != null, "chest block entity should remain");

        PlacementOwnershipLedger waterLedger = new PlacementOwnershipLedger(level.dimension());
        PlacementOperationId waterId = operationId(41);
        commitSingle(
                helper, world, waterLedger, waterId,
                helper.absolutePos(forceWaterRelative), Blocks.STONE.defaultBlockState()
        );
        helper.setBlock(forceWaterRelative, Blocks.WATER);
        PlacementRemovalResult.Failure waterFailure = requireType(
                helper,
                removalService.remove(waterId, RemovalMode.FORCE, world, waterLedger),
                PlacementRemovalResult.Failure.class,
                "force should reject an external fluid"
        );
        helper.assertTrue(
                waterFailure.code() == PlacementRemovalFailureCode.FORCE_CURRENT_FLUID_UNSUPPORTED,
                "force fluid failure should be explicit"
        );
        helper.assertBlockPresent(Blocks.WATER, forceWaterRelative);
        helper.assertTrue(waterLedger.operation(waterId).isPresent(), "failed force must retain ownership");

        PlacementOwnershipLedger chestLedger = new PlacementOwnershipLedger(level.dimension());
        PlacementOperationId chestId = operationId(42);
        commitSingle(
                helper, world, chestLedger, chestId,
                helper.absolutePos(forceChestRelative), Blocks.STONE.defaultBlockState()
        );
        helper.setBlock(forceChestRelative, Blocks.CHEST);
        PlacementRemovalResult.Failure chestFailure = requireType(
                helper,
                removalService.remove(chestId, RemovalMode.FORCE, world, chestLedger),
                PlacementRemovalResult.Failure.class,
                "force should reject an external block entity"
        );
        helper.assertTrue(
                chestFailure.code() == PlacementRemovalFailureCode.FORCE_CURRENT_BLOCK_ENTITY_UNSUPPORTED,
                "force block-entity failure should be explicit"
        );
        helper.assertBlockPresent(Blocks.CHEST, forceChestRelative);
        helper.assertTrue(chestLedger.operation(chestId).isPresent(), "failed force must retain ownership");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void authorizerUsesRealDimensionBoundsDistanceAndPermissionContext(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPlacementPlan plan = plan(
                level,
                new PlannedBlock(position, Blocks.STONE.defaultBlockState())
        );
        ServerPlayer creative = testPlayer(level, true);
        creative.setPos(position.getX() + 0.5, position.getY() + 1.0, position.getZ() + 0.5);
        ServerPlayer survival = testPlayer(level, false);
        survival.setPos(creative.getX(), creative.getY(), creative.getZ());
        ConstructionLimits limits = new ConstructionLimits(16.0, 16, 4, 128);

        assertValidation(
                helper,
                ConstructionAuthorizer.creativeOnly().validate(creative, plan, limits),
                ConstructionValidationCode.ALLOWED
        );
        assertValidation(
                helper,
                ConstructionAuthorizer.creativeOnly().validate(survival, plan, limits),
                ConstructionValidationCode.NOT_CREATIVE
        );
        assertValidation(
                helper,
                new ConstructionAuthorizer(player -> false).validate(creative, plan, limits),
                ConstructionValidationCode.NO_PERMISSION
        );
        assertValidation(
                helper,
                new ConstructionAuthorizer(player -> true, (player, serverLevel, candidate) -> false)
                        .validate(creative, plan, limits),
                ConstructionValidationCode.NO_PERMISSION
        );

        BlockPlacementPlan wrongDimension = BlockPlacementPlan.create(
                Level.NETHER,
                List.of(new PlannedBlock(position, Blocks.STONE.defaultBlockState()))
        );
        assertValidation(
                helper,
                ConstructionAuthorizer.creativeOnly().validate(creative, wrongDimension, limits),
                ConstructionValidationCode.WRONG_DIMENSION
        );

        BlockPos aboveBuildHeight = new BlockPos(
                position.getX(),
                level.getMaxBuildHeight(),
                position.getZ()
        );
        BlockPlacementPlan outsideWorld = plan(
                level,
                new PlannedBlock(aboveBuildHeight, Blocks.STONE.defaultBlockState())
        );
        assertValidation(
                helper,
                ConstructionAuthorizer.creativeOnly().validate(creative, outsideWorld, limits),
                ConstructionValidationCode.OUTSIDE_WORLD
        );

        creative.setPos(position.getX() + 100.0, position.getY(), position.getZ());
        assertValidation(
                helper,
                ConstructionAuthorizer.creativeOnly().validate(
                        creative,
                        plan,
                        new ConstructionLimits(2.0, 16, 4, 128)
                ),
                ConstructionValidationCode.ALLOWED
        );
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void existingRoadAuthorizationUsesTheNearbyPartOfALongFootprint(
            GameTestHelper helper
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos nearby = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos distant = nearby.offset(32, 0, 0);
        BlockPlacementPlan footprint = BlockPlacementPlan.create(
                level.dimension(),
                List.of(
                        new PlannedBlock(
                                nearby,
                                Blocks.STONE.defaultBlockState()
                        ),
                        new PlannedBlock(
                                distant,
                                Blocks.STONE.defaultBlockState()
                        )
                )
        );
        ServerPlayer player = testPlayer(level, true);
        player.setPos(
                nearby.getX() + 0.5,
                nearby.getY() + 1.0,
                nearby.getZ() + 0.5
        );
        ConstructionLimits limits = new ConstructionLimits(8.0, 16, 4, 128);
        ConstructionAuthorizer authorizer = ConstructionAuthorizer.creativeOnly();

        assertValidation(
                helper,
                authorizer.validate(player, footprint, limits),
                ConstructionValidationCode.ALLOWED
        );
        assertValidation(
                helper,
                authorizer.validateExistingFootprint(
                        player,
                        footprint,
                        limits
                ),
                ConstructionValidationCode.ALLOWED
        );
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void removalEndpointPreviewsAndReauthorizesSavedFootprint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos relative = new BlockPos(3, 2, 3);
        BlockPos position = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.DIRT);

        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(level.dimension());
        PlacementOperationId operationId = operationId(50);
        ServerLevelPlacementBlockWorld world = new ServerLevelPlacementBlockWorld(level);
        commitSingle(helper, world, ledger, operationId, position, Blocks.STONE.defaultBlockState());

        ServerPlayer player = testPlayer(level, true);
        player.setPos(position.getX() + 0.5, position.getY() + 1.0, position.getZ() + 0.5);
        ConstructionLimits limits = new ConstructionLimits(16.0, 16, 4, 128);
        PlacementRemovalService removalService = new PlacementRemovalService();

        PlacementRemovalEndpoint footprintDenied = new PlacementRemovalEndpoint(
                new ConstructionAuthorizer(
                        candidate -> true,
                        (candidate, serverLevel, footprint) ->
                                !footprint.positions().equals(List.of(position))
                ),
                limits,
                removalService
        );
        RemovalResultS2CPacket denied = footprintDenied.handle(
                removalPacket(level, operationId, RemovalRequestAction.PREVIEW, RemovalMode.SAFE),
                player,
                ledger
        );
        helper.assertTrue(
                denied.resultCode() == RemovalResultCode.NOT_AUTHORIZED,
                "saved-footprint permission denial should reject preview"
        );
        helper.assertBlockPresent(Blocks.STONE, relative);
        helper.assertTrue(ledger.operation(operationId).isPresent(), "denied preview must retain ownership");

        PlacementRemovalEndpoint endpoint = new PlacementRemovalEndpoint(
                ConstructionAuthorizer.creativeOnly(),
                limits,
                removalService
        );
        helper.setBlock(relative, Blocks.GOLD_BLOCK);
        RemovalResultS2CPacket forcePreview = endpoint.handle(
                removalPacket(level, operationId, RemovalRequestAction.PREVIEW, RemovalMode.FORCE),
                player,
                ledger
        );
        helper.assertTrue(
                forcePreview.resultCode() == RemovalResultCode.SUCCESS,
                "force preview should be available to an authorized builder"
        );
        helper.assertTrue(
                forcePreview.summary().orElseThrow().forcedConflictCount() == 1,
                "force preview should report the overwritten edit"
        );
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, relative);
        helper.assertTrue(ledger.operation(operationId).isPresent(), "force preview must be read-only");

        RemovalResultS2CPacket preview = endpoint.handle(
                removalPacket(level, operationId, RemovalRequestAction.PREVIEW, RemovalMode.SAFE),
                player,
                ledger
        );
        helper.assertTrue(preview.resultCode() == RemovalResultCode.SUCCESS, "safe preview should succeed");
        helper.assertTrue(
                preview.summary().orElseThrow().conflictCount() == 1,
                "safe preview should report the external edit"
        );
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, relative);
        helper.assertTrue(ledger.operation(operationId).isPresent(), "preview must be read-only");

        player.setPos(position.getX() + 100.0, position.getY(), position.getZ());
        RemovalResultS2CPacket distantExecute = endpoint.handle(
                removalPacket(level, operationId, RemovalRequestAction.EXECUTE, RemovalMode.SAFE),
                player,
                ledger
        );
        helper.assertTrue(
                distantExecute.resultCode() == RemovalResultCode.SUCCESS,
                "authorized builders must be able to remove saved roads remotely"
        );
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, relative);
        helper.assertTrue(
                distantExecute.summary().orElseThrow().conflictCount() == 1,
                "safe execute should report the external edit"
        );
        helper.assertTrue(ledger.operation(operationId).isEmpty(), "safe execute should release ownership");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void realCommitAndRemovalCrossAChunkBoundary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 2, 4));
        int boundaryX = Math.floorDiv(anchor.getX(), 16) * 16 + 15;
        BlockPos left = new BlockPos(boundaryX, anchor.getY(), anchor.getZ());
        BlockPos right = left.east();
        level.getChunkAt(left);
        level.getChunkAt(right);
        BlockState originalLeft = level.getBlockState(left);
        BlockState originalRight = level.getBlockState(right);

        try {
            level.setBlock(left, Blocks.DIRT.defaultBlockState(), UPDATE_FLAGS);
            level.setBlock(right, Blocks.DIRT.defaultBlockState(), UPDATE_FLAGS);
            BlockPlacementPlan plan = plan(
                    level,
                    new PlannedBlock(left, Blocks.STONE.defaultBlockState()),
                    new PlannedBlock(right, Blocks.STONE.defaultBlockState())
            );
            helper.assertTrue(plan.touchedChunks().size() == 2, "fixture should cross exactly two chunks");
            PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(level.dimension());
            PlacementOperationId operationId = operationId(50);
            ServerLevelPlacementBlockWorld world = new ServerLevelPlacementBlockWorld(level);

            requireType(
                    helper,
                    new BlockPlanCommitter().commit(operationId, plan, world, ledger),
                    PlacementCommitResult.Success.class,
                    "cross-chunk commit should succeed"
            );
            helper.assertTrue(level.getBlockState(left).is(Blocks.STONE), "left chunk target should change");
            helper.assertTrue(level.getBlockState(right).is(Blocks.STONE), "right chunk target should change");
            requireType(
                    helper,
                    new PlacementRemovalService().remove(operationId, RemovalMode.SAFE, world, ledger),
                    PlacementRemovalResult.Success.class,
                    "cross-chunk operation should remove"
            );
            helper.assertTrue(level.getBlockState(left).is(Blocks.DIRT), "left terrain should restore");
            helper.assertTrue(level.getBlockState(right).is(Blocks.DIRT), "right terrain should restore");
        } finally {
            level.setBlock(left, originalLeft, UPDATE_FLAGS);
            level.setBlock(right, originalRight, UPDATE_FLAGS);
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 300)
    public void placementSurvivesActualChunkUnloadAndReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlacementOperationId operationId = operationId(70);
        BlockPos anchor = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos position = anchor.offset(48 * 16, 0, 48 * 16);
        ChunkPos chunkPosition = new ChunkPos(position);
        AtomicBoolean unloaded = new AtomicBoolean();
        AtomicBoolean loadedAgain = new AtomicBoolean();

        ServerChunkEvents.CHUNK_UNLOAD.register((eventLevel, chunk) -> {
            if (eventLevel == level && chunk.getPos().equals(chunkPosition)) {
                unloaded.set(true);
            }
        });

        LevelChunk initiallyLoaded = level.getChunkAt(position);
        RoadBuilderPlacementSavedData savedData = RoadBuilderPlacementSavedData.get(level);
        ServerLevelPlacementBlockWorld initialWorld = new ServerLevelPlacementBlockWorld(level);
        if (savedData.ledger().operation(operationId).isPresent()) {
            requireType(
                    helper,
                    new PlacementRemovalService().remove(
                            operationId,
                            RemovalMode.FORCE,
                            initialWorld,
                            savedData.ledger()
                    ),
                    PlacementRemovalResult.Success.class,
                    "stale durability fixture should clean up before rerun"
            );
        }
        BlockState originalState = level.getBlockState(position);
        ServerChunkEvents.CHUNK_LOAD.register((eventLevel, chunk) -> {
            if (eventLevel == level && chunk.getPos().equals(chunkPosition)) {
                loadedAgain.set(true);
            }
        });

        level.setBlock(position, Blocks.DIRT.defaultBlockState(), UPDATE_FLAGS);
        requireType(
                helper,
                new BlockPlanCommitter().commit(
                        operationId,
                        plan(level, new PlannedBlock(position, Blocks.STONE.defaultBlockState())),
                        initialWorld,
                        savedData.ledger()
                ),
                PlacementCommitResult.Success.class,
                "distant chunk placement should commit"
        );
        helper.assertTrue(level.getBlockState(position).is(Blocks.STONE), "fixture block should be stone");

        // Persist both stores before releasing the one-tick UNKNOWN load ticket.
        level.getChunkSource().save(true);
        level.getDataStorage().save();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        unloaded.get(),
                        "target chunk has not emitted a real unload event yet"
                ))
                .thenExecute(() -> {
                    ServerLevelPlacementBlockWorld world = new ServerLevelPlacementBlockWorld(level);
                    try {
                        LevelChunk reloaded = level.getChunkAt(position);
                        helper.assertTrue(loadedAgain.get(), "target chunk should emit a reload event");
                        helper.assertTrue(
                                reloaded != initiallyLoaded,
                                "reloaded chunk must not reuse the unloaded LevelChunk instance"
                        );
                        helper.assertTrue(
                                level.getBlockState(position).is(Blocks.STONE),
                                "placed block should survive chunk unload/reload"
                        );
                        helper.assertTrue(
                                savedData.ledger().operation(operationId).isPresent(),
                                "ownership should remain available after chunk reload"
                        );
                        requireType(
                                helper,
                                new PlacementRemovalService().remove(
                                        operationId,
                                        RemovalMode.SAFE,
                                        world,
                                        savedData.ledger()
                                ),
                                PlacementRemovalResult.Success.class,
                                "reloaded operation should remove safely"
                        );
                        helper.assertTrue(
                                level.getBlockState(position).is(Blocks.DIRT),
                                "reloaded operation should restore its original terrain"
                        );
                    } finally {
                        if (savedData.ledger().operation(operationId).isPresent()) {
                            new PlacementRemovalService().remove(
                                    operationId,
                                    RemovalMode.FORCE,
                                    world,
                                    savedData.ledger()
                            );
                        }
                        level.setBlock(position, originalState, UPDATE_FLAGS);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void injectedSecondWriteFailureRollsBackRealFirstWrite(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos firstRelative = new BlockPos(2, 2, 2);
        BlockPos secondRelative = new BlockPos(3, 2, 2);
        BlockPos first = helper.absolutePos(firstRelative);
        BlockPos second = helper.absolutePos(secondRelative);
        helper.setBlock(firstRelative, Blocks.DIRT);
        helper.setBlock(secondRelative, Blocks.DIRT);
        ServerLevelPlacementBlockWorld realWorld = new ServerLevelPlacementBlockWorld(level);
        PlacementBlockWorld rejectingWorld = new RejectingPlacementBlockWorld(realWorld, second);
        PlacementOwnershipLedger ledger = new PlacementOwnershipLedger(level.dimension());
        PlacementOperationId operationId = operationId(60);
        BlockPlacementPlan plan = plan(
                level,
                new PlannedBlock(first, Blocks.STONE.defaultBlockState()),
                new PlannedBlock(second, Blocks.STONE.defaultBlockState())
        );

        PlacementCommitResult.Failure failure = requireType(
                helper,
                new BlockPlanCommitter().commit(operationId, plan, rejectingWorld, ledger),
                PlacementCommitResult.Failure.class,
                "injected second write should reject the commit"
        );

        helper.assertTrue(
                failure.code() == PlacementCommitFailureCode.WORLD_WRITE_REJECTED,
                "write rejection should survive successful rollback"
        );
        helper.assertBlockPresent(Blocks.DIRT, firstRelative);
        helper.assertBlockPresent(Blocks.DIRT, secondRelative);
        helper.assertTrue(ledger.operation(operationId).isEmpty(), "failed commit must publish no operation");
        helper.succeed();
    }

    private static void commitSingle(
            GameTestHelper helper,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            PlacementOperationId operationId,
            BlockPos position,
            BlockState targetState
    ) {
        requireType(
                helper,
                new BlockPlanCommitter().commit(
                        operationId,
                        plan(helper.getLevel(), new PlannedBlock(position, targetState)),
                        world,
                        ledger
                ),
                PlacementCommitResult.Success.class,
                "single-block commit should succeed"
        );
    }

    private static void assertCommitFailure(
            GameTestHelper helper,
            PlacementBlockWorld world,
            PlacementOwnershipLedger ledger,
            PlacementOperationId operationId,
            BlockPos position,
            BlockState targetState,
            PlacementCommitFailureCode expectedCode
    ) {
        PlacementCommitResult.Failure failure = requireType(
                helper,
                new BlockPlanCommitter().commit(
                        operationId,
                        plan(helper.getLevel(), new PlannedBlock(position, targetState)),
                        world,
                        ledger
                ),
                PlacementCommitResult.Failure.class,
                "unsupported transition should fail"
        );
        helper.assertTrue(failure.code() == expectedCode, "expected failure code " + expectedCode);
    }

    private static void assertValidation(
            GameTestHelper helper,
            ConstructionValidationResult result,
            ConstructionValidationCode expectedCode
    ) {
        helper.assertTrue(
                result.code() == expectedCode,
                "expected validation " + expectedCode + " but got " + result.code()
        );
    }

    private static BlockPlacementPlan plan(ServerLevel level, PlannedBlock... blocks) {
        return BlockPlacementPlan.create(level.dimension(), List.of(blocks));
    }

    private static RoadPoint pointAtBlockCenter(BlockPos position) {
        return new RoadPoint(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
    }

    private static PlacementOperationId operationId(long lowBits) {
        return new PlacementOperationId(new UUID(0L, lowBits));
    }

    private static RemovalIntentC2SPacket removalPacket(
            ServerLevel level,
            PlacementOperationId operationId,
            RemovalRequestAction action,
            RemovalMode mode
    ) {
        return RemovalIntentC2SPacket.current(
                UUID.randomUUID(),
                level.dimension().location(),
                operationId.value(),
                action,
                mode
        );
    }

    private static SegmentRemovalIntentC2SPacket segmentRemovalPacket(
            ServerLevel level,
            UUID segmentId,
            long expectedGraphRevision,
            RemovalRequestAction action,
            RemovalMode mode
    ) {
        return SegmentRemovalIntentC2SPacket.current(
                UUID.randomUUID(),
                level.dimension().location(),
                segmentId,
                expectedGraphRevision,
                action,
                mode
        );
    }

    private static StraightRoadIntentC2SPacket placementPacket(
            ServerLevel level,
            ResourceLocation presetId,
            StraightRoadIntent intent
    ) {
        return StraightRoadIntentC2SPacket.current(
                UUID.randomUUID(),
                level.dimension().location(),
                presetId,
                intent.expectedPresetRevision(),
                intent.start().x(),
                intent.start().y(),
                intent.start().z(),
                intent.end().x(),
                intent.end().y(),
                intent.end().z()
        );
    }

    private static int testPresetRevision(ResourceLocation presetId) {
        return RoadPresetResources.repository().find(presetId).orElseThrow().revision();
    }

    private static ServerPlayer testPlayer(ServerLevel level, boolean creative) {
        return new ServerPlayer(
                level.getServer(),
                level,
                new GameProfile(UUID.randomUUID(), "roadbuilder-gametest")
        ) {
            @Override
            public boolean isCreative() {
                return creative;
            }

            @Override
            public boolean isSpectator() {
                return false;
            }
        };
    }

    private static <T> T requireType(
            GameTestHelper helper,
            Object value,
            Class<T> expectedType,
            String message
    ) {
        helper.assertTrue(
                expectedType.isInstance(value),
                message + "; got " + value.getClass().getSimpleName()
        );
        return expectedType.cast(value);
    }

    private static final class RejectingPlacementBlockWorld implements PlacementBlockWorld {
        private final PlacementBlockWorld delegate;
        private final BlockPos rejectedPosition;

        private RejectingPlacementBlockWorld(PlacementBlockWorld delegate, BlockPos rejectedPosition) {
            this.delegate = delegate;
            this.rejectedPosition = rejectedPosition.immutable();
        }

        @Override
        public net.minecraft.resources.ResourceKey<Level> dimension() {
            return delegate.dimension();
        }

        @Override
        public BlockState blockState(BlockPos position) {
            return delegate.blockState(position);
        }

        @Override
        public boolean hasBlockEntity(BlockPos position) {
            return delegate.hasBlockEntity(position);
        }

        @Override
        public boolean setBlockState(BlockPos position, BlockState state) {
            return !rejectedPosition.equals(position) && delegate.setBlockState(position, state);
        }
    }
}

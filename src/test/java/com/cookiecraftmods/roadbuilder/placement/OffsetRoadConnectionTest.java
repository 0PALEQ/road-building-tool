package com.cookiecraftmods.roadbuilder.placement;

import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.network.RoadId;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OffsetRoadConnectionTest {
    private static final ResourceLocation PAVEMENT = new ResourceLocation("minecraft", "gray_concrete");
    private static final ResourceLocation SIDEWALK = new ResourceLocation("minecraft", "smooth_stone");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.bootstrap(); }

    @Test void offsetConnectionsMaterializeEvenWithOnlyTwoArms() {
        for (Fixture fixture : fixtures()) {
            assertTrue(StraightRoadIntersectionMaterializer.requiresJunctionSurface(fixture.node(), fixture.arms()),
                    "Offset lane ports need a junction surface even for tangent two-arm joins");
        }
    }

    @Test void overlappingSidewalksDoNotBlockEntryOrExitLanes() {
        for (Fixture fixture : fixtures()) {
            RoadSegment branch = fixture.arms().stream()
                    .filter(s -> s.presetSnapshot().surfaceBlockWidth() == 13).findFirst().orElseThrow();
            var plan = assertInstanceOf(BlockPlanResult.Success.class,
                    new StraightRoadIntersectionMaterializer().plan(Level.OVERWORLD, fixture.node(), fixture.arms(),
                            PAVEMENT, SIDEWALK, 10000, 1000000)).plan();
            Map<BlockPos, BlockState> junction = new HashMap<>();
            plan.blocks().forEach(b -> junction.put(b.position(), b.targetState()));
            Set<BlockPos> trunkSidewalk = new HashSet<>();
            for (RoadSegment trunk : fixture.arms()) {
                if (trunk == branch) continue;
                var surface = assertInstanceOf(BlockPlanResult.Success.class,
                        new StraightRoadSegmentMaterializer().plan(Level.OVERWORLD, trunk, ConstructionLimits.defaults())).plan();
                surface.blocks().stream().filter(b -> b.targetState().is(Blocks.SMOOTH_STONE))
                        .forEach(b -> trunkSidewalk.add(b.position()));
            }
            boolean atStart = branch.startNodeId().equals(fixture.node().id());
            int conflicts = 0;
            for (double distance = 1; distance < 25; distance += 0.25) {
                var sample = branch.referenceGeometry().sample(atStart ? distance : branch.referenceGeometry().length() - distance);
                var right = sample.tangent().horizontalLeftUnit();
                for (double lateral : new double[]{-0.75, 0, 0.75}) {
                    var p = sample.position().add(right.scale(lateral));
                    BlockPos pos = BlockPos.containing(p.x(), p.y(), p.z());
                    if (trunkSidewalk.contains(pos)) {
                        conflicts++;
                        assertEquals(Blocks.GRAY_CONCRETE.defaultBlockState(), junction.get(pos),
                                "Old sidewalk crosses branch lane at " + pos + " near " + fixture.node().position());
                    }
                }
            }
            if (fixture.arms().size() >= 3) assertTrue(conflicts > 0, "Fixture must reproduce sidewalk/lane overlap");
        }
    }

    private record Fixture(RoadNode node, List<RoadSegment> arms) { }

    private static RoadSegment arm(RoadNodeId start, RoadNodeId end, ReferenceGeometry geometry,
                                   int width, List<LaneComponent> lanes) {
        return RoadSegment.path(new RoadSegmentId(UUID.randomUUID()), new RoadId(UUID.randomUUID()),
                start, end, geometry, PAVEMENT, 1,
                new RoadPresetSnapshot(width, PAVEMENT, lanes, List.of(
                        new RoadSurfaceBand(RoadSurfaceRole.SIDEWALK, 5, SIDEWALK),
                        new RoadSurfaceBand(RoadSurfaceRole.PAVEMENT, width - 10, PAVEMENT),
                        new RoadSurfaceBand(RoadSurfaceRole.SIDEWALK, 5, SIDEWALK))),
                TrafficHandedness.RHT, lanes.stream().map(l -> new RoadLaneId(UUID.randomUUID())).toList());
    }

    // Cubic control points and lane offsets reproduced from the reported roundabout.
    private static List<Fixture> fixtures() {
        List<RoadNodeId> ids = java.util.stream.IntStream.range(0, 8)
                .mapToObj(i -> new RoadNodeId(new UUID(0, i + 1))).toList();
        return List.of(
                new Fixture(new RoadNode(ids.get(0),
                                new RoadPoint(-700.5458304762622, -34.0, 519.8599785964781)), List.of(
                        arm(ids.get(6), ids.get(0), new CubicBezierGeometry(
                                new RoadPoint(-696.2788950436097, -34.0, 444.97042924418764),
                                new RoadPoint(-688.6884790155576, -34.0, 469.63928133535654),
                                new RoadPoint(-690.2025489632225, -34.0, 496.21292367378436),
                                new RoadPoint(-700.5458304762622, -34.0, 519.8599785964781)), 17, List.of(new LaneComponent(LaneType.VEHICLE, 3, -2.0, LaneTravelDirection.FORWARD),
                                new LaneComponent(LaneType.VEHICLE, 3, 2.0, LaneTravelDirection.FORWARD))),
                        arm(ids.get(0), ids.get(7), new CubicBezierGeometry(
                                new RoadPoint(-698.7134502236523, -34.0, 520.6614663442606),
                                new RoadPoint(-708.1096492016119, -34.0, 542.1432787667738),
                                new RoadPoint(-722.0513389802327, -34.0, 556.565497117391),
                                new RoadPoint(-727.7884871093302, -34.0, 587.7100155324919)), 13, List.of(new LaneComponent(LaneType.VEHICLE, 3, 0.0, LaneTravelDirection.FORWARD))))),
                new Fixture(new RoadNode(ids.get(1),
                                new RoadPoint(-641.4594523731757, -34.0, 553.2798843115254)), List.of(
                        arm(ids.get(1), ids.get(4), new CubicBezierGeometry(
                                new RoadPoint(-641.4594523731757, -34.0, 553.2798843115254),
                                new RoadPoint(-634.8296636885913, -34.0, 531.9706268836923),
                                new RoadPoint(-640.1955561965804, -34.0, 508.74230740984405),
                                new RoadPoint(-655.5, -34.0, 492.5)), 17, List.of(new LaneComponent(LaneType.VEHICLE, 3, -2.0, LaneTravelDirection.FORWARD),
                                new LaneComponent(LaneType.VEHICLE, 3, 2.0, LaneTravelDirection.FORWARD))),
                        arm(ids.get(5), ids.get(1), new CubicBezierGeometry(
                                new RoadPoint(-643.2115128906698, -34.0, 603.2899844675081),
                                new RoadPoint(-639.826489442558, -34.0, 584.9141428920439),
                                new RoadPoint(-646.1126625431835, -34.0, 574.9683595959335),
                                new RoadPoint(-639.5497449706701, -34.0, 553.8740371847955)), 13, List.of(new LaneComponent(LaneType.VEHICLE, 3, 0.0, LaneTravelDirection.FORWARD))))),
                new Fixture(new RoadNode(ids.get(5),
                                new RoadPoint(-647.1453256450261, -34.0, 602.5653347496004)), List.of(
                        arm(ids.get(3), ids.get(5), new CubicBezierGeometry(
                                new RoadPoint(-692.5653347496004, -34.0, 633.8546743549739),
                                new RoadPoint(-671.3826330186221, -34.0, 637.7567509896278),
                                new RoadPoint(-651.04740227968, -34.0, 623.7480364805788),
                                new RoadPoint(-647.1453256450261, -34.0, 602.5653347496004)), 21, List.of(new LaneComponent(LaneType.VEHICLE, 3, -4.0, LaneTravelDirection.FORWARD),
                                new LaneComponent(LaneType.VEHICLE, 3, 0.0, LaneTravelDirection.FORWARD),
                                new LaneComponent(LaneType.VEHICLE, 3, 4.0, LaneTravelDirection.FORWARD))),
                        arm(ids.get(5), ids.get(2), new CubicBezierGeometry(
                                new RoadPoint(-647.1453256450261, -34.0, 602.5653347496004),
                                new RoadPoint(-643.2432490103722, -34.0, 581.3826330186221),
                                new RoadPoint(-657.2519635194212, -34.0, 561.04740227968),
                                new RoadPoint(-678.4346652503996, -34.0, 557.1453256450261)), 21, List.of(new LaneComponent(LaneType.VEHICLE, 3, -4.0, LaneTravelDirection.FORWARD),
                                new LaneComponent(LaneType.VEHICLE, 3, 0.0, LaneTravelDirection.FORWARD),
                                new LaneComponent(LaneType.VEHICLE, 3, 4.0, LaneTravelDirection.FORWARD))),
                        arm(ids.get(5), ids.get(1), new CubicBezierGeometry(
                                new RoadPoint(-643.2115128906698, -34.0, 603.2899844675081),
                                new RoadPoint(-639.826489442558, -34.0, 584.9141428920439),
                                new RoadPoint(-646.1126625431835, -34.0, 574.9683595959335),
                                new RoadPoint(-639.5497449706701, -34.0, 553.8740371847955)), 13, List.of(new LaneComponent(LaneType.VEHICLE, 3, 0.0, LaneTravelDirection.FORWARD))))),
                new Fixture(new RoadNode(ids.get(7),
                                new RoadPoint(-723.8546743549739, -34.0, 588.4346652503996)), List.of(
                        arm(ids.get(7), ids.get(3), new CubicBezierGeometry(
                                new RoadPoint(-723.8546743549739, -34.0, 588.4346652503996),
                                new RoadPoint(-727.7567509896278, -34.0, 609.6173669813779),
                                new RoadPoint(-713.7480364805788, -34.0, 629.95259772032),
                                new RoadPoint(-692.5653347496004, -34.0, 633.8546743549739)), 21, List.of(new LaneComponent(LaneType.VEHICLE, 3, -4.0, LaneTravelDirection.FORWARD),
                                new LaneComponent(LaneType.VEHICLE, 3, 0.0, LaneTravelDirection.FORWARD),
                                new LaneComponent(LaneType.VEHICLE, 3, 4.0, LaneTravelDirection.FORWARD))),
                        arm(ids.get(2), ids.get(7), new CubicBezierGeometry(
                                new RoadPoint(-678.4346652503996, -34.0, 557.1453256450261),
                                new RoadPoint(-699.6173669813779, -34.0, 553.2432490103722),
                                new RoadPoint(-719.95259772032, -34.0, 567.2519635194212),
                                new RoadPoint(-723.8546743549739, -34.0, 588.4346652503996)), 21, List.of(new LaneComponent(LaneType.VEHICLE, 3, -4.0, LaneTravelDirection.FORWARD),
                                new LaneComponent(LaneType.VEHICLE, 3, 0.0, LaneTravelDirection.FORWARD),
                                new LaneComponent(LaneType.VEHICLE, 3, 4.0, LaneTravelDirection.FORWARD))),
                        arm(ids.get(0), ids.get(7), new CubicBezierGeometry(
                                new RoadPoint(-698.7134502236523, -34.0, 520.6614663442606),
                                new RoadPoint(-708.1096492016119, -34.0, 542.1432787667738),
                                new RoadPoint(-722.0513389802327, -34.0, 556.565497117391),
                                new RoadPoint(-727.7884871093302, -34.0, 587.7100155324919)), 13, List.of(new LaneComponent(LaneType.VEHICLE, 3, 0.0, LaneTravelDirection.FORWARD))))));
    }
}


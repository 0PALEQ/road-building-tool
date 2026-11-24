package com.cookiecraftmods.roadbuilder.persistence;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry;
import com.cookiecraftmods.roadbuilder.geometry.MultiCurveGeometry;
import com.cookiecraftmods.roadbuilder.geometry.ReferenceGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.StraightGeometry;
import com.cookiecraftmods.roadbuilder.geometry.QuadraticBezierGeometry;
import com.cookiecraftmods.roadbuilder.network.PreparedRoadAddition;
import com.cookiecraftmods.roadbuilder.network.PreparedSegmentRemoval;
import com.cookiecraftmods.roadbuilder.network.RoadGraphLimits;
import com.cookiecraftmods.roadbuilder.network.RoadGraphSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadId;
import com.cookiecraftmods.roadbuilder.network.RoadLaneId;
import com.cookiecraftmods.roadbuilder.network.RoadNode;
import com.cookiecraftmods.roadbuilder.network.RoadNodeId;
import com.cookiecraftmods.roadbuilder.network.RoadNodePortResolver;
import com.cookiecraftmods.roadbuilder.network.RoadPresetSnapshot;
import com.cookiecraftmods.roadbuilder.network.RoadSegment;
import com.cookiecraftmods.roadbuilder.network.RoadSegmentId;
import com.cookiecraftmods.roadbuilder.preset.LaneComponent;
import com.cookiecraftmods.roadbuilder.preset.LaneTravelDirection;
import com.cookiecraftmods.roadbuilder.preset.LaneType;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadMarkingPattern;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceBand;
import com.cookiecraftmods.roadbuilder.preset.RoadSurfaceRole;
import com.cookiecraftmods.roadbuilder.preset.TrafficHandedness;
import com.cookiecraftmods.roadbuilder.test.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoadBuilderNetworkSavedDataTest {
    private static final ResourceLocation PRESET_ID = MinecraftIds.id("roadbuilder", "persistent");
    private static final RoadPreset PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            11,
            5.0,
            MinecraftIds.id("minecraft", "gray_concrete"),
            List.of(
                    new LaneComponent(
                            LaneType.VEHICLE,
                            2.0,
                            -1.25,
                            LaneTravelDirection.FORWARD
                    ),
                    new LaneComponent(
                            LaneType.VEHICLE,
                            2.0,
                            1.25,
                            LaneTravelDirection.BACKWARD
                    )
            ),
            List.of(
                    new RoadSurfaceBand(
                            RoadSurfaceRole.PAVEMENT,
                            1,
                            MinecraftIds.id(
                                    "minecraft",
                                    "gray_concrete"
                            )
                    ),
                    new RoadSurfaceBand(
                            RoadSurfaceRole.MARKING,
                            1,
                            MinecraftIds.id(
                                    "minecraft",
                                    "white_concrete"
                            ),
                            RoadMarkingPattern.DASHED
                    ),
                    new RoadSurfaceBand(
                            RoadSurfaceRole.MARKING,
                            3,
                            MinecraftIds.id(
                                    "minecraft",
                                    "yellow_concrete"
                            ),
                            RoadMarkingPattern.DOUBLE_CONTINUOUS
                    ).withUnderlayBlockSet(List.of(MinecraftIds.id("minecraft", "stone"),
                            MinecraftIds.id("minecraft", "andesite")))
            )
    );
    private static final ResourceLocation NARROW_PRESET_ID =
            MinecraftIds.id("roadbuilder", "narrow_ramp");
    private static final RoadPreset NARROW_PRESET = new RoadPreset(
            RoadPreset.CURRENT_SCHEMA_VERSION,
            1,
            1.0,
            MinecraftIds.id("minecraft", "gray_concrete"),
            List.of(
                    new LaneComponent(
                            LaneType.VEHICLE,
                            1.0,
                            0.0,
                            LaneTravelDirection.FORWARD
                    )
            )
    );

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.bootstrap();
    }

    @Test
    void roundTripsJoinedTopologyStableIdsLanesConnectorsAndRevision() {
        RoadBuilderNetworkSavedData data = RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
        PreparedRoadAddition addition = data.graph().prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(-3.5, 70.0, 2.5),
                        new RoadPoint(14.5, 74.0, 9.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.LHT
        );

        assertFalse(data.isDirty());
        data.graph().applyAddition(addition);
        PreparedRoadAddition continuation = data.graph().prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(14.5, 74.0, 9.5),
                        new RoadPoint(32.5, 78.0, 16.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.LHT
        );
        data.graph().applyAddition(continuation);
        assertTrue(data.isDirty());
        RoadGraphSnapshot expected = data.graph().snapshot();
        assertEquals(1, expected.roads().size());
        assertEquals(3, expected.nodes().size());
        assertEquals(2, expected.segments().size());
        assertEquals(2, expected.connectors().size());

        CompoundTag saved = data.save(new CompoundTag());
        RoadBuilderNetworkSavedData reloaded = RoadBuilderNetworkSavedData.load(
                Level.OVERWORLD,
                saved
        );

        assertTrue(reloaded.isCompatible());
        assertFalse(reloaded.isDirty());
        assertEquals(expected, reloaded.graph().snapshot());
        assertEquals(saved, reloaded.save(new CompoundTag()));
    }

    @Test
    void legacyDoubleMarkingUsesAllVariationsFromTheSavedPavementBand() {
        CompoundTag legacy = oneRoadTag();
        legacy.putInt("schema_version", 4);
        var bands = legacy.getList("segments", Tag.TAG_COMPOUND).getCompound(0)
                .getCompound("preset").getList("surface_bands", Tag.TAG_COMPOUND);
        bands.getCompound(2).remove("underlay_block_set");
        bands.getCompound(0).getList("block_set", Tag.TAG_STRING).add(StringTag.valueOf("minecraft:stone"));

        var data = RoadBuilderNetworkSavedData.load(Level.OVERWORLD, legacy);
        assertTrue(data.isCompatible());
        var segment = data.graph().snapshot().segments().values().iterator().next();
        assertEquals(List.of(MinecraftIds.id("minecraft", "gray_concrete"), MinecraftIds.id("minecraft", "stone")),
                segment.presetSnapshot().surfaceColumns().get(3).blocks());
    }

    @Test
    void segmentRemovalPersistsAnEmptyGraphAtTheNextRevision() {
        RoadBuilderNetworkSavedData data = RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
        PreparedRoadAddition addition = data.graph().prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(6.5, 64.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        );
        data.graph().applyAddition(addition);
        PreparedSegmentRemoval removal = data.graph().prepareSegmentRemoval(
                addition.segment().id()
        );
        data.graph().applyRemoval(removal);

        RoadBuilderNetworkSavedData reloaded = RoadBuilderNetworkSavedData.load(
                Level.OVERWORLD,
                data.save(new CompoundTag())
        );

        RoadGraphSnapshot snapshot = reloaded.graph().snapshot();
        assertEquals(2, snapshot.revision());
        assertTrue(snapshot.roads().isEmpty());
        assertTrue(snapshot.nodes().isEmpty());
        assertTrue(snapshot.segments().isEmpty());
        assertTrue(snapshot.lanes().isEmpty());
    }

    @Test
    void persistsCanonicalCurveControlsAndDerivedLanes() {
        RoadBuilderNetworkSavedData data =
                RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
        QuadraticBezierGeometry geometry =
                new QuadraticBezierGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(6.5, 65.0, 8.5),
                        new RoadPoint(14.5, 66.0, 0.5)
                );
        data.graph().applyNetworkMutation(data.graph().prepareRoadNetwork(
                geometry,
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));

        RoadBuilderNetworkSavedData reloaded =
                RoadBuilderNetworkSavedData.load(
                        Level.OVERWORLD,
                        data.save(new CompoundTag())
                );
        var segment = reloaded.graph().snapshot()
                .segments().values().iterator().next();

        assertEquals(geometry, assertInstanceOf(
                QuadraticBezierGeometry.class,
                segment.referenceGeometry()
        ));
        assertEquals(2, segment.lanes().size());
        assertEquals(
                data.graph().snapshot(),
                reloaded.graph().snapshot()
        );
    }

    @Test
    void malformedTopologyAndUnknownFieldsFailClosedWithoutLosingRawData() {
        CompoundTag valid = oneRoadTag();
        CompoundTag distantNode = valid.copy();
        ListTag nodes = distantNode.getList("nodes", TagIds.COMPOUND);
        CompoundTag firstNode = nodes.getCompound(0);
        firstNode.putDouble("x", firstNode.getDouble("x") + 40.0);
        assertIncompatible(distantNode, "geometry disagrees");

        CompoundTag extraRootField = valid.copy();
        extraRootField.putString("unexpected", "payload");
        assertIncompatible(extraRootField, "must contain exactly");

        CompoundTag unknownHandedness = valid.copy();
        unknownHandedness.getList("roads", TagIds.COMPOUND)
                .getCompound(0)
                .putString("handedness", "center");
        assertIncompatible(unknownHandedness, "handedness is unknown");
    }

    @Test
    void newerSchemaAndReadBoundsAreRawPreservingSentinels() {
        CompoundTag newer = oneRoadTag();
        newer.putInt("schema_version", RoadBuilderNetworkSavedData.CURRENT_SCHEMA_VERSION + 1);
        assertIncompatible(newer, "unsupported road graph schema");

        CompoundTag valid = oneRoadTag();
        CompoundTag retained = valid.copy();
        RoadBuilderNetworkSavedData bounded = assertDoesNotThrow(() ->
                RoadBuilderNetworkSavedData.load(
                        Level.OVERWORLD,
                        valid,
                        new RoadGraphLimits(1, 1, 1, 2, 1)
                )
        );
        assertFalse(bounded.isCompatible());
        assertTrue(bounded.incompatibilityReason().orElseThrow().contains("node count exceeds"));
        assertThrows(IllegalStateException.class, bounded::graph);
        assertEquals(retained, bounded.save(new CompoundTag()));
    }

    @Test
    void boundedLostAnchorLoadRecoveryIsDirtyAndReloadIdempotent() {
        RoadBuilderNetworkSavedData data =
                RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
        data.graph().applyAddition(data.graph().prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 2.5),
                        new RoadPoint(0.5, 64.0, 12.5)
                ),
                NARROW_PRESET_ID,
                NARROW_PRESET,
                TrafficHandedness.RHT
        ));
        CompoundTag saved = data.save(new CompoundTag());
        // Simulate the legacy lost-anchor case: the node that used to sit on
        // a removed centered trunk is still at the old trunk position, 2.5
        // blocks away from the surviving narrow ramp endpoint.
        moveNodeTo(saved, new RoadPoint(0.5, 64.0, 2.5), new RoadPoint(0.5, 64.0, 0.5));

        RoadBuilderNetworkSavedData recovered = RoadBuilderNetworkSavedData.load(
                Level.OVERWORLD,
                saved
        );

        assertTrue(recovered.isCompatible());
        assertTrue(recovered.isDirty());
        RoadNode reanchored = recovered.graph().snapshot().nodes().values()
                .stream()
                .filter(node -> node.position().equals(
                        new RoadPoint(0.5, 64.0, 2.5)
                ))
                .findFirst()
                .orElseThrow();
        assertEquals(
                new RoadPoint(0.5, 64.0, 2.5),
                reanchored.position()
        );
        RoadSegment ramp = recovered.graph().snapshot().segments().values()
                .iterator().next();
        assertEquals(
                new RoadPoint(0.5, 64.0, 2.5),
                ramp.referenceGeometry().start()
        );

        RoadBuilderNetworkSavedData reloaded = RoadBuilderNetworkSavedData.load(
                Level.OVERWORLD,
                recovered.save(new CompoundTag())
        );
        assertTrue(reloaded.isCompatible());
        assertFalse(reloaded.isDirty());
        assertEquals(
                recovered.graph().snapshot(),
                reloaded.graph().snapshot()
        );
    }

    @Test
    void distantDataFailsClosedButBoundedDisconnectedPortsAreSeparated() {
        RoadBuilderNetworkSavedData data =
                RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
        data.graph().applyAddition(data.graph().prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 2.5),
                        new RoadPoint(0.5, 64.0, 12.5)
                ),
                NARROW_PRESET_ID,
                NARROW_PRESET,
                TrafficHandedness.RHT
        ));
        CompoundTag far = data.save(new CompoundTag());
        moveNodeTo(
                far,
                new RoadPoint(0.5, 64.0, 2.5),
                new RoadPoint(0.5, 64.0, 100.5)
        );
        assertIncompatible(far, "geometry disagrees");

        RoadBuilderNetworkSavedData cluster =
                RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
        cluster.graph().applyAddition(cluster.graph().prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(10.5, 64.0, 0.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));
        cluster.graph().applyAddition(cluster.graph().prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 2.5),
                        new RoadPoint(0.5, 64.0, 12.5)
                ),
                NARROW_PRESET_ID,
                NARROW_PRESET,
                TrafficHandedness.RHT
        ));
        cluster.graph().applyAddition(cluster.graph().prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, -2.5),
                        new RoadPoint(0.5, 64.0, -12.5)
                ),
                NARROW_PRESET_ID,
                NARROW_PRESET,
                TrafficHandedness.RHT
        ));
        CompoundTag disconnected = cluster.save(new CompoundTag());
        UUID trunkId = trunkSegmentId(disconnected);
        RoadBuilderNetworkSavedData recovered = RoadBuilderNetworkSavedData.load(
                Level.OVERWORLD, withoutSegment(disconnected, trunkId)
        );
        assertTrue(recovered.isCompatible());
        assertTrue(recovered.isDirty());
        assertEquals(2, recovered.graph().snapshot().segments().size());
        assertEquals(4, recovered.graph().snapshot().nodes().size());
        RoadBuilderNetworkSavedData reloaded = RoadBuilderNetworkSavedData.load(
                Level.OVERWORLD, recovered.save(new CompoundTag())
        );
        assertEquals(recovered.graph().snapshot(), reloaded.graph().snapshot());
    }

    @Test
    void diagnosticActualUserSaveReadOnly() throws Exception {
        String saveFile = System.getProperty(
                "roadbuilder.diagnostic.saveFile"
        );
        Assumptions.assumeTrue(
                saveFile != null && !saveFile.isBlank(),
                "diagnostic requires -Droadbuilder.diagnostic.saveFile=<path>"
        );
        File file = new File(saveFile);
        Assumptions.assumeTrue(
                file.isFile(),
                "diagnostic save file does not exist: " + saveFile
        );
        CompoundTag raw = RoadNbtIo.readCompressed(file.toPath());
        CompoundTag root = raw.contains("data", TagIds.COMPOUND)
                ? raw.getCompound("data")
                : raw;
        ListTag nodeTags = root.getList("nodes", TagIds.COMPOUND);
        ListTag segmentTags = root.getList("segments", TagIds.COMPOUND);
        int invalidNodeCount = 0;
        System.out.println(
                "DIAGNOSTIC raw counts: nodes=" + nodeTags.size()
                        + " segments=" + segmentTags.size()
        );
        for (int index = 0; index < nodeTags.size(); index++) {
            CompoundTag nodeTag = nodeTags.getCompound(index);
            UUID nodeId = nodeTag.getUUID("id");
            double x = nodeTag.getDouble("x");
            double y = nodeTag.getDouble("y");
            double z = nodeTag.getDouble("z");
            RoadPoint position = new RoadPoint(x, y, z);
            List<RoadSegment> incident = new java.util.ArrayList<>();
            for (int segmentIndex = 0;
                 segmentIndex < segmentTags.size();
                 segmentIndex++) {
                CompoundTag segmentTag = segmentTags.getCompound(segmentIndex);
                if (segmentTag.getUUID("start_node").equals(nodeId)
                        || segmentTag.getUUID("end_node").equals(nodeId)) {
                    incident.add(segmentFromTag(segmentTag));
                }
            }
            if (incident.isEmpty()) {
                continue;
            }
            RoadNode node = new RoadNode(
                    new RoadNodeId(nodeId),
                    position
            );
            if (RoadNodePortResolver.isValidTopology(node, incident)) {
                continue;
            }
            invalidNodeCount++;
            System.out.println(
                    "DIAGNOSTIC invalid node " + nodeId
                            + " position=" + position
            );
            for (RoadSegment segment : incident) {
                RoadPoint endpoint = segment.startNodeId().equals(
                        new RoadNodeId(nodeId)
                )
                        ? segment.referenceGeometry().start()
                        : segment.referenceGeometry().end();
                System.out.println(
                        "DIAGNOSTIC   incident segment " + segment.id()
                                + " endpoint=" + endpoint
                                + " width="
                                + segment.presetSnapshot().surfaceBlockWidth()
                );
            }
        }
        System.out.println(
                "DIAGNOSTIC before load: " + invalidNodeCount
                        + " invalid node(s); old strict loader incompatible"
        );
        RoadBuilderNetworkSavedData loaded = RoadBuilderNetworkSavedData.load(
                Level.OVERWORLD,
                root
        );
        System.out.println(
                "DIAGNOSTIC after load compatible=" + loaded.isCompatible()
                        + " reason="
                        + loaded.incompatibilityReason().orElse("none")
        );
        assertTrue(loaded.isCompatible(), loaded.incompatibilityReason().orElse(""));
        RoadBuilderNetworkSavedData reloaded = RoadBuilderNetworkSavedData.load(
                Level.OVERWORLD, loaded.save(new CompoundTag())
        );
        assertTrue(reloaded.isCompatible());
        assertEquals(loaded.graph().snapshot(), reloaded.graph().snapshot());
        assertEquals(segmentTags.size(), loaded.graph().snapshot().segments().size());
        if (loaded.isCompatible()) {
            RoadGraphSnapshot snapshot = loaded.graph().snapshot();
            System.out.println(
                    "DIAGNOSTIC after counts: nodes="
                            + snapshot.nodes().size()
                            + " segments=" + snapshot.segments().size()
                            + " dirty=" + loaded.isDirty()
            );
        }
    }

    private static RoadSegment segmentFromTag(CompoundTag segmentTag) {
        UUID id = segmentTag.getUUID("id");
        UUID roadId = segmentTag.getUUID("road");
        UUID startNodeId = segmentTag.getUUID("start_node");
        UUID endNodeId = segmentTag.getUUID("end_node");
        CompoundTag geometryTag = segmentTag.getCompound("geometry");
        String type = geometryTag.getString("type");
        ReferenceGeometry geometry;
        if (type.equals("straight")) {
            geometry = new StraightGeometry(
                    new RoadPoint(
                            geometryTag.getDouble("start_x"),
                            geometryTag.getDouble("start_y"),
                            geometryTag.getDouble("start_z")
                    ),
                    new RoadPoint(
                            geometryTag.getDouble("end_x"),
                            geometryTag.getDouble("end_y"),
                            geometryTag.getDouble("end_z")
                    )
            );
        } else {
            ListTag points = geometryTag.getList(
                    "control_points",
                    TagIds.COMPOUND
            );
            List<RoadPoint> controls = new java.util.ArrayList<>();
            for (int index = 0; index < points.size(); index++) {
                CompoundTag pointTag = points.getCompound(index);
                controls.add(new RoadPoint(
                        pointTag.getDouble("x"),
                        pointTag.getDouble("y"),
                        pointTag.getDouble("z")
                ));
            }
            geometry = switch (type) {
                case "quadratic" -> new QuadraticBezierGeometry(
                        controls.get(0),
                        controls.get(1),
                        controls.get(2)
                );
                case "cubic" -> new CubicBezierGeometry(
                        controls.get(0),
                        controls.get(1),
                        controls.get(2),
                        controls.get(3)
                );
                default -> new MultiCurveGeometry(controls);
            };
        }
        CompoundTag presetTag = segmentTag.getCompound("preset");
        int surfaceWidth = presetTag.getInt("surface_width");
        ResourceLocation surfaceBlock = MinecraftIds.id(
                presetTag.getString("surface_block")
        );
        ListTag laneTags = presetTag.getList("lanes", TagIds.COMPOUND);
        List<LaneComponent> components = new java.util.ArrayList<>();
        for (int index = 0; index < laneTags.size(); index++) {
            CompoundTag laneTag = laneTags.getCompound(index);
            components.add(new LaneComponent(
                    LaneType.fromSerializedName(laneTag.getString("type"))
                            .orElse(LaneType.VEHICLE),
                    laneTag.getDouble("width"),
                    laneTag.getDouble("offset"),
                    LaneTravelDirection.fromSerializedName(
                            laneTag.getString("direction")
                    ).orElse(LaneTravelDirection.FORWARD)
            ));
        }
        ListTag laneIdTags = segmentTag.getList("lane_ids", TagIds.COMPOUND);
        List<RoadLaneId> laneIds = new java.util.ArrayList<>();
        for (int index = 0; index < laneIdTags.size(); index++) {
            laneIds.add(new RoadLaneId(
                    laneIdTags.getCompound(index).getUUID("id")
            ));
        }
        return RoadSegment.path(
                new RoadSegmentId(id),
                new RoadId(roadId),
                new RoadNodeId(startNodeId),
                new RoadNodeId(endNodeId),
                geometry,
                MinecraftIds.id(presetTag.getString("source_id")),
                presetTag.getInt("source_revision"),
                new RoadPresetSnapshot(
                        surfaceWidth,
                        surfaceBlock,
                        components
                ),
                TrafficHandedness.RHT,
                laneIds
        );
    }

    private static void moveNodeTo(
            CompoundTag tag,
            RoadPoint from,
            RoadPoint to
    ) {
        ListTag nodes = tag.getList("nodes", TagIds.COMPOUND);
        for (int index = 0; index < nodes.size(); index++) {
            CompoundTag nodeTag = nodes.getCompound(index);
            if (nodeTag.getDouble("x") == from.x()
                    && nodeTag.getDouble("y") == from.y()
                    && nodeTag.getDouble("z") == from.z()) {
                nodeTag.putDouble("x", to.x());
                nodeTag.putDouble("y", to.y());
                nodeTag.putDouble("z", to.z());
                return;
            }
        }
        throw new IllegalStateException("node not found at " + from);
    }

    private static UUID trunkSegmentId(CompoundTag tag) {
        ListTag segments = tag.getList("segments", TagIds.COMPOUND);
        for (int index = 0; index < segments.size(); index++) {
            CompoundTag segmentTag = segments.getCompound(index);
            CompoundTag geometryTag = segmentTag.getCompound("geometry");
            if (geometryTag.getString("type").equals("straight")
                    && geometryTag.getDouble("start_x") == 0.5
                    && geometryTag.getDouble("start_z") == 0.5
                    && geometryTag.getDouble("end_x") == 10.5
                    && geometryTag.getDouble("end_z") == 0.5) {
                return segmentTag.getUUID("id");
            }
        }
        throw new IllegalStateException("trunk segment not found");
    }

    private static CompoundTag withoutSegment(
            CompoundTag source,
            UUID segmentId
    ) {
        CompoundTag tag = source.copy();
        ListTag segments = tag.getList("segments", TagIds.COMPOUND);
        ListTag remainingSegments = new ListTag();
        Set<UUID> removedLanes = new HashSet<>();
        UUID removedRoad = null;
        for (int index = 0; index < segments.size(); index++) {
            CompoundTag segmentTag = segments.getCompound(index);
            if (segmentTag.getUUID("id").equals(segmentId)) {
                removedRoad = segmentTag.getUUID("road");
                ListTag laneIds = segmentTag.getList(
                        "lane_ids",
                        TagIds.COMPOUND
                );
                for (int laneIndex = 0; laneIndex < laneIds.size(); laneIndex++) {
                    removedLanes.add(
                            laneIds.getCompound(laneIndex).getUUID("id")
                    );
                }
            } else {
                remainingSegments.add(segmentTag);
            }
        }
        tag.put("segments", remainingSegments);
        Set<UUID> referencedNodes = new HashSet<>();
        for (int index = 0; index < remainingSegments.size(); index++) {
            referencedNodes.add(remainingSegments.getCompound(index).getUUID("start_node"));
            referencedNodes.add(remainingSegments.getCompound(index).getUUID("end_node"));
        }
        ListTag remainingNodes = new ListTag();
        ListTag nodes = tag.getList("nodes", TagIds.COMPOUND);
        for (int index = 0; index < nodes.size(); index++) {
            if (referencedNodes.contains(nodes.getCompound(index).getUUID("id"))) {
                remainingNodes.add(nodes.getCompound(index));
            }
        }
        tag.put("nodes", remainingNodes);
        if (removedRoad != null) {
            ListTag roads = tag.getList("roads", TagIds.COMPOUND);
            ListTag remainingRoads = new ListTag();
            for (int index = 0; index < roads.size(); index++) {
                CompoundTag roadTag = roads.getCompound(index);
                if (!roadTag.getUUID("id").equals(removedRoad)) {
                    remainingRoads.add(roadTag);
                }
            }
            tag.put("roads", remainingRoads);
        }
        ListTag connectors = tag.getList("connectors", TagIds.COMPOUND);
        ListTag remainingConnectors = new ListTag();
        for (int index = 0; index < connectors.size(); index++) {
            CompoundTag connectorTag = connectors.getCompound(index);
            if (!removedLanes.contains(connectorTag.getUUID("from_lane"))
                    && !removedLanes.contains(connectorTag.getUUID("to_lane"))) {
                remainingConnectors.add(connectorTag);
            }
        }
        tag.put("connectors", remainingConnectors);
        return tag;
    }

    private static CompoundTag oneRoadTag() {
        RoadBuilderNetworkSavedData data = RoadBuilderNetworkSavedData.create(Level.OVERWORLD);
        data.graph().applyAddition(data.graph().prepareStraightRoad(
                new StraightGeometry(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(8.5, 64.0, 3.5)
                ),
                PRESET_ID,
                PRESET,
                TrafficHandedness.RHT
        ));
        return data.save(new CompoundTag());
    }

    private static void assertIncompatible(CompoundTag raw, String expectedReason) {
        CompoundTag retained = raw.copy();
        RoadBuilderNetworkSavedData data = assertDoesNotThrow(
                () -> RoadBuilderNetworkSavedData.load(Level.OVERWORLD, raw)
        );
        assertFalse(data.isCompatible());
        assertTrue(
                data.incompatibilityReason().orElseThrow().contains(expectedReason),
                data.incompatibilityReason()::orElseThrow
        );
        assertThrows(IllegalStateException.class, data::graph);
        assertEquals(retained, data.incompatibleRawTag().orElseThrow());
        assertEquals(retained, data.save(new CompoundTag()));
    }

    private static final class TagIds {
        private static final int COMPOUND = 10;

        private TagIds() {
        }
    }
}

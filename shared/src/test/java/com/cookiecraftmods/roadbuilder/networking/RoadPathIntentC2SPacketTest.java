package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.geometry.MultiCurveGeometry;
import com.cookiecraftmods.roadbuilder.geometry.RoadPoint;
import com.cookiecraftmods.roadbuilder.geometry.RoundaboutGeometry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoadPathIntentC2SPacketTest {
    private static final UUID REQUEST_ID = UUID.fromString(
            "665730cf-b9bd-41b5-b846-296831ba42e6"
    );
    private static final ResourceLocation DIMENSION =
            MinecraftIds.id("minecraft", "overworld");
    private static final ResourceLocation PRESET =
            MinecraftIds.id("roadbuilder", "two_way");

    @Test
    void sunkenModeSurvivesParallelOffsetAndWireRoundTrip() {
        var packet = RoadPathIntentC2SPacket.current(REQUEST_ID, DIMENSION, PRESET, 1,
                RoadPathIntentC2SPacket.PathType.STRAIGHT,
                List.of(new RoadPoint(0, 64, 0), new RoadPoint(30, 64, 0)))
                .withSunkenRoad(true).withParallelOffset(8);
        FriendlyByteBuf buffer = PacketByteBufs.create();
        try {
            packet.write(buffer);
            assertEquals(packet, RoadPathIntentC2SPacket.TYPE.read(buffer));
            assertEquals(true, packet.sunkenRoad());
        } finally {
            buffer.release();
        }
    }

    @Test
    void laneAttachedCurveUsesOrdinaryCubicPacket() {
        var geometry = new com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry(
                new RoadPoint(0, 64, 3), new RoadPoint(10, 64, 3),
                new RoadPoint(20, 65, 15), new RoadPoint(30, 65, 20));
        var packet = RoadPathIntentC2SPacket.current(REQUEST_ID, DIMENSION, PRESET, 2, 19L,
                RoadPathIntentC2SPacket.PathType.CUBIC, geometry.controlPoints());
        FriendlyByteBuf buffer = PacketByteBufs.create();
        try {
            packet.write(buffer);
            var decoded = RoadPathIntentC2SPacket.TYPE.read(buffer);
            assertEquals(RoadPathIntentC2SPacket.PathType.CUBIC, decoded.pathType());
            assertEquals(geometry, decoded.geometry());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void parallelPreviewSurvivesThePlacementPacketUnchanged() {
        var source = new com.cookiecraftmods.roadbuilder.geometry.CubicBezierGeometry(
                new RoadPoint(0, 64, 0), new RoadPoint(30, 64, 0),
                new RoadPoint(30, 64, 30), new RoadPoint(60, 64, 30));
        var preview = com.cookiecraftmods.roadbuilder.geometry.ParallelRoadGeometry.create(source, 8);
        RoadPathIntentC2SPacket packet = RoadPathIntentC2SPacket.current(
                REQUEST_ID, DIMENSION, PRESET, 2, 19L,
                RoadPathIntentC2SPacket.PathType.CUBIC, source.controlPoints()).withParallelOffset(8);
        FriendlyByteBuf buffer = PacketByteBufs.create();
        try {
            packet.write(buffer);
            var decoded = RoadPathIntentC2SPacket.TYPE.read(buffer);
            assertEquals(source, decoded.geometry());
            assertEquals(8, decoded.parallelOffset());
            assertEquals(preview, com.cookiecraftmods.roadbuilder.geometry.ParallelRoadGeometry.create(decoded.geometry(), decoded.parallelOffset()));
            assertEquals(19L, decoded.expectedGraphRevision());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripsCanonicalMultiControlData() {
        RoadPathIntentC2SPacket packet = RoadPathIntentC2SPacket.current(
                REQUEST_ID,
                DIMENSION,
                PRESET,
                2,
                19L,
                RoadPathIntentC2SPacket.PathType.MULTI,
                List.of(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(5.5, 64.0, 5.5),
                        new RoadPoint(11.5, 65.0, -2.5),
                        new RoadPoint(18.5, 65.0, 3.5)
                )
        );
        FriendlyByteBuf buffer = PacketByteBufs.create();

        packet.write(buffer);
        RoadPathIntentC2SPacket decoded =
                RoadPathIntentC2SPacket.TYPE.read(buffer);

        assertEquals(packet, decoded);
        assertEquals(0, buffer.readableBytes());
        assertEquals(19L, decoded.expectedGraphRevision());
        assertSame(RoadPathIntentC2SPacket.TYPE, packet.packetType());
        assertInstanceOf(MultiCurveGeometry.class, decoded.geometry());
    }

    @Test
    void roundTripsCanonicalRoundaboutCenterAndRadius() {
        RoadPathIntentC2SPacket packet = RoadPathIntentC2SPacket.current(
                REQUEST_ID,
                DIMENSION,
                PRESET,
                2,
                21L,
                RoadPathIntentC2SPacket.PathType.ROUNDABOUT,
                List.of(
                        new RoadPoint(20.5, 64.0, 20.5),
                        new RoadPoint(32.5, 64.0, 20.5)
                )
        );
        FriendlyByteBuf buffer = PacketByteBufs.create();

        packet.write(buffer);
        RoadPathIntentC2SPacket decoded =
                RoadPathIntentC2SPacket.TYPE.read(buffer);

        assertEquals(packet, decoded);
        RoundaboutGeometry geometry = assertInstanceOf(
                RoundaboutGeometry.class,
                decoded.geometry()
        );
        assertEquals(12.0, geometry.radius(), 1.0e-9);
        assertEquals(geometry.start(), geometry.end());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void rejectsWrongCountsVerticalPathsAndTrailingData() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RoadPathIntentC2SPacket.current(
                        REQUEST_ID,
                        DIMENSION,
                        PRESET,
                        2,
                        RoadPathIntentC2SPacket.PathType.QUADRATIC,
                        List.of(
                                new RoadPoint(0.0, 64.0, 0.0),
                                new RoadPoint(5.0, 64.0, 5.0)
                        )
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RoadPathIntentC2SPacket.current(
                        REQUEST_ID,
                        DIMENSION,
                        PRESET,
                        2,
                        RoadPathIntentC2SPacket.PathType.QUADRATIC,
                        List.of(
                                new RoadPoint(0.0, 64.0, 0.0),
                                new RoadPoint(5_000.0, 64.0, 0.0),
                                new RoadPoint(10_000.0, 64.0, 0.0)
                        )
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> RoadPathIntentC2SPacket.current(
                        REQUEST_ID,
                        DIMENSION,
                        PRESET,
                        2,
                        RoadPathIntentC2SPacket.PathType.QUADRATIC,
                        List.of(
                                new RoadPoint(0.0, 64.0, 0.0),
                                new RoadPoint(1.0, 66.0, 0.0),
                                new RoadPoint(0.0, 68.0, 0.0)
                        )
                )
        );

        FriendlyByteBuf trailing = PacketByteBufs.create();
        validQuadratic().write(trailing);
        trailing.writeByte(0x5a);
        assertThrows(
                RuntimeException.class,
                () -> RoadPathIntentC2SPacket.TYPE.read(trailing)
        );
    }

    private static RoadPathIntentC2SPacket validQuadratic() {
        return RoadPathIntentC2SPacket.current(
                REQUEST_ID,
                DIMENSION,
                PRESET,
                2,
                RoadPathIntentC2SPacket.PathType.QUADRATIC,
                List.of(
                        new RoadPoint(0.5, 64.0, 0.5),
                        new RoadPoint(5.5, 64.0, 5.5),
                        new RoadPoint(10.5, 64.0, 0.5)
                )
        );
    }
}

package com.cookiecraftmods.roadbuilder.networking;

import com.cookiecraftmods.roadbuilder.compat.MinecraftIds;

import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;
import java.util.UUID;

/** A layout is scoped to this construction request, never installed in the server catalog. */
public record ClientRoadIntentC2SPacket(RoadPacket intent, RoadPreset preset) implements RoadPacket {
    public static final int MAX_PAYLOAD_BYTES = 32_767;
    public static final int MAX_LAYOUT_BYTES = 30_000;
    public static final RoadPacketType<ClientRoadIntentC2SPacket> TYPE = RoadPacketType.create(
            MinecraftIds.id("roadbuilder", "client_road_intent_v1"), ClientRoadIntentC2SPacket::new);

    public ClientRoadIntentC2SPacket {
        Objects.requireNonNull(preset, "preset");
        String json = RoadPreset.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, preset)
                .result().orElseThrow(() -> new IllegalArgumentException("invalid road layout")).toString();
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_LAYOUT_BYTES) {
            throw new IllegalArgumentException("client road layout is too large");
        }
        int revision;
        if (intent instanceof StraightRoadIntentC2SPacket road) revision = road.expectedPresetRevision();
        else if (intent instanceof RoadPathIntentC2SPacket road) revision = road.expectedPresetRevision();
        else if (intent instanceof SegmentReplaceIntentC2SPacket road) revision = road.expectedPresetRevision();
        else throw new IllegalArgumentException("unsupported client road intent");
        if (revision != preset.revision()) throw new IllegalArgumentException("layout revision mismatch");
    }

    public ClientRoadIntentC2SPacket(FriendlyByteBuf buffer) { this(decode(buffer)); }
    private ClientRoadIntentC2SPacket(ClientRoadIntentC2SPacket decoded) { this(decoded.intent, decoded.preset); }

    private static ClientRoadIntentC2SPacket decode(FriendlyByteBuf buffer) {
        PacketValidation.requirePayloadSize(buffer, MAX_PAYLOAD_BYTES, "client road intent");
        int kind = buffer.readUnsignedByte();
        byte[] bytes = buffer.readByteArray(2048);
        FriendlyByteBuf nested = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes));
        try {
            RoadPacket intent = switch (kind) {
                case 0 -> new StraightRoadIntentC2SPacket(nested);
                case 1 -> new RoadPathIntentC2SPacket(nested);
                case 2 -> new SegmentReplaceIntentC2SPacket(nested);
                default -> throw new IllegalArgumentException("unknown client road intent");
            };
            RoadPreset preset = RoadPresetPacketCodecs.readPreset(buffer);
            PacketValidation.requireFullyRead(buffer, "client road intent");
            return new ClientRoadIntentC2SPacket(intent, preset);
        } finally { nested.release(); }
    }

    public ResourceLocation presetId() {
        if (intent instanceof StraightRoadIntentC2SPacket road) return road.presetId();
        if (intent instanceof RoadPathIntentC2SPacket road) return road.presetId();
        return ((SegmentReplaceIntentC2SPacket) intent).presetId();
    }

    public UUID requestId() {
        if (intent instanceof StraightRoadIntentC2SPacket road) return road.requestId();
        if (intent instanceof RoadPathIntentC2SPacket road) return road.requestId();
        return ((SegmentReplaceIntentC2SPacket) intent).requestId();
    }

    @Override public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(intent instanceof StraightRoadIntentC2SPacket ? 0 : intent instanceof RoadPathIntentC2SPacket ? 1 : 2);
        FriendlyByteBuf nested = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            intent.write(nested);
            byte[] bytes = new byte[nested.readableBytes()];
            nested.readBytes(bytes);
            buffer.writeByteArray(bytes);
        } finally { nested.release(); }
        RoadPresetPacketCodecs.writePreset(buffer, preset);
    }

    @Override public RoadPacketType<?> packetType() { return TYPE; }
}

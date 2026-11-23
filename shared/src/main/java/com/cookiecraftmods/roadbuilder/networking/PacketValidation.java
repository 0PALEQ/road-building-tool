package com.cookiecraftmods.roadbuilder.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

final class PacketValidation {
    static final int PROTOCOL_VERSION = 1;
    static final int MAX_RESOURCE_ID_CHARACTERS = 128;

    private PacketValidation() {
    }

    static void requireProtocolVersion(int protocolVersion) {
        if (protocolVersion != PROTOCOL_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported protocol version " + protocolVersion + "; expected " + PROTOCOL_VERSION
            );
        }
    }

    static ResourceLocation requireBoundedId(ResourceLocation id, String fieldName) {
        Objects.requireNonNull(id, fieldName);
        if (id.toString().length() > MAX_RESOURCE_ID_CHARACTERS) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + MAX_RESOURCE_ID_CHARACTERS + " characters"
            );
        }
        return id;
    }

    static ResourceLocation readBoundedId(FriendlyByteBuf buffer, String fieldName) {
        String encodedId = buffer.readUtf(MAX_RESOURCE_ID_CHARACTERS);
        ResourceLocation id = ResourceLocation.tryParse(encodedId);
        if (id == null) {
            throw new IllegalArgumentException(fieldName + " is not a valid resource identifier");
        }
        return requireBoundedId(id, fieldName);
    }

    static void writeBoundedId(FriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeUtf(id.toString(), MAX_RESOURCE_ID_CHARACTERS);
    }

    static void requirePayloadSize(FriendlyByteBuf buffer, int maximumBytes, String packetName) {
        Objects.requireNonNull(buffer, "buffer");
        int readableBytes = buffer.readableBytes();
        if (readableBytes > maximumBytes) {
            throw new IllegalArgumentException(
                    packetName + " payload is " + readableBytes + " bytes; maximum is " + maximumBytes
            );
        }
    }

    static void requireFullyRead(FriendlyByteBuf buffer, String packetName) {
        int trailingBytes = buffer.readableBytes();
        if (trailingBytes != 0) {
            throw new IllegalArgumentException(packetName + " payload has " + trailingBytes + " trailing bytes");
        }
    }
}

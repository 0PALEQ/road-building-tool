package com.cookiecraftmods.roadbuilder.networking;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public enum RemovalRequestAction {
    PREVIEW(0),
    EXECUTE(1);

    private static final Map<Integer, RemovalRequestAction> BY_WIRE_ID = createLookup();

    private final int wireId;

    RemovalRequestAction(int wireId) {
        this.wireId = wireId;
    }

    public int wireId() {
        return wireId;
    }

    public static Optional<RemovalRequestAction> fromWireId(int wireId) {
        return Optional.ofNullable(BY_WIRE_ID.get(wireId));
    }

    private static Map<Integer, RemovalRequestAction> createLookup() {
        Map<Integer, RemovalRequestAction> lookup = new HashMap<>();
        for (RemovalRequestAction action : values()) {
            RemovalRequestAction duplicate = lookup.put(action.wireId, action);
            if (duplicate != null) {
                throw new IllegalStateException("duplicate removal action wire id " + action.wireId);
            }
        }
        return Collections.unmodifiableMap(lookup);
    }
}

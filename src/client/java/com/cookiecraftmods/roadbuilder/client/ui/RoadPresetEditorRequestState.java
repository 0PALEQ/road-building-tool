package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.preset.RoadPreset;

import java.util.Objects;
import java.util.UUID;

final class RoadPresetEditorRequestState {
    private UUID contentRequestId;
    private UUID saveRequestId;
    private int contentTicks;
    private int saveTicks;
    private RoadPreset pendingCandidate;
    private boolean deletePending;

    UUID beginContentRequest() {
        if (contentRequestId != null) {
            throw new IllegalStateException("content request already pending");
        }
        contentRequestId = UUID.randomUUID();
        contentTicks = 0;
        return contentRequestId;
    }

    UUID contentRequestId() {
        return contentRequestId;
    }

    boolean contentPending() {
        return contentRequestId != null;
    }

    boolean tickContent(int timeoutTicks) {
        requirePositiveTimeout(timeoutTicks);
        if (!contentPending()) {
            return false;
        }
        return ++contentTicks >= timeoutTicks;
    }

    void finishContentRequest() {
        contentRequestId = null;
        contentTicks = 0;
    }

    UUID beginSave(RoadPreset candidate) {
        return beginSave(candidate, false);
    }

    UUID beginDelete() {
        return beginSave(null, true);
    }

    private UUID beginSave(RoadPreset candidate, boolean deleting) {
        if (saveRequestId != null) {
            throw new IllegalStateException("save request already pending");
        }
        if (!deleting) {
            Objects.requireNonNull(candidate, "candidate");
        }
        saveRequestId = UUID.randomUUID();
        saveTicks = 0;
        pendingCandidate = candidate;
        deletePending = deleting;
        return saveRequestId;
    }

    UUID saveRequestId() {
        return saveRequestId;
    }

    boolean savePending() {
        return saveRequestId != null;
    }

    boolean tickSave(int timeoutTicks) {
        requirePositiveTimeout(timeoutTicks);
        if (!savePending()) {
            return false;
        }
        return ++saveTicks >= timeoutTicks;
    }

    SaveCompletion finishSave() {
        SaveCompletion completion = new SaveCompletion(
                pendingCandidate,
                deletePending
        );
        saveRequestId = null;
        saveTicks = 0;
        pendingCandidate = null;
        deletePending = false;
        return completion;
    }

    void clear() {
        finishContentRequest();
        finishSave();
    }

    private static void requirePositiveTimeout(int timeoutTicks) {
        if (timeoutTicks <= 0) {
            throw new IllegalArgumentException("timeoutTicks must be positive");
        }
    }

    record SaveCompletion(RoadPreset candidate, boolean deleting) {
    }
}

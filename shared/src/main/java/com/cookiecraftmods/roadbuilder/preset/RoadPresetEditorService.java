package com.cookiecraftmods.roadbuilder.preset;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

public final class RoadPresetEditorService {
    private final RoadPresetRepository repository;
    private final Predicate<UUID> authorizer;
    private final Predicate<ResourceLocation> knownBlock;

    public RoadPresetEditorService(
            RoadPresetRepository repository,
            Predicate<UUID> authorizer
    ) {
        this(repository, authorizer, ignored -> true);
    }

    public RoadPresetEditorService(
            RoadPresetRepository repository,
            Predicate<UUID> authorizer,
            Predicate<ResourceLocation> knownBlock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.knownBlock = Objects.requireNonNull(knownBlock, "knownBlock");
    }

    public EditResult save(
            UUID actorId,
            ResourceLocation id,
            int expectedRevision,
            RoadPreset candidate
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(candidate, "candidate");
        if (!authorizer.test(actorId)) {
            return new EditResult.Rejected(EditFailure.NOT_AUTHORIZED);
        }
        if (expectedRevision < 0) {
            return new EditResult.Rejected(EditFailure.INVALID_REVISION);
        }
        RoadPreset existing = repository.find(id).orElse(null);
        if (existing != null && !repository.isEditable(id)) {
            return new EditResult.Rejected(EditFailure.NOT_AUTHORIZED);
        }
        if ((existing == null && expectedRevision != 0)
                || (existing != null
                        && existing.revision() != expectedRevision)) {
            return new EditResult.Rejected(EditFailure.STALE_REVISION);
        }
        int requiredRevision = expectedRevision + 1;
        if (candidate.revision() != requiredRevision) {
            return new EditResult.Rejected(EditFailure.INVALID_REVISION);
        }
        if (!allBlocksKnown(candidate)) {
            return new EditResult.Rejected(EditFailure.UNKNOWN_BLOCK);
        }
        repository.upsertServerAuthored(id, candidate);
        return new EditResult.Accepted(
                repository.catalog(),
                RoadPresetCatalog.fingerprint(candidate)
        );
    }

    public EditResult duplicate(
            UUID actorId,
            ResourceLocation sourceId,
            ResourceLocation targetId
    ) {
        Objects.requireNonNull(sourceId, "sourceId");
        RoadPreset source = repository.find(sourceId).orElse(null);
        if (source == null) {
            return new EditResult.Rejected(EditFailure.INVALID_PRESET);
        }
        if (repository.find(targetId).isPresent()) {
            return new EditResult.Rejected(EditFailure.STALE_REVISION);
        }
        RoadPreset copy = new RoadPreset(
                RoadPreset.CURRENT_SCHEMA_VERSION,
                1,
                source.surfaceWidth(),
                source.surfaceBlock(),
                source.lanes(),
                source.surfaceBands(),
                source.display(),
                source.classification(),
                source.intersectionStyle(),
                source.materialPalette()
        );
        return save(actorId, targetId, 0, copy);
    }

    public EditResult delete(
            UUID actorId,
            ResourceLocation id,
            int expectedRevision
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(id, "id");
        if (!authorizer.test(actorId)) {
            return new EditResult.Rejected(EditFailure.NOT_AUTHORIZED);
        }
        RoadPreset existing = repository.find(id).orElse(null);
        if (existing == null || !repository.isEditable(id)) {
            return new EditResult.Rejected(EditFailure.NOT_AUTHORIZED);
        }
        if (existing.revision() != expectedRevision) {
            return new EditResult.Rejected(EditFailure.STALE_REVISION);
        }
        repository.removeServerAuthored(id);
        return new EditResult.Accepted(repository.catalog(), "");
    }

    private boolean allBlocksKnown(RoadPreset preset) {
        if (!knownBlock.test(preset.surfaceBlock())
                || !knownBlock.test(
                        preset.intersectionStyle().pavementBlock()
                )) {
            return false;
        }
        if (java.util.Arrays.stream(RoadSurfaceRole.values())
                .flatMap(role -> preset.materialPalette()
                        .blockSetFor(role).stream())
                .anyMatch(block -> !knownBlock.test(block))) {
            return false;
        }
        if (preset.surfaceBands().stream()
                .flatMap(band -> java.util.stream.Stream.concat(
                        band.blockSet().stream(), band.underlayBlockSet().stream()))
                .anyMatch(block -> !knownBlock.test(block))) {
            return false;
        }
        return java.util.stream.Stream.of(
                        preset.intersectionStyle().markingBlock(),
                        preset.intersectionStyle().crosswalkBlock(),
                        preset.intersectionStyle().stopLineBlock()
                )
                .flatMap(java.util.Optional::stream)
                .allMatch(knownBlock);
    }

    public sealed interface EditResult {
        record Accepted(
                RoadPresetCatalog catalog,
                String fingerprint
        ) implements EditResult {
            public Accepted {
                Objects.requireNonNull(catalog, "catalog");
                Objects.requireNonNull(fingerprint, "fingerprint");
            }
        }

        record Rejected(EditFailure failure) implements EditResult {
            public Rejected {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    public enum EditFailure {
        NOT_AUTHORIZED,
        STALE_REVISION,
        INVALID_REVISION,
        INVALID_PRESET,
        UNKNOWN_BLOCK,
        PAYLOAD_LIMIT
    }
}

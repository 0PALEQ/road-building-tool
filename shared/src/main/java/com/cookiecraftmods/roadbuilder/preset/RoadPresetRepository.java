package com.cookiecraftmods.roadbuilder.preset;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

public final class RoadPresetRepository implements RoadPresetLookup {
    private final AtomicReference<Map<ResourceLocation, RoadPreset>> resources =
            new AtomicReference<>(Map.of());
    private final AtomicReference<Map<ResourceLocation, RoadPreset>> authored =
            new AtomicReference<>(Map.of());
    private final AtomicReference<
            Map<ResourceLocation, Map<ResourceLocation, RoadPreset>>>
            providers = new AtomicReference<>(Map.of());
    private final AtomicLong catalogRevision = new AtomicLong();

    @Override
    public Optional<RoadPreset> find(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        RoadPreset authoredPreset = authored.get().get(id);
        if (authoredPreset != null) {
            return Optional.of(authoredPreset);
        }
        RoadPreset resourcePreset = resources.get().get(id);
        if (resourcePreset != null) {
            return Optional.of(resourcePreset);
        }
        return providers.get().values().stream()
                .map(presets -> presets.get(id))
                .filter(Objects::nonNull)
                .findFirst();
    }

    public Optional<RoadPreset> find(ResourceLocation id, int revision) {
        return find(id).filter(preset -> preset.revision() == revision);
    }

    public Map<ResourceLocation, RoadPreset> snapshot() {
        TreeMap<ResourceLocation, RoadPreset> merged = new TreeMap<>(
                resources.get()
        );
        providers.get().values().forEach(providerPresets ->
                providerPresets.forEach(merged::putIfAbsent));
        merged.putAll(authored.get());
        return Collections.unmodifiableMap(new LinkedHashMap<>(merged));
    }

    public int size() {
        return snapshot().size();
    }

    synchronized void replaceAll(Map<ResourceLocation, RoadPreset> replacements) {
        Objects.requireNonNull(replacements, "replacements");
        TreeMap<ResourceLocation, RoadPreset> ordered = new TreeMap<>();
        replacements.forEach((id, preset) -> ordered.put(
                Objects.requireNonNull(id, "preset id"),
                Objects.requireNonNull(preset, "preset for " + id)
        ));
        resources.set(Collections.unmodifiableMap(new LinkedHashMap<>(ordered)));
        catalogRevision.incrementAndGet();
    }

    public synchronized void upsertServerAuthored(
            ResourceLocation id,
            RoadPreset preset
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(preset, "preset");
        TreeMap<ResourceLocation, RoadPreset> replacement = new TreeMap<>(
                authored.get()
        );
        replacement.put(id, preset);
        authored.set(Collections.unmodifiableMap(
                new LinkedHashMap<>(replacement)
        ));
        catalogRevision.incrementAndGet();
    }

    public synchronized boolean removeServerAuthored(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        if (!authored.get().containsKey(id)) {
            return false;
        }
        TreeMap<ResourceLocation, RoadPreset> replacement = new TreeMap<>(
                authored.get()
        );
        replacement.remove(id);
        authored.set(Collections.unmodifiableMap(
                new LinkedHashMap<>(replacement)
        ));
        catalogRevision.incrementAndGet();
        return true;
    }

    public synchronized void replaceServerAuthored(
            Map<ResourceLocation, RoadPreset> replacements
    ) {
        Objects.requireNonNull(replacements, "replacements");
        TreeMap<ResourceLocation, RoadPreset> ordered = new TreeMap<>();
        replacements.forEach((id, preset) -> ordered.put(
                Objects.requireNonNull(id, "authored preset id"),
                Objects.requireNonNull(preset, "authored preset")
        ));
        authored.set(Collections.unmodifiableMap(
                new LinkedHashMap<>(ordered)
        ));
        catalogRevision.incrementAndGet();
    }

    public synchronized void registerProvider(RoadPresetProvider provider) {
        Objects.requireNonNull(provider, "provider");
        ResourceLocation providerId = Objects.requireNonNull(
                provider.providerId(),
                "providerId"
        );
        TreeMap<ResourceLocation, RoadPreset> supplied = new TreeMap<>();
        Objects.requireNonNull(provider.presets(), "provider presets")
                .forEach((id, preset) -> supplied.put(
                        Objects.requireNonNull(id, "provider preset id"),
                        Objects.requireNonNull(
                                preset,
                                "provider preset for " + id
                        )
                ));
        TreeMap<ResourceLocation, Map<ResourceLocation, RoadPreset>>
                replacement = new TreeMap<>(providers.get());
        if (replacement.containsKey(providerId)) {
            throw new IllegalArgumentException(
                    "road preset provider is already registered: "
                            + providerId
            );
        }
        replacement.put(
                providerId,
                Collections.unmodifiableMap(new LinkedHashMap<>(supplied))
        );
        providers.set(Collections.unmodifiableMap(
                new LinkedHashMap<>(replacement)
        ));
        catalogRevision.incrementAndGet();
    }

    public synchronized boolean unregisterProvider(
            ResourceLocation providerId
    ) {
        Objects.requireNonNull(providerId, "providerId");
        if (!providers.get().containsKey(providerId)) {
            return false;
        }
        TreeMap<ResourceLocation, Map<ResourceLocation, RoadPreset>>
                replacement = new TreeMap<>(providers.get());
        replacement.remove(providerId);
        providers.set(Collections.unmodifiableMap(
                new LinkedHashMap<>(replacement)
        ));
        catalogRevision.incrementAndGet();
        return true;
    }

    public boolean isEditable(ResourceLocation id) {
        return authored.get().containsKey(Objects.requireNonNull(id, "id"));
    }

    public Map<ResourceLocation, RoadPreset> authoredSnapshot() {
        return authored.get();
    }

    public RoadPresetCatalog catalog() {
        return RoadPresetCatalog.from(
                catalogRevision.get(),
                snapshot(),
                Set.copyOf(authored.get().keySet())
        );
    }
}

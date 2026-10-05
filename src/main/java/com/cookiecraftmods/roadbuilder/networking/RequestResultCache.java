package com.cookiecraftmods.roadbuilder.networking;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

final class RequestResultCache<T> {
    static final int DEFAULT_MAX_RESULTS_PER_PLAYER = 256;

    private final int maximumResultsPerPlayer;
    private final Function<? super T, UUID> requestId;
    private final Map<UUID, PlayerResults<T>> resultsByPlayer = new LinkedHashMap<>();

    RequestResultCache(Function<? super T, UUID> requestId) {
        this(DEFAULT_MAX_RESULTS_PER_PLAYER, requestId);
    }

    RequestResultCache(
            int maximumResultsPerPlayer,
            Function<? super T, UUID> requestId
    ) {
        if (maximumResultsPerPlayer <= 0) {
            throw new IllegalArgumentException("maximumResultsPerPlayer must be greater than zero");
        }
        this.maximumResultsPerPlayer = maximumResultsPerPlayer;
        this.requestId = Objects.requireNonNull(requestId, "requestId");
    }

    synchronized Optional<T> find(UUID playerId, UUID requestId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(requestId, "requestId");
        PlayerResults<T> playerResults = resultsByPlayer.get(playerId);
        return playerResults == null
                ? Optional.empty()
                : Optional.ofNullable(playerResults.get(requestId));
    }

    synchronized void remember(UUID playerId, T result) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(result, "result");
        resultsByPlayer.computeIfAbsent(
                playerId,
                ignored -> new PlayerResults<>(maximumResultsPerPlayer)
        ).put(
                Objects.requireNonNull(requestId.apply(result), "result requestId"),
                result
        );
    }

    synchronized void clear(UUID playerId) {
        resultsByPlayer.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    private static final class PlayerResults<T> extends LinkedHashMap<UUID, T> {
        private final int maximumSize;

        private PlayerResults(int maximumSize) {
            super(maximumSize + 1, 0.75f, true);
            this.maximumSize = maximumSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, T> eldest) {
            return size() > maximumSize;
        }
    }
}

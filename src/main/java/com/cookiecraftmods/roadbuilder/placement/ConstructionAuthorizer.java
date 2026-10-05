package com.cookiecraftmods.roadbuilder.placement;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.Predicate;

public final class ConstructionAuthorizer {
    private final Predicate<ServerPlayer> playerPermissionCheck;
    private final ConstructionPermissionPolicy footprintPermissionPolicy;

    public ConstructionAuthorizer(Predicate<ServerPlayer> playerPermissionCheck) {
        this(playerPermissionCheck, (player, level, footprint) -> true);
    }

    public ConstructionAuthorizer(
            Predicate<ServerPlayer> playerPermissionCheck,
            ConstructionPermissionPolicy footprintPermissionPolicy
    ) {
        this.playerPermissionCheck = Objects.requireNonNull(playerPermissionCheck, "playerPermissionCheck");
        this.footprintPermissionPolicy = Objects.requireNonNull(
                footprintPermissionPolicy,
                "footprintPermissionPolicy"
        );
    }

    public static ConstructionAuthorizer creativeOnly() {
        return new ConstructionAuthorizer(player -> true);
    }

    public ConstructionValidationResult validatePlayer(ServerPlayer player) {
        Objects.requireNonNull(player, "player");

        if (!player.isCreative()) {
            return rejected(ConstructionValidationCode.NOT_CREATIVE);
        }
        if (!playerPermissionCheck.test(player)) {
            return rejected(ConstructionValidationCode.NO_PERMISSION);
        }
        return ConstructionValidationResult.allowed();
    }

    public ConstructionValidationResult validate(
            ServerPlayer player,
            ConstructionFootprint footprint,
            ConstructionLimits limits
    ) {
        ConstructionValidationResult common = validateCommon(
                player,
                footprint,
                limits
        );
        if (!common.isAllowed()) {
            return common;
        }
        /*
         * Authorized city-building is not tied to the player's physical
         * avatar or the currently loaded chunk ring. Incremental jobs provide
         * the tick-time boundary; ServerLevel loads the chunks they mutate.
         */
        return ConstructionValidationResult.allowed();
    }

        public ConstructionValidationResult validateExistingFootprint(
            ServerPlayer player,
            ConstructionFootprint footprint,
            ConstructionLimits limits
    ) {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(footprint, "footprint");
        ConstructionValidationResult playerValidation =
                validatePlayer(player);
        if (!playerValidation.isAllowed()) {
            return playerValidation;
        }
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(footprint.dimension())) {
            return rejected(ConstructionValidationCode.WRONG_DIMENSION);
        }
        if (!footprintPermissionPolicy.mayModify(
                player,
                level,
                footprint
        )) {
            return rejected(ConstructionValidationCode.NO_PERMISSION);
        }
        ConstructionBounds bounds = footprint.bounds();
        if (!level.isInWorldBounds(bounds.minimum())
                || !level.isInWorldBounds(bounds.maximum())) {
            return rejected(ConstructionValidationCode.OUTSIDE_WORLD);
        }
        if (!level.getWorldBorder().isWithinBounds(bounds.minimum())
                || !level.getWorldBorder().isWithinBounds(bounds.maximum())) {
            return rejected(ConstructionValidationCode.OUTSIDE_WORLD_BORDER);
        }
        return ConstructionValidationResult.allowed();
    }

    private ConstructionValidationResult validateCommon(
            ServerPlayer player,
            ConstructionFootprint footprint,
            ConstructionLimits limits
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(footprint, "footprint");
        Objects.requireNonNull(limits, "limits");

        ConstructionValidationResult playerValidation = validatePlayer(player);
        if (!playerValidation.isAllowed()) {
            return playerValidation;
        }

        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(footprint.dimension())) {
            return rejected(ConstructionValidationCode.WRONG_DIMENSION);
        }
        if (!footprintPermissionPolicy.mayModify(player, level, footprint)) {
            return rejected(ConstructionValidationCode.NO_PERMISSION);
        }
        if (footprint.affectedBlockCount() > limits.maximumAffectedBlocks()) {
            return rejected(ConstructionValidationCode.BLOCK_LIMIT_EXCEEDED);
        }

        ConstructionBounds bounds = footprint.bounds();
        if (footprint.touchedChunks().size() > limits.maximumTouchedChunks()) {
            return rejected(ConstructionValidationCode.CHUNK_LIMIT_EXCEEDED);
        }
        if (!level.isInWorldBounds(bounds.minimum()) || !level.isInWorldBounds(bounds.maximum())) {
            return rejected(ConstructionValidationCode.OUTSIDE_WORLD);
        }
        if (!level.getWorldBorder().isWithinBounds(bounds.minimum())
                || !level.getWorldBorder().isWithinBounds(bounds.maximum())) {
            return rejected(ConstructionValidationCode.OUTSIDE_WORLD_BORDER);
        }

        return ConstructionValidationResult.allowed();
    }

    private static ConstructionValidationResult rejected(ConstructionValidationCode code) {
        return ConstructionValidationResult.rejected(code);
    }
}

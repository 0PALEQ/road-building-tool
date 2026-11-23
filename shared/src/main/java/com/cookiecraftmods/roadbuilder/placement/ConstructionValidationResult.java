package com.cookiecraftmods.roadbuilder.placement;

import java.util.Objects;

public record ConstructionValidationResult(ConstructionValidationCode code) {
    private static final ConstructionValidationResult ALLOWED =
            new ConstructionValidationResult(ConstructionValidationCode.ALLOWED);

    public ConstructionValidationResult {
        Objects.requireNonNull(code, "code");
    }

    public static ConstructionValidationResult allowed() {
        return ALLOWED;
    }

    public static ConstructionValidationResult rejected(ConstructionValidationCode code) {
        if (code == ConstructionValidationCode.ALLOWED) {
            throw new IllegalArgumentException("Use allowed() for an allowed result");
        }
        return new ConstructionValidationResult(code);
    }

    public boolean isAllowed() {
        return code == ConstructionValidationCode.ALLOWED;
    }
}

package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.validation.DirectoryValidator;

import java.nio.file.Path;

final class RoadPackFormatTestSupport {
    private RoadPackFormatTestSupport() {
    }

    static FolderRepositorySource folderSource(Path directory, PackType type) {
        return new FolderRepositorySource(directory, type, PackSource.DEFAULT, new DirectoryValidator(path -> false));
    }
}

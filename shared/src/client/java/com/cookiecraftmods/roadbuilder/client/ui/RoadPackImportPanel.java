package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.client.networking.RoadBuilderClientNetworking;
import com.cookiecraftmods.roadbuilder.networking.RoadPackImportC2SPacket;
import com.cookiecraftmods.roadbuilder.networking.RoadPackImportResultS2CPacket;
import com.cookiecraftmods.roadbuilder.preset.RoadPackArchive;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;

import static com.cookiecraftmods.roadbuilder.client.ui.UIStyle.*;

final class RoadPackImportPanel {
    private static final String KEY = "screen.roadbuilder.settings.server.";
    private static final int REQUEST_TIMEOUT_TICKS = 200;
    private static final int PADDING = 8;
    private static final int GAP = 4;

    private ClientPacketListener connection;
    private UUID pendingRequest;
    private int requestTicks;
    private boolean allowed;
    private Path selectedPack;
    private Component status = Component.empty();
    private boolean error;
    private UiRectangle body;
    private RoadTextButton chooseButton;
    private RoadTextButton importButton;

    void open(ClientPacketListener currentConnection) {
        updateConnection(currentConnection);
        if (!allowed && pendingRequest == null) {
            send(new byte[0], "checking");
        }
    }

    void addControls(UiRectangle bounds, Consumer<RoadTextButton> addWidget) {
        body = bounds;
        int x = body.x() + PADDING;
        int y = body.bottom() - 28;
        int available = Math.max(2, body.width() - PADDING * 2 - GAP);
        int chooseWidth = available / 2;
        chooseButton = new RoadTextButton(new UiRectangle(x, y, chooseWidth, 20),
                text("choose"), RoadTextButton.Style.NORMAL, this::choosePack);
        importButton = new RoadTextButton(
                new UiRectangle(x + chooseWidth + GAP, y, available - chooseWidth, 20),
                text("import"), RoadTextButton.Style.PRIMARY, this::importPack);
        addWidget.accept(chooseButton);
        addWidget.accept(importButton);
        refreshButtons();
    }

    private void choosePack() {
        String file;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var filter = stack.mallocPointer(1);
            filter.put(stack.UTF8("*.zip")).flip();
            file = TinyFileDialogs.tinyfd_openFileDialog(
                    text("choose").getString(), "", filter, "ZIP", false);
        }
        if (file != null) {
            selectedPack = Path.of(file);
            status = Component.empty();
            error = false;
            refreshButtons();
        }
    }

    private void importPack() {
        byte[] archive;
        try (var stream = Files.newInputStream(selectedPack)) {
            archive = stream.readNBytes(RoadPackArchive.MAXIMUM_ARCHIVE_BYTES + 1);
        } catch (IOException exception) {
            showStatus("read_failed", true);
            return;
        }
        if (archive.length > RoadPackArchive.MAXIMUM_ARCHIVE_BYTES) {
            showStatus("too_large", true, RoadPackArchive.MAXIMUM_ARCHIVE_BYTES / 1024);
            return;
        }
        try {
            RoadPackArchive.read(archive);
        } catch (IOException | RuntimeException invalid) {
            showStatus("invalid_pack", true);
            return;
        }
        send(archive, "importing");
    }

    private void send(byte[] archive, String progressKey) {
        UUID request = UUID.randomUUID();
        if (connection == null || !RoadBuilderClientNetworking.sendRoadPackImport(
                RoadPackImportC2SPacket.current(request, archive))) {
            allowed = false;
            showStatus("unavailable", true);
        } else {
            pendingRequest = request;
            requestTicks = 0;
            showStatus(progressKey, false);
        }
        refreshButtons();
    }

    void tick(ClientPacketListener currentConnection) {
        updateConnection(currentConnection);
        if (pendingRequest == null) {
            return;
        }
        var result = RoadBuilderClientNetworking.takeRoadPackImportResult(pendingRequest);
        if (result.isPresent()) {
            pendingRequest = null;
            receive(result.orElseThrow());
            refreshButtons();
        } else if (++requestTicks >= REQUEST_TIMEOUT_TICKS) {
            pendingRequest = null;
            showStatus("timed_out", true);
            refreshButtons();
        }
    }

    private void receive(RoadPackImportResultS2CPacket result) {
        switch (result.code()) {
            case AVAILABLE -> {
                allowed = true;
                status = Component.empty();
                error = false;
            }
            case IMPORTED -> {
                selectedPack = null;
                showStatus("imported", false, result.imported(), result.skipped());
            }
            case NOT_AUTHORIZED -> {
                allowed = false;
                showStatus("not_authorized", true);
            }
            case INVALID_PACK -> showStatus("invalid_pack", true);
            case UNKNOWN_BLOCK -> showStatus("unknown_block", true);
            case CATALOG_FULL -> showStatus("catalog_full", true);
        }
    }

    private void updateConnection(ClientPacketListener currentConnection) {
        if (connection != currentConnection) {
            pendingRequest = null;
            connection = currentConnection;
            allowed = false;
            selectedPack = null;
            showStatus("unavailable", true);
            refreshButtons();
        }
    }

    private void refreshButtons() {
        if (chooseButton == null) {
            return;
        }
        chooseButton.active = allowed && pendingRequest == null;
        importButton.active = chooseButton.active && selectedPack != null;
        importButton.setTooltip(selectedPack == null ? null
                : Tooltip.create(Component.literal(selectedPack.getFileName().toString())));
    }

    private void showStatus(String key, boolean error, Object... arguments) {
        status = text(key, arguments);
        this.error = error;
    }

    void render(GuiGraphics graphics, Font font) {
        int x = body.x() + PADDING;
        int width = Math.max(1, body.width() - PADDING * 2);
        graphics.drawString(font, text("title"), x, body.y() + 9, TEXT_PRIMARY, false);
        int y = body.y() + 25;
        for (var line : font.split(text("description"), width)) {
            if (y + font.lineHeight > body.bottom() - 44) {
                break;
            }
            graphics.drawString(font, line, x, y, TEXT_SECONDARY, false);
            y += font.lineHeight;
        }
        if (selectedPack != null) {
            String filename = selectedPack.getFileName().toString();
            graphics.drawString(font, font.plainSubstrByWidth(filename, width),
                    x, body.bottom() - 42, TEXT_SUBTITLE, false);
        }
    }

    void renderStatus(GuiGraphics graphics, Font font, int y, int maximumLines) {
        int lineCount = 0;
        for (var line : font.split(status, Math.max(1, body.width()))) {
            if (++lineCount > maximumLines) {
                break;
            }
            graphics.drawString(font, line, body.x(), y, error ? TEXT_ERROR : TEXT_PRIMARY, false);
            y += font.lineHeight;
        }
    }

    private static Component text(String suffix, Object... arguments) {
        return Component.translatable(KEY + suffix, arguments);
    }
}

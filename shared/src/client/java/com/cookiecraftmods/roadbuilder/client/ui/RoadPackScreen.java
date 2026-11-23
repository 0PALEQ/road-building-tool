package com.cookiecraftmods.roadbuilder.client.ui;

import com.cookiecraftmods.roadbuilder.client.networking.RoadBuilderClientNetworking;
import com.cookiecraftmods.roadbuilder.preset.RoadPreset;
import com.cookiecraftmods.roadbuilder.preset.RoadPresetCatalogEntry;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.cookiecraftmods.roadbuilder.client.ui.UIStyle.*;

public final class RoadPackScreen extends RoadBuilderScreen {
    private static final String KEY = "screen.roadbuilder.road_pack.";
    private static final int ROW_HEIGHT = 30;

    private final Screen parent;
    private final Set<ResourceLocation> selected = new LinkedHashSet<>();
    private EditBox nameBox;
    private RoadList roadList;
    private Button exportButton;
    private Component status = Component.empty();
    private boolean error;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;

    public RoadPackScreen(Screen parent) {
        super(Component.translatable(KEY + "title"));
        this.parent = Objects.requireNonNull(parent, "parent");
    }

    @Override
    protected void init() {
        panelWidth = Math.min(560, Math.max(250, width - 24));
        panelHeight = Math.min(460, Math.max(190, height - 20));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        nameBox = new EditBox(
                font,
                panelX + 8,
                panelY + 28,
                panelWidth - 16,
                20,
                Component.translatable(KEY + "name")
        );
        nameBox.setMaxLength(64);
        nameBox.setValue("My Road Pack");
        nameBox.setHint(Component.translatable(KEY + "name_hint"));
        nameBox.setResponder(ignored -> refreshExportButton());
        addRenderableWidget(nameBox);

        roadList = new RoadList(
                minecraft,
                width,
                height,
                panelY + 68,
                panelY + panelHeight - 34,
                ROW_HEIGHT,
                panelWidth - 16
        );


        roadList.replaceCatalog(RoadBuilderClientNetworking
                .presetCatalog().entries());
        addRenderableWidget(roadList);

        int buttonY = panelY + panelHeight - 26;
        exportButton = Button.builder(
                        Component.translatable(KEY + "export"),
                        ignored -> exportPack()
                ).bounds(panelX + panelWidth - 194, buttonY, 108, 20)
                .build();
        addRenderableWidget(exportButton);
        addRenderableWidget(Button.builder(
                        CommonComponents.GUI_BACK,
                        ignored -> onClose()
                ).bounds(panelX + panelWidth - 80, buttonY, 72, 20)
                .build());
        refreshExportButton();
        setInitialFocus(nameBox);
    }

    @Override
    public void tick() {
        tickEditBox(nameBox);
        selected.forEach(
                RoadBuilderClientNetworking::requestPresetContentIfMissing
        );
        refreshExportButton();
    }

    private void toggle(ResourceLocation id) {
        if (!selected.add(id)) {
            selected.remove(id);
        }
        status = Component.empty();
        error = false;
        RoadBuilderClientNetworking.requestPresetContentIfMissing(id);
        refreshExportButton();
    }

    private void refreshExportButton() {
        if (exportButton == null || nameBox == null) {
            return;
        }
        exportButton.active = !selected.isEmpty()
                && !nameBox.getValue().strip().isEmpty()
                && selected.stream().allMatch(id ->
                        RoadBuilderClientNetworking.presetContent(id)
                                .isPresent());
    }

    private void exportPack() {
        Map<ResourceLocation, RoadPreset> roads = new LinkedHashMap<>();
        for (ResourceLocation id : selected) {
            RoadPreset preset = RoadBuilderClientNetworking.presetContent(id)
                    .orElse(null);
            if (preset == null) {
                status = Component.translatable(KEY + "loading");
                error = false;
                return;
            }
            roads.put(id, preset);
        }
        try {
            Path destination = RoadPackExporter.export(
                    minecraft.gameDirectory.toPath()
                            .resolve("resourcepacks"),
                    nameBox.getValue(),
                    roads
            );
            status = Component.translatable(
                    KEY + "exported",
                    destination.toAbsolutePath()
            );
            error = false;
            Util.getPlatform().openFile(destination.getParent().toFile());
        } catch (IOException | IllegalArgumentException exception) {
            status = Component.literal(Objects.requireNonNullElse(
                    exception.getMessage(),
                    "Road pack export failed"
            ));
            error = true;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        renderRoadBackground(graphics);
        graphics.fill(panelX, panelY, panelX + panelWidth,
                panelY + panelHeight, BACKGROUND_PANEL);
        graphics.renderOutline(panelX, panelY, panelWidth, panelHeight,
                BORDER_PANEL);
        graphics.drawCenteredString(font, title,
                panelX + panelWidth / 2, panelY + 9, TEXT_PRIMARY);
        graphics.drawString(font, Component.translatable(KEY + "install_hint"),
                panelX + 8, panelY + 54, TEXT_SECONDARY, false);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!status.getString().isEmpty()) {
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(
                            status.getString(),
                            Math.max(1, panelWidth - 214)
                    ),
                    panelX + 9,
                    panelY + panelHeight - 20,
                    error ? HIGHLIGHT_STRONG : TEXT_SECONDARY,
                    false
            );
        }
    }

    private final class RoadList extends RoadObjectSelectionList<RoadEntry> {
        private final int rowWidth;

        private RoadList(
                Minecraft minecraft,
                int width,
                int height,
                int top,
                int bottom,
                int itemHeight,
                int rowWidth
        ) {
            super(minecraft, width, height, top, bottom, itemHeight);
            this.rowWidth = rowWidth;
        }

        private void replaceCatalog(List<RoadPresetCatalogEntry> entries) {
            replaceEntries(entries.stream().map(RoadEntry::new).toList());
        }

        @Override
        public int getRowWidth() {
            return rowWidth;
        }

        @Override
        protected int getScrollbarPosition() {
            return getRowRight() + 5;
        }
    }

    private final class RoadEntry extends ObjectSelectionList.Entry<RoadEntry> {
        private final RoadPresetCatalogEntry entry;

        private RoadEntry(RoadPresetCatalogEntry entry) {
            this.entry = entry;
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int index,
                int top,
                int left,
                int width,
                int height,
                int mouseX,
                int mouseY,
                boolean hovered,
                float partialTick
        ) {
            boolean checked = selected.contains(entry.id());
            if (hovered || checked) {
                graphics.fill(left, top, left + width, top + height,
                        checked ? CONTROL_SELECTED : OVERLAY_ROW_HOVERED);
            }
            int boxX = left + 5;
            int boxY = top + 8;
            graphics.renderOutline(boxX, boxY, 11, 11,
                    checked ? HIGHLIGHT : BORDER_HOVERED);
            if (checked) {
                graphics.fill(boxX + 3, boxY + 3, boxX + 8, boxY + 8,
                        HIGHLIGHT);
            }
            int textX = left + 22;
            graphics.drawString(font, entry.display().fallbackName(),
                    textX, top + 4, TEXT_PRIMARY, false);
            graphics.drawString(
                    font,
                    entry.display().category() + " · " + entry.id(),
                    textX,
                    top + 16,
                    TEXT_SECONDARY,
                    false
            );
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            toggle(entry.id());
            return true;
        }

        @Override
        public Component getNarration() {
            return CommonComponents.joinForNarration(
                    Component.literal(selected.contains(entry.id())
                            ? "Selected"
                            : "Not selected"),
                    Component.literal(entry.display().fallbackName())
            );
        }
    }
}

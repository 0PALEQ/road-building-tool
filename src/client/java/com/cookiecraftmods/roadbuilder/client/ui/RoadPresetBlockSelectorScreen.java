package com.cookiecraftmods.roadbuilder.client.ui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

import static com.cookiecraftmods.roadbuilder.client.ui.UIStyle.*;

public final class RoadPresetBlockSelectorScreen extends Screen {
    private static final String KEY = "screen.roadbuilder.block_selector.";
    private static final int ROW_HEIGHT = 26;

    private final Screen parent;
    private final Consumer<ResourceLocation> onSelected;
    private final List<BlockOption> allOptions;
    private ResourceLocation preferredId;
    private String query = "";
    private EditBox searchBox;
    private BlockList blockList;
    private Button selectButton;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listTop;
    private int listBottom;

    public RoadPresetBlockSelectorScreen(
            Screen parent,
            ResourceLocation selectedId,
            Consumer<ResourceLocation> onSelected
    ) {
        super(Component.translatable(KEY + "title"));
        this.parent = Objects.requireNonNull(parent, "parent");
        preferredId = Objects.requireNonNull(selectedId, "selectedId");
        this.onSelected = Objects.requireNonNull(onSelected, "onSelected");
        allOptions = registryOptions();
    }

        public static void open(
            Screen parent,
            ResourceLocation selectedId,
            Consumer<ResourceLocation> onSelected
    ) {
        Minecraft.getInstance().setScreen(
                new RoadPresetBlockSelectorScreen(
                        parent,
                        selectedId,
                        onSelected
                )
        );
    }

    @Override
    protected void init() {
        panelWidth = Math.min(520, Math.max(220, width - 24));
        panelHeight = Math.min(430, Math.max(180, height - 20));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int searchY = panelY + 28;
        listTop = searchY + 25;
        listBottom = panelY + panelHeight - 32;
        searchBox = new EditBox(
                font,
                panelX + 8,
                searchY,
                panelWidth - 16,
                20,
                Component.translatable(KEY + "search")
        );
        searchBox.setMaxLength(128);
        searchBox.setHint(Component.translatable(KEY + "search_hint"));
        searchBox.setValue(query);
        searchBox.setResponder(value -> {
            query = value;
            applyFilter();
        });
        addRenderableWidget(searchBox);

        blockList = new BlockList(
                minecraft,
                width,
                height,
                listTop,
                listBottom,
                ROW_HEIGHT,
                panelWidth - 16
        );
        blockList.setRenderBackground(false);
        blockList.setRenderTopAndBottom(false);
        addRenderableWidget(blockList);
        applyFilter();

        int buttonY = panelY + panelHeight - 26;
        selectButton = Button.builder(
                        Component.translatable(KEY + "use_selected"),
                        button -> acceptSelection()
                )
                .bounds(panelX + panelWidth - 202, buttonY, 116, 20)
                .build();
        selectButton.active = blockList.getSelected() != null;
        addRenderableWidget(selectButton);
        addRenderableWidget(
                Button.builder(
                                CommonComponents.GUI_CANCEL,
                                button -> onClose()
                        )
                        .bounds(panelX + panelWidth - 80, buttonY, 72, 20)
                        .build()
        );
        setInitialFocus(searchBox);
    }

    private void applyFilter() {
        if (blockList == null) {
            return;
        }
        ResourceLocation previous = blockList.selectedId();
        if (previous == null) {
            previous = preferredId;
        }
        List<BlockOption> matching = allOptions.stream()
                .filter(option -> RoadPresetBlockSearch.matches(
                        query,
                        option.displayName().getString(),
                        option.id()
                ))
                .toList();
        blockList.setOptions(matching, previous);
        if (selectButton != null) {
            selectButton.active = blockList.getSelected() != null;
        }
    }

    private void acceptSelection() {
        BlockEntry selected = blockList == null
                ? null
                : blockList.getSelected();
        if (selected == null) {
            return;
        }
        preferredId = selected.option.id();
        onSelected.accept(preferredId);
        minecraft.setScreen(parent);
    }

    private void onEntryClicked(BlockEntry entry, boolean doubleClick) {
        blockList.setSelected(entry);
        preferredId = entry.option.id();
        if (selectButton != null) {
            selectButton.active = true;
        }
        if (doubleClick) {
            acceptSelection();
        }
    }

    @Override
    public void tick() {
        searchBox.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            acceptSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN
                && searchBox.isFocused()
                && blockList.getSelected() != null) {
            setFocused(blockList);
            blockList.setFocused(blockList.getSelected());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && Screen.hasControlDown()) {
            setFocused(searchBox);
            searchBox.setFocused(true);
            searchBox.setHighlightPos(0);
            searchBox.setCursorPosition(searchBox.getValue().length());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        renderBackground(graphics);
        graphics.fill(
                panelX,
                panelY,
                panelX + panelWidth,
                panelY + panelHeight,
                BACKGROUND_PANEL
        );
        graphics.renderOutline(
                panelX,
                panelY,
                panelWidth,
                panelHeight,
                BORDER_PANEL
        );
        graphics.drawCenteredString(
                font,
                title,
                panelX + panelWidth / 2,
                panelY + 9,
                TEXT_PRIMARY
        );
        super.render(graphics, mouseX, mouseY, partialTick);

        if (blockList.children().isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.translatable(KEY + "no_results"),
                    panelX + panelWidth / 2,
                    listTop + Math.max(6, (listBottom - listTop) / 2 - 4),
                    TEXT_SECONDARY
            );
        }
        if (panelWidth >= 360) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            KEY + "result_count",
                            blockList.children().size(),
                            allOptions.size()
                    ),
                    panelX + 9,
                    panelY + panelHeight - 20,
                    TEXT_SECONDARY,
                    false
            );
        }
    }

    private static List<BlockOption> registryOptions() {
        List<BlockOption> options = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block == null || block.defaultBlockState().isAir()) {
                continue;
            }
            options.add(new BlockOption(
                    id,
                    block.getName(),
                    new ItemStack(block),
                    RoadPresetBlockAppearance.opaquePreviewColor(id)
            ));
        }
        options.sort(
                Comparator.comparing(
                                (BlockOption option) -> option.displayName()
                                        .getString()
                                        .toLowerCase(Locale.ROOT)
                        )
                        .thenComparing(option -> option.id().toString())
        );
        return List.copyOf(options);
    }

    private final class BlockList extends ObjectSelectionList<BlockEntry> {
        private final int rowWidth;

        private BlockList(
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

        private void setOptions(
                List<BlockOption> options,
                ResourceLocation selectedId
        ) {
            List<BlockEntry> entries = options.stream()
                    .map(BlockEntry::new)
                    .toList();
            replaceEntries(entries);
            boolean selectedIdIsAvailable = BuiltInRegistries.BLOCK
                    .getOptional(selectedId)
                    .filter(block -> !block.defaultBlockState().isAir())
                    .isPresent();
            int selectionIndex = RoadPresetBlockSearch.preferredIndex(
                    entries.stream()
                            .map(entry -> entry.option.id())
                            .toList(),
                    selectedId,
                    selectedIdIsAvailable
            );
            BlockEntry selected = selectionIndex < 0
                    ? null
                    : entries.get(selectionIndex);
            setSelected(selected);
            setScrollAmount(0.0);
            if (selected != null && selected.option.id().equals(selectedId)) {
                centerScrollOn(selected);
            }
        }

        private ResourceLocation selectedId() {
            BlockEntry selected = getSelected();
            return selected == null ? null : selected.option.id();
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

    private final class BlockEntry
            extends ObjectSelectionList.Entry<BlockEntry> {
        private final BlockOption option;
        private long lastClickTime;

        private BlockEntry(BlockOption option) {
            this.option = option;
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
            if (hovered) {
                graphics.fill(
                        left,
                        top,
                        left + width,
                        top + height,
                        OVERLAY_ROW_HOVERED
                );
            }
            int iconX = left + 5;
            int iconY = top + Math.max(1, (height - 16) / 2);
            if (!option.icon().isEmpty()) {
                graphics.renderItem(option.icon(), iconX, iconY);
            } else {
                renderFallbackSwatch(
                        graphics,
                        iconX,
                        iconY,
                        option.previewColor()
                );
            }

            int textX = left + 27;
            int availableWidth = Math.max(1, width - 32);
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(
                            option.displayName().getString(),
                            availableWidth
                    ),
                    textX,
                    top + 3,
                    TEXT_PRIMARY,
                    false
            );
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(
                            option.id().toString(),
                            availableWidth
                    ),
                    textX,
                    top + 14,
                    TEXT_SECONDARY,
                    false
            );
            if (hovered) {
                RoadPresetBlockSelectorScreen.this
                        .setTooltipForNextRenderPass(
                                Component.literal(
                                        option.displayName().getString()
                                                + "\n" + option.id()
                                )
                        );
            }
        }

        @Override
        public boolean mouseClicked(
                double mouseX,
                double mouseY,
                int button
        ) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return false;
            }
            long now = Util.getMillis();
            boolean doubleClick = blockList.getSelected() == this
                    && now - lastClickTime < 250L;
            lastClickTime = now;
            onEntryClicked(this, doubleClick);
            return true;
        }

        @Override
        public Component getNarration() {
            Component description = CommonComponents.joinForNarration(
                    option.displayName(),
                    Component.literal(option.id().toString())
            );
            if (blockList.getSelected() == this) {
                return CommonComponents.joinForNarration(
                        Component.translatable(KEY + "selected"),
                        description
                );
            }
            return description;
        }
    }

    private static void renderFallbackSwatch(
            GuiGraphics graphics,
            int x,
            int y,
            int color
    ) {
        graphics.fill(x, y, x + 16, y + 16, color);
        graphics.fill(x, y, x + 16, y + 2, lighten(color, 32));
        graphics.fill(x, y + 14, x + 16, y + 16, darken(color, 32));
        graphics.renderOutline(x, y, 16, 16, BORDER_HOVERED);
    }

    private record BlockOption(
            ResourceLocation id,
            Component displayName,
            ItemStack icon,
            int previewColor
    ) {
    }
}

package github.ssourabh58.spawnerfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.mojang.blaze3d.platform.InputConstants;

import java.text.SimpleDateFormat;
import java.util.*;

public class SpawnerHistoryScreen extends Screen {

    public enum ViewMode {
        SPAWNERS,
        CLUSTERS
    }

    public enum SortColumn {
        NAME,
        COORDINATES,
        DISTANCE,
        DATE,
        SIZE,
        PIN
    }

    private EditBox editBox;
    private ViewMode currentView = ViewMode.SPAWNERS;
    private SortColumn currentSort = SortColumn.DISTANCE;
    private boolean sortAscending = true;
    private int page = 0;
    private final List<Button> itemPinButtons = new ArrayList<>();

    // Performance Caching Fields
    private List<SpawnerStorage.SavedSpawner> cachedSpawners = new ArrayList<>();
    private List<SpawnerStorage.SavedCluster> cachedClusters = new ArrayList<>();
    private String lastSearchQuery = "";

    public SpawnerHistoryScreen() {
        super(Component.literal("Spawner History"));
    }

    private int getItemsPerPage() {
        int availableHeight = this.height - 110;
        return Math.max(5, availableHeight / 24);
    }

    @Override
    protected void init() {
        // Clear all previous renderable widgets completely before rebuilding screen elements
        this.clearWidgets();
        this.itemPinButtons.clear();

        int centerX = this.width / 2;
        String prevQuery = (this.editBox != null) ? this.editBox.getValue() : "";

        // --- Top Flat Horizontal Control Bar ---
        // 1. Search Box
        this.editBox = new EditBox(this.font, centerX - 215, 25, 140, 20, Component.literal("Search..."));
        this.editBox.setMaxLength(50);
        this.editBox.setValue(prevQuery);
        this.addRenderableWidget(this.editBox);

        // 2. View Mode Toggle Button
        String viewLabel = "View: " + (currentView == ViewMode.SPAWNERS ? "Spawners" : "Clusters");
        Button viewButton = Button.builder(Component.literal(viewLabel), btn -> {
            currentView = (currentView == ViewMode.SPAWNERS) ? ViewMode.CLUSTERS : ViewMode.SPAWNERS;
            currentSort = (currentView == ViewMode.SPAWNERS) ? SortColumn.DISTANCE : SortColumn.SIZE;
            page = 0;
            init();
        }).bounds(centerX - 70, 25, 100, 20).build();
        viewButton.setTooltip(Tooltip.create(Component.literal("Switch between individual spawners history and discovered clusters.")));
        this.addRenderableWidget(viewButton);

        // 3. Clear All Pins Button (Red Warning Tooltip)
        Button clearPinsButton = Button.builder(Component.literal("Clear Pins"), btn -> {
            Minecraft mc = Minecraft.getInstance();
            String serverId = SpawnerRenderer.getCurrentServerOrWorldId(mc);
            SpawnerStorage.getInstance(serverId).clearAllPins();
            VersionHelper.setOverlayMessage(mc, Component.literal("§cAll spawner/cluster pins cleared!"), false);
            refreshData();
        }).bounds(centerX + 35, 25, 80, 20).build();
        clearPinsButton.setTooltip(Tooltip.create(Component.literal("§cClears all pinned spawners and clusters.\nYou can re-pin any item anytime from History.")));
        this.addRenderableWidget(clearPinsButton);

        // 4. Done Button
        Button doneButton = Button.builder(Component.literal("Done"), btn -> {
            this.onClose();
        }).bounds(centerX + 120, 25, 95, 20).build();
        doneButton.setTooltip(Tooltip.create(Component.literal("Close the History screen.")));
        this.addRenderableWidget(doneButton);

        // --- Table Header Column Sorting Buttons ---
        int tableY = 52;
        if (currentView == ViewMode.SPAWNERS) {
            // Spawner View Headers
            Button col1Btn = Button.builder(Component.literal("Name / Type" + getSortArrow(SortColumn.NAME)), btn -> {
                setSortColumn(SortColumn.NAME);
            }).bounds(centerX - 215, tableY, 115, 18).build();
            col1Btn.setTooltip(Tooltip.create(Component.literal("Sort history by mob type name.")));
            this.addRenderableWidget(col1Btn);

            Button col2Btn = Button.builder(Component.literal("Coordinates" + getSortArrow(SortColumn.COORDINATES)), btn -> {
                setSortColumn(SortColumn.COORDINATES);
            }).bounds(centerX - 95, tableY, 90, 18).build();
            col2Btn.setTooltip(Tooltip.create(Component.literal("Sort history by coordinates.")));
            this.addRenderableWidget(col2Btn);

            Button col3Btn = Button.builder(Component.literal("Distance" + getSortArrow(SortColumn.DISTANCE)), btn -> {
                setSortColumn(SortColumn.DISTANCE);
            }).bounds(centerX - 0, tableY, 65, 18).build();
            col3Btn.setTooltip(Tooltip.create(Component.literal("Sort history by distance from player.")));
            this.addRenderableWidget(col3Btn);

            Button col4Btn = Button.builder(Component.literal("Date Found" + getSortArrow(SortColumn.DATE)), btn -> {
                setSortColumn(SortColumn.DATE);
            }).bounds(centerX + 70, tableY, 95, 18).build();
            col4Btn.setTooltip(Tooltip.create(Component.literal("Sort history by timestamp when found.")));
            this.addRenderableWidget(col4Btn);

            Button col5Btn = Button.builder(Component.literal("Pin" + getSortArrow(SortColumn.PIN)), btn -> {
                setSortColumn(SortColumn.PIN);
            }).bounds(centerX + 170, tableY, 55, 18).build();
            col5Btn.setTooltip(Tooltip.create(Component.literal("Sort history to bring all pinned items to top.")));
            this.addRenderableWidget(col5Btn);
        } else {
            // Cluster View Headers (With Spawner Count Column)
            Button col1Btn = Button.builder(Component.literal("Cluster Mobs" + getSortArrow(SortColumn.NAME)), btn -> {
                setSortColumn(SortColumn.NAME);
            }).bounds(centerX - 215, tableY, 110, 18).build();
            col1Btn.setTooltip(Tooltip.create(Component.literal("Sort clusters by mob types included.")));
            this.addRenderableWidget(col1Btn);

            Button col2Btn = Button.builder(Component.literal("Coords" + getSortArrow(SortColumn.COORDINATES)), btn -> {
                setSortColumn(SortColumn.COORDINATES);
            }).bounds(centerX - 100, tableY, 80, 18).build();
            col2Btn.setTooltip(Tooltip.create(Component.literal("Sort clusters by activation coordinates.")));
            this.addRenderableWidget(col2Btn);

            Button col3Btn = Button.builder(Component.literal("Dist" + getSortArrow(SortColumn.DISTANCE)), btn -> {
                setSortColumn(SortColumn.DISTANCE);
            }).bounds(centerX - 15, tableY, 45, 18).build();
            col3Btn.setTooltip(Tooltip.create(Component.literal("Sort clusters by distance from player.")));
            this.addRenderableWidget(col3Btn);

            Button col4Btn = Button.builder(Component.literal("Count" + getSortArrow(SortColumn.SIZE)), btn -> {
                setSortColumn(SortColumn.SIZE);
            }).bounds(centerX + 35, tableY, 50, 18).build();
            col4Btn.setTooltip(Tooltip.create(Component.literal("Sort clusters by number of spawners.")));
            this.addRenderableWidget(col4Btn);

            Button col5Btn = Button.builder(Component.literal("Date" + getSortArrow(SortColumn.DATE)), btn -> {
                setSortColumn(SortColumn.DATE);
            }).bounds(centerX + 90, tableY, 75, 18).build();
            col5Btn.setTooltip(Tooltip.create(Component.literal("Sort clusters by date found.")));
            this.addRenderableWidget(col5Btn);

            Button col6Btn = Button.builder(Component.literal("Pin" + getSortArrow(SortColumn.PIN)), btn -> {
                setSortColumn(SortColumn.PIN);
            }).bounds(centerX + 170, tableY, 55, 18).build();
            col6Btn.setTooltip(Tooltip.create(Component.literal("Sort clusters to bring pinned items to top.")));
            this.addRenderableWidget(col6Btn);
        }

        // --- Bottom Dynamic Pagination Controls ---
        int paginationBtnY = this.height - 30;
        Button prevButton = Button.builder(Component.literal("< Prev"), btn -> {
            if (page > 0) {
                page--;
                rebuildItemButtons();
            }
        }).bounds(centerX - 120, paginationBtnY, 60, 20).build();
        prevButton.setTooltip(Tooltip.create(Component.literal("View previous page of history entries.")));
        this.addRenderableWidget(prevButton);

        Button nextButton = Button.builder(Component.literal("Next >"), btn -> {
            int itemsPerPage = getItemsPerPage();
            int totalItems = (currentView == ViewMode.SPAWNERS) ? cachedSpawners.size() : cachedClusters.size();
            int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / itemsPerPage));
            if (page < totalPages - 1) {
                page++;
                rebuildItemButtons();
            }
        }).bounds(centerX + 60, paginationBtnY, 60, 20).build();
        nextButton.setTooltip(Tooltip.create(Component.literal("View next page of history entries.")));
        this.addRenderableWidget(nextButton);

        refreshData();
    }

    private void setSortColumn(SortColumn col) {
        if (currentSort == col) {
            sortAscending = !sortAscending;
        } else {
            currentSort = col;
            sortAscending = (col != SortColumn.PIN && col != SortColumn.SIZE);
        }
        page = 0;
        init();
    }

    private String getSortArrow(SortColumn col) {
        if (currentSort == col) {
            return sortAscending ? " ▲" : " ▼";
        }
        return "";
    }

    private void refreshData() {
        Minecraft mc = Minecraft.getInstance();
        String serverId = SpawnerRenderer.getCurrentServerOrWorldId(mc);
        SpawnerStorage storage = SpawnerStorage.getInstance(serverId);

        if (currentView == ViewMode.SPAWNERS) {
            cachedSpawners = getFilteredSpawners(storage);
        } else {
            cachedClusters = getFilteredClusters(storage);
        }
        rebuildItemButtons();
    }

    private void rebuildItemButtons() {
        for (Button btn : itemPinButtons) {
            this.removeWidget(btn);
        }
        itemPinButtons.clear();

        int centerX = this.width / 2;
        int startY = 75;
        int startX = centerX - 215;
        int itemsPerPage = getItemsPerPage();

        Minecraft mc = Minecraft.getInstance();
        String serverId = SpawnerRenderer.getCurrentServerOrWorldId(mc);
        SpawnerStorage storage = SpawnerStorage.getInstance(serverId);

        if (currentView == ViewMode.SPAWNERS) {
            int totalPages = Math.max(1, (int) Math.ceil((double) cachedSpawners.size() / itemsPerPage));
            page = Math.min(page, totalPages - 1);

            int startIdx = page * itemsPerPage;
            int endIdx = Math.min(startIdx + itemsPerPage, cachedSpawners.size());

            for (int i = startIdx; i < endIdx; i++) {
                SpawnerStorage.SavedSpawner s = cachedSpawners.get(i);
                int y = startY + (i - startIdx) * 24;

                Button pinBtn = Button.builder(Component.literal(s.isPinned ? "§e★" : "§fPin"), btn -> {
                    boolean pinned = storage.togglePinSpawner(s.getKey());
                    btn.setMessage(Component.literal(pinned ? "§e★" : "§fPin"));
                    VersionHelper.setOverlayMessage(mc, Component.literal(pinned ? "§aPinned Spawner!" : "§7Unpinned Spawner"), false);
                    refreshData();
                }).bounds(startX + 385, y + 1, 45, 18).build();

                pinBtn.setTooltip(Tooltip.create(Component.literal("Pin or unpin this spawner.")));
                this.addRenderableWidget(pinBtn);
                itemPinButtons.add(pinBtn);
            }
        } else {
            int totalPages = Math.max(1, (int) Math.ceil((double) cachedClusters.size() / itemsPerPage));
            page = Math.min(page, totalPages - 1);

            int startIdx = page * itemsPerPage;
            int endIdx = Math.min(startIdx + itemsPerPage, cachedClusters.size());

            for (int i = startIdx; i < endIdx; i++) {
                SpawnerStorage.SavedCluster c = cachedClusters.get(i);
                int y = startY + (i - startIdx) * 24;

                // Build rich multi-line tooltip detailing all member spawners
                StringBuilder sb = new StringBuilder();
                String summary = (c.mobNamesSummary != null && !c.mobNamesSummary.isEmpty()) ? c.mobNamesSummary : "Cluster of " + c.spawnerKeys.size();
                sb.append("§e").append(summary).append(":\n");

                for (String key : c.spawnerKeys) {
                    String[] parts = key.split(":");
                    if (parts.length == 2) {
                        String[] coords = parts[1].split(",");
                        if (coords.length == 3) {
                            SpawnerStorage.SavedSpawner sp = storage.getAllSpawners().stream().filter(s -> s.getKey().equals(key)).findFirst().orElse(null);
                            String mobName = "Spawner";
                            if (sp != null && sp.entityTypeId != null) {
                                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(sp.entityTypeId);
                                if (id != null) {
                                    EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(id).map(net.minecraft.core.Holder::value).orElse(null);
                                    mobName = SpawnerRenderer.getMobDisplayName(t);
                                }
                            }
                            sb.append("§f• ").append(mobName).append(" (").append(coords[0]).append(", ").append(coords[1]).append(", ").append(coords[2]).append(")\n");
                        }
                    }
                }
                sb.append("§7Activation: ").append(c.activationX).append(", ").append(c.activationY).append(", ").append(c.activationZ);

                Button pinBtn = Button.builder(Component.literal(c.isPinned ? "§e★" : "§fPin"), btn -> {
                    boolean pinned = storage.togglePinCluster(c.id);
                    btn.setMessage(Component.literal(pinned ? "§e★" : "§fPin"));
                    VersionHelper.setOverlayMessage(mc, Component.literal(pinned ? "§aPinned Cluster!" : "§7Unpinned Cluster"), false);
                    refreshData();
                }).bounds(startX + 385, y + 1, 45, 18).build();

                pinBtn.setTooltip(Tooltip.create(Component.literal(sb.toString().trim())));
                this.addRenderableWidget(pinBtn);
                itemPinButtons.add(pinBtn);
            }
        }
    }

    @Override
    public void tick() {
        if (this.editBox != null) {
            String currentVal = this.editBox.getValue().trim();
            if (!currentVal.equalsIgnoreCase(lastSearchQuery)) {
                lastSearchQuery = currentVal;
                page = 0;
                refreshData();
            }
        }
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent charEvent) {
        if (this.editBox != null && this.editBox.isFocused()) {
            if (this.editBox.charTyped(charEvent)) {
                return true;
            }
        }
        return super.charTyped(charEvent);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (this.editBox != null && this.editBox.isFocused()) {
            if (this.editBox.keyPressed(keyEvent)) {
                return true;
            }
        }
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    private List<SpawnerStorage.SavedSpawner> getFilteredSpawners(SpawnerStorage storage) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos ppos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        String query = editBox != null ? editBox.getValue().trim().toLowerCase() : "";

        List<SpawnerStorage.SavedSpawner> filtered = new ArrayList<>();
        for (SpawnerStorage.SavedSpawner s : storage.getAllSpawners()) {
            EntityType<?> type = null;
            if (s.entityTypeId != null) {
                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(s.entityTypeId);
                if (id != null) type = BuiltInRegistries.ENTITY_TYPE.get(id).map(net.minecraft.core.Holder::value).orElse(null);
            }
            String mobName = SpawnerRenderer.getMobDisplayName(type);
            if (query.isEmpty() || mobName.toLowerCase().contains(query) || s.dimension.toLowerCase().contains(query)) {
                filtered.add(s);
            }
        }

        filtered.sort((s1, s2) -> {
            int result = 0;
            if (currentSort == SortColumn.PIN) {
                result = Boolean.compare(s2.isPinned, s1.isPinned);
            } else if (currentSort == SortColumn.NAME) {
                EntityType<?> t1 = s1.entityTypeId != null ? BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.Identifier.tryParse(s1.entityTypeId)).map(net.minecraft.core.Holder::value).orElse(null) : null;
                EntityType<?> t2 = s2.entityTypeId != null ? BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.Identifier.tryParse(s2.entityTypeId)).map(net.minecraft.core.Holder::value).orElse(null) : null;
                result = SpawnerRenderer.getMobDisplayName(t1).compareToIgnoreCase(SpawnerRenderer.getMobDisplayName(t2));
            } else if (currentSort == SortColumn.COORDINATES) {
                result = Integer.compare(s1.x, s2.x);
            } else if (currentSort == SortColumn.DISTANCE) {
                result = Double.compare(Math.sqrt(ppos.distSqr(s1.getPos())), Math.sqrt(ppos.distSqr(s2.getPos())));
            } else if (currentSort == SortColumn.DATE) {
                result = Long.compare(s1.lastSeen, s2.lastSeen);
            }
            return sortAscending ? result : -result;
        });

        return filtered;
    }

    private List<SpawnerStorage.SavedCluster> getFilteredClusters(SpawnerStorage storage) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos ppos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        String query = editBox != null ? editBox.getValue().trim().toLowerCase() : "";

        List<SpawnerStorage.SavedCluster> filtered = new ArrayList<>();
        for (SpawnerStorage.SavedCluster c : storage.getAllClusters()) {
            String mobSummary = (c.mobNamesSummary != null) ? c.mobNamesSummary.toLowerCase() : "";
            if (query.isEmpty() || mobSummary.contains(query) || c.dimension.toLowerCase().contains(query)) {
                filtered.add(c);
            }
        }

        filtered.sort((c1, c2) -> {
            int result = 0;
            if (currentSort == SortColumn.PIN) {
                result = Boolean.compare(c2.isPinned, c1.isPinned);
            } else if (currentSort == SortColumn.NAME) {
                String s1 = (c1.mobNamesSummary != null) ? c1.mobNamesSummary : "";
                String s2 = (c2.mobNamesSummary != null) ? c2.mobNamesSummary : "";
                result = s1.compareToIgnoreCase(s2);
            } else if (currentSort == SortColumn.SIZE) {
                result = Integer.compare(c1.spawnerKeys.size(), c2.spawnerKeys.size());
            } else if (currentSort == SortColumn.COORDINATES) {
                result = Integer.compare(c1.activationX, c2.activationX);
            } else if (currentSort == SortColumn.DISTANCE) {
                result = Double.compare(Math.sqrt(ppos.distSqr(c1.getActivationPos())), Math.sqrt(ppos.distSqr(c2.getActivationPos())));
            } else if (currentSort == SortColumn.DATE) {
                result = Long.compare(c1.dateFound, c2.dateFound);
            }
            return sortAscending ? result : -result;
        });

        return filtered;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(this.font, "Spawner Finder - History Table", this.width / 2, 8, 0xFFFFFFFF);

        Minecraft mc = Minecraft.getInstance();
        BlockPos ppos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;

        int centerX = this.width / 2;
        int startY = 75;
        int startX = centerX - 215;
        int itemsPerPage = getItemsPerPage();

        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm");

        // Dynamic Table Outer Background Box
        int tableBgHeight = itemsPerPage * 24 + 6;
        graphics.fill(startX - 5, startY - 3, startX + 435, startY + tableBgHeight, 0xAA000000);

        if (currentView == ViewMode.SPAWNERS) {
            int totalPages = Math.max(1, (int) Math.ceil((double) cachedSpawners.size() / itemsPerPage));
            page = Math.min(page, totalPages - 1);

            graphics.centeredText(this.font, String.format("Page %d of %d (%d Spawners)", page + 1, totalPages, cachedSpawners.size()), centerX, this.height - 45, 0xFFFFF066);

            int startIdx = page * itemsPerPage;
            int endIdx = Math.min(startIdx + itemsPerPage, cachedSpawners.size());

            for (int i = startIdx; i < endIdx; i++) {
                SpawnerStorage.SavedSpawner s = cachedSpawners.get(i);
                int y = startY + (i - startIdx) * 24;

                if (i % 2 == 0) {
                    graphics.fill(startX - 3, y, startX + 433, y + 22, 0x22FFFFFF);
                }

                EntityType<?> type = null;
                if (s.entityTypeId != null) {
                    net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(s.entityTypeId);
                    if (id != null) type = BuiltInRegistries.ENTITY_TYPE.get(id).map(net.minecraft.core.Holder::value).orElse(null);
                }

                net.minecraft.world.item.Item icon = s.destroyed ? Items.BARRIER : SpawnerRenderer.getIconItem(type);
                graphics.item(new ItemStack(icon), startX + 2, y + 2);

                String mobName = SpawnerRenderer.getMobDisplayName(type);
                double dist = Math.sqrt(ppos.distSqr(s.getPos()));
                int color = s.destroyed ? 0xFF888888 : 0xFFFFFFFF;

                // Col 1: Mob Name (x: startX + 20)
                String nameText = (s.isPinned ? "§e★ " : "") + (s.destroyed ? "§7§m" + mobName : mobName);
                graphics.text(this.font, nameText, startX + 20, y + 6, color, false);

                // Col 2: Coords (x: startX + 120)
                String coordsText = String.format("%d, %d, %d", s.x, s.y, s.z);
                graphics.text(this.font, coordsText, startX + 120, y + 6, 0xFFCCCCCC, false);

                // Col 3: Distance (x: startX + 215)
                String distText = String.format("%.0fm", dist);
                graphics.text(this.font, distText, startX + 215, y + 6, 0xFF55FF55, false);

                // Col 4: Date (x: startX + 285)
                String dateStr = dateFormat.format(new Date(s.lastSeen));
                graphics.text(this.font, dateStr, startX + 285, y + 6, 0xFFAAAAAA, false);
            }
        } else {
            int totalPages = Math.max(1, (int) Math.ceil((double) cachedClusters.size() / itemsPerPage));
            page = Math.min(page, totalPages - 1);

            graphics.centeredText(this.font, String.format("Page %d of %d (%d Clusters)", page + 1, totalPages, cachedClusters.size()), centerX, this.height - 45, 0xFF66DDFF);

            int startIdx = page * itemsPerPage;
            int endIdx = Math.min(startIdx + itemsPerPage, cachedClusters.size());

            for (int i = startIdx; i < endIdx; i++) {
                SpawnerStorage.SavedCluster c = cachedClusters.get(i);
                int y = startY + (i - startIdx) * 24;

                if (i % 2 == 0) {
                    graphics.fill(startX - 3, y, startX + 433, y + 22, 0x22FFFFFF);
                }

                graphics.item(new ItemStack(Items.NETHER_STAR), startX + 2, y + 2);

                double dist = Math.sqrt(ppos.distSqr(c.getActivationPos()));

                // Col 1: Cluster Mobs Summary (x: startX + 20)
                String summary = (c.mobNamesSummary != null && !c.mobNamesSummary.isEmpty()) ? c.mobNamesSummary : "Cluster of " + c.spawnerKeys.size();
                if (summary.length() > 16) summary = summary.substring(0, 14) + "..";
                String clusterText = (c.isPinned ? "§e★ " : "") + summary;
                graphics.text(this.font, clusterText, startX + 20, y + 6, 0xFFFFFF00, false);

                // Col 2: Coords (x: startX + 115)
                String coordsText = String.format("%d, %d, %d", c.activationX, c.activationY, c.activationZ);
                graphics.text(this.font, coordsText, startX + 115, y + 6, 0xFFCCCCCC, false);

                // Col 3: Distance (x: startX + 200)
                String distText = String.format("%.0fm", dist);
                graphics.text(this.font, distText, startX + 200, y + 6, 0xFF55FF55, false);

                // Col 4: Count Column (x: startX + 250)
                String countText = c.spawnerKeys.size() + " Mobs";
                graphics.text(this.font, countText, startX + 250, y + 6, 0xFF66DDFF, false);

                // Col 5: Date (x: startX + 305)
                String dateStr = dateFormat.format(new Date(c.dateFound));
                graphics.text(this.font, dateStr, startX + 305, y + 6, 0xFFAAAAAA, false);
            }
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
}

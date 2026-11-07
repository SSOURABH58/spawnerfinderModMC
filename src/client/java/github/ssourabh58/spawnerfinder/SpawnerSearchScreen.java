package github.ssourabh58.spawnerfinder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.world.entity.EntityType;
import java.util.*;

public class SpawnerSearchScreen extends Screen {
    private EditBox editBox;
    private Button clearButton;
    private Button toggleExpandButton;
    private Button vanillaButton;
    private SearchDistanceSlider searchDistanceSlider;
    
    private int autocompleteIndex = -1;
    private String lastQuery = "";

    public SpawnerSearchScreen() {
        super(Component.literal("Spawner Search"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        
        // Add Support Buttons at the very top
        Button curseForgeBtn = Button.builder(Component.literal("CurseForge \u2764"), (btn) -> {
            Blaze3D.openUri(java.net.URI.create("https://www.curseforge.com/minecraft/mc-mods/spawnerfinder"));
        }).bounds(centerX - 100, 22, 95, 20).build();
        curseForgeBtn.setTooltip(Tooltip.create(Component.literal("Support SpawnerFinder on CurseForge!")));
        this.addRenderableWidget(curseForgeBtn);

        Button gitHubBtn = Button.builder(Component.literal("GitHub \u2605"), (btn) -> {
            Blaze3D.openUri(java.net.URI.create("https://github.com/SSOURABH58/spawnerfinderModMC"));
        }).bounds(centerX + 5, 22, 95, 20).build();
        gitHubBtn.setTooltip(Tooltip.create(Component.literal("Star SpawnerFinder on GitHub!")));
        this.addRenderableWidget(gitHubBtn);

        // Add EditBox
        this.editBox = new EditBox(this.font, centerX - 100, 75, 200, 20, Component.literal("Search..."));
        this.editBox.setMaxLength(50);
        this.editBox.setValue(SpawnerRenderer.searchQuery);
        this.addRenderableWidget(this.editBox);
        this.setFocused(this.editBox);

        // Add Clear Button
        this.clearButton = Button.builder(Component.literal("Clear Search"), (btn) -> {
            this.editBox.setValue("");
            SpawnerRenderer.searchQuery = "";
            SpawnerFinderConfig config = SpawnerFinderConfig.getInstance();
            config.searchQuery = "";
            config.save();
        }).bounds(centerX - 100, 100, 95, 20).build();
        this.clearButton.setTooltip(Tooltip.create(Component.literal("Clear the active search query filter.")));
        this.addRenderableWidget(this.clearButton);

        // Add Toggle Expand Button
        String expandText = SpawnerFinderConfig.getInstance().expandedList ? "Collapse View" : "Expand View";
        this.toggleExpandButton = Button.builder(Component.literal(expandText), (btn) -> {
            SpawnerFinderConfig config = SpawnerFinderConfig.getInstance();
            config.expandedList = !config.expandedList;
            config.save();
            btn.setMessage(Component.literal(config.expandedList ? "Collapse View" : "Expand View"));
        }).bounds(centerX + 5, 100, 95, 20).build();
        this.toggleExpandButton.setTooltip(Tooltip.create(Component.literal("Toggle between full list view and compact list view on HUD.")));
        this.addRenderableWidget(this.toggleExpandButton);

        // Add Vanilla Toggle Button
        String vanillaText = SpawnerFinderConfig.getInstance().vanillaOnly ? "Vanilla: ON" : "Vanilla: OFF";
        this.vanillaButton = Button.builder(Component.literal(vanillaText), (btn) -> {
            SpawnerFinderConfig config = SpawnerFinderConfig.getInstance();
            config.vanillaOnly = !config.vanillaOnly;
            config.save();
            btn.setMessage(Component.literal(config.vanillaOnly ? "Vanilla: ON" : "Vanilla: OFF"));
        }).bounds(centerX - 100, 125, 95, 20).build();
        this.vanillaButton.setTooltip(Tooltip.create(Component.literal("Filter list to display standard vanilla spawners only.")));
        this.addRenderableWidget(this.vanillaButton);

        // Add Trial Mobs Toggle Button
        String trialText = SpawnerFinderConfig.getInstance().includeTrialSpawners ? "Trial Mobs: ON" : "Trial Mobs: OFF";
        Button trialButton = Button.builder(Component.literal(trialText), (btn) -> {
            SpawnerFinderConfig config = SpawnerFinderConfig.getInstance();
            config.includeTrialSpawners = !config.includeTrialSpawners;
            config.save();
            btn.setMessage(Component.literal(config.includeTrialSpawners ? "Trial Mobs: ON" : "Trial Mobs: OFF"));
        }).bounds(centerX + 5, 125, 95, 20).build();
        trialButton.setTooltip(Tooltip.create(Component.literal("Include or exclude Trial Spawners from detection.")));
        this.addRenderableWidget(trialButton);

        // Add History Button
        Button historyBtn = Button.builder(Component.literal("History"), (btn) -> {
            Minecraft.getInstance().setScreenAndShow(new SpawnerHistoryScreen());
        }).bounds(centerX - 100, 150, 95, 20).build();
        historyBtn.setTooltip(Tooltip.create(Component.literal("Open Spawner History & Discovered Clusters screen.")));
        this.addRenderableWidget(historyBtn);

        // Add Pinned Toggle Button
        String pinnedText = SpawnerFinderConfig.getInstance().showPinned ? "Pinned: SHOW" : "Pinned: HIDE";
        Button pinnedButton = Button.builder(Component.literal(pinnedText), (btn) -> {
            SpawnerFinderConfig config = SpawnerFinderConfig.getInstance();
            config.showPinned = !config.showPinned;
            config.save();
            btn.setMessage(Component.literal(config.showPinned ? "Pinned: SHOW" : "Pinned: HIDE"));
        }).bounds(centerX + 5, 150, 95, 20).build();
        pinnedButton.setTooltip(Tooltip.create(Component.literal("Show or hide pinned spawners and clusters on top of HUD.")));
        this.addRenderableWidget(pinnedButton);

        // Add Destroyed Toggle Button
        String destroyedText = SpawnerFinderConfig.getInstance().showDestroyed ? "Destroyed: SHOW" : "Destroyed: HIDE";
        Button destroyedButton = Button.builder(Component.literal(destroyedText), (btn) -> {
            SpawnerFinderConfig config = SpawnerFinderConfig.getInstance();
            config.showDestroyed = !config.showDestroyed;
            config.save();
            btn.setMessage(Component.literal(config.showDestroyed ? "Destroyed: SHOW" : "Destroyed: HIDE"));
        }).bounds(centerX - 100, 175, 95, 20).build();
        destroyedButton.setTooltip(Tooltip.create(Component.literal("Show or hide destroyed/mined spawners in gray.")));
        this.addRenderableWidget(destroyedButton);

        // Add Clear Pins Button with Red Warning Tooltip
        Button clearPinsBtn = Button.builder(Component.literal("Clear Pins"), (btn) -> {
            Minecraft mc = Minecraft.getInstance();
            String serverId = SpawnerRenderer.getCurrentServerOrWorldId(mc);
            SpawnerStorage.getInstance(serverId).clearAllPins();
            VersionHelper.setOverlayMessage(mc, Component.literal("§cAll spawner/cluster pins cleared!"), false);
        }).bounds(centerX + 5, 175, 95, 20).build();
        clearPinsBtn.setTooltip(Tooltip.create(Component.literal("§cClears all pinned spawners and clusters.\nYou can re-pin any item anytime from History.")));
        this.addRenderableWidget(clearPinsBtn);

        // Add Search Distance Slider dynamically capped to active client render distance
        int maxRenderDistance = 16;
        if (Minecraft.getInstance().options != null) {
            maxRenderDistance = Math.max(1, Minecraft.getInstance().options.getEffectiveRenderDistance());
        }
        int currentDistance = Math.min(SpawnerFinderConfig.getInstance().searchDistance, maxRenderDistance);
        this.searchDistanceSlider = new SearchDistanceSlider(centerX - 100, 200, 200, 20, 0, maxRenderDistance, currentDistance);
        this.addRenderableWidget(this.searchDistanceSlider);

        // Add Done Button
        Button doneBtn = Button.builder(Component.literal("Done"), (btn) -> {
            this.onClose();
        }).bounds(centerX - 100, 225, 200, 20).build();
        doneBtn.setTooltip(Tooltip.create(Component.literal("Save settings and close screen.")));
        this.addRenderableWidget(doneBtn);
    }

    @Override
    public void tick() {
        // Real-time filtering when user types
        if (this.editBox != null) {
            String currentVal = this.editBox.getValue().trim();
            if (!currentVal.equalsIgnoreCase(SpawnerRenderer.searchQuery)) {
                SpawnerRenderer.searchQuery = currentVal;
                SpawnerFinderConfig.getInstance().searchQuery = currentVal;
                SpawnerFinderConfig.getInstance().save();
            }
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (keyEvent.key() == InputConstants.KEY_TAB) {
            String currentText = this.editBox.getValue();
            List<String> suggestions = getAutocompleteSuggestions(currentText);
            if (!suggestions.isEmpty()) {
                if (autocompleteIndex == -1 || !suggestions.contains(this.editBox.getValue())) {
                    autocompleteIndex = 0;
                } else {
                    autocompleteIndex = (autocompleteIndex + 1) % suggestions.size();
                }
                String match = suggestions.get(autocompleteIndex);
                this.editBox.setValue(match);
                SpawnerRenderer.searchQuery = match;
                SpawnerFinderConfig.getInstance().searchQuery = match;
                SpawnerFinderConfig.getInstance().save();
            }
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    // ponytail: Dynamic loading from registry rather than static list
    private List<String> getAutocompleteSuggestions(String text) {
        Set<String> allNames = new LinkedHashSet<>();
        boolean vanillaOnly = SpawnerFinderConfig.getInstance().vanillaOnly;

        // Add currently found spawners first
        for (SpawnerRenderer.SpawnerInfo s : SpawnerRenderer.getFoundSpawners()) {
            if (!vanillaOnly || SpawnerRenderer.isVanillaSpawnerMob(s.entityType)) {
                allNames.add(SpawnerRenderer.getMobDisplayName(s.entityType));
            }
        }
        // Add all dynamically loaded mobs
        for (EntityType<?> type : SpawnerRenderer.getAllMobs()) {
            if (!vanillaOnly || SpawnerRenderer.isVanillaSpawnerMob(type)) {
                allNames.add(SpawnerRenderer.getMobDisplayName(type));
            }
        }
        
        List<String> matches = new ArrayList<>();
        for (String name : allNames) {
            if (name.toLowerCase().startsWith(text.toLowerCase())) {
                matches.add(name);
            }
        }
        return matches;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        
        // Draw support request text at the very top
        graphics.centeredText(this.font, "Support by leaving a heart on CurseForge and a star on GitHub!", this.width / 2, 10, 0xFFFFE066);

        // Draw title
        graphics.centeredText(this.font, "Spawner Finder - Search", this.width / 2, 48, 0xFFFFFFFF);
        graphics.centeredText(this.font, "Type a mob name to filter nearby spawners (TAB to cycle)", this.width / 2, 60, 0xFFAAAAAA);

        // Draw UI list of suggestions below the buttons
        String currentQuery = this.editBox.getValue().trim();
        List<String> suggestions = getAutocompleteSuggestions(currentQuery);
        
        int startY = 255;
        int startX = this.width / 2 - 100;
        
        if (!suggestions.isEmpty()) {
            graphics.text(this.font, "Matching Mobs:", startX, startY, 0xFFFFFF00, false);
            startY += 15;
            
            // Show up to 5 suggestions
            int count = Math.min(5, suggestions.size());
            for (int i = 0; i < count; i++) {
                String name = suggestions.get(i);
                
                // Draw icon
                net.minecraft.world.item.Item iconItem = SpawnerRenderer.getIconItemByName(name);
                ItemStack stack = new ItemStack(iconItem);
                
                // Render icon
                graphics.item(stack, startX, startY - 4);
                
                // Draw name
                int color = name.equalsIgnoreCase(currentQuery) ? 0xFF55FF55 : 0xFFFFFFFF;
                graphics.text(this.font, name, startX + 20, startY, color, false);
                
                startY += 20;
            }
        }
    }

    private static int getServerViewDistance() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            return mc.getSingleplayerServer().getPlayerList().getViewDistance();
        }
        if (mc.level != null) {
            try {
                Object chunkSource = mc.level.getChunkSource();
                for (java.lang.reflect.Field field : chunkSource.getClass().getDeclaredFields()) {
                    if (field.getType().getSimpleName().contains("Storage")) {
                        field.setAccessible(true);
                        Object storage = field.get(chunkSource);
                        if (storage != null) {
                            for (java.lang.reflect.Field sf : storage.getClass().getDeclaredFields()) {
                                if (sf.getType() == int.class && (sf.getName().contains("radius") || sf.getName().contains("Distance") || sf.getName().contains("chunkRadius"))) {
                                    sf.setAccessible(true);
                                    int r = sf.getInt(storage);
                                    if (r > 0) return r;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private static class SearchDistanceSlider extends AbstractSliderButton {
        private final int minChunks;
        private final int maxChunks;

        public SearchDistanceSlider(int x, int y, int width, int height, int minChunks, int maxChunks, int currentChunks) {
            super(x, y, width, height, Component.empty(), (double) (Math.min(Math.max(currentChunks, minChunks), maxChunks) - minChunks) / Math.max(1, maxChunks - minChunks));
            this.minChunks = minChunks;
            this.maxChunks = maxChunks;
            this.updateMessage();

            int serverDist = getServerViewDistance();
            String serverText = serverDist > 0 ? "\nServer View Distance: " + serverDist + " chunks" : "";

            this.setTooltip(Tooltip.create(Component.literal(
                "Scans loaded chunks within this radius.\n\n" +
                "Client Render Distance: " + maxChunks + " chunks (" + (maxChunks * 16) + "m)" +
                serverText + "\n" +
                "Note: Only loaded chunks within server & client range can be scanned."
            )));
        }

        @Override
        protected void updateMessage() {
            int chunks = getChunks();
            int blocks = chunks * 16;
            this.setMessage(Component.literal("Search Distance: " + chunks + " Chunks (" + blocks + "m)"));
        }

        @Override
        protected void applyValue() {
            int chunks = getChunks();
            SpawnerFinderConfig config = SpawnerFinderConfig.getInstance();
            config.searchDistance = chunks;
            config.save();
        }

        public int getChunks() {
            return minChunks + (int) Math.round(this.value * (maxChunks - minChunks));
        }
    }
}

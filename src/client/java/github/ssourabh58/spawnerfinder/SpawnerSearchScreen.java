package github.ssourabh58.spawnerfinder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public class SpawnerSearchScreen extends Screen {
    private EditBox editBox;
    private Button clearButton;
    private Button toggleExpandButton;
    
    private int autocompleteIndex = -1;
    private String lastQuery = "";

    public SpawnerSearchScreen() {
        super(Component.literal("Spawner Search"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        
        // Add EditBox
        this.editBox = new EditBox(this.font, centerX - 100, 60, 200, 20, Component.literal("Search..."));
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
        }).bounds(centerX - 100, 90, 95, 20).build();
        this.addRenderableWidget(this.clearButton);

        // Add Toggle Expand Button
        String expandText = SpawnerFinderConfig.getInstance().expandedList ? "Collapse View" : "Expand View";
        this.toggleExpandButton = Button.builder(Component.literal(expandText), (btn) -> {
            SpawnerFinderConfig config = SpawnerFinderConfig.getInstance();
            config.expandedList = !config.expandedList;
            config.save();
            btn.setMessage(Component.literal(config.expandedList ? "Collapse View" : "Expand View"));
        }).bounds(centerX + 5, 90, 95, 20).build();
        this.addRenderableWidget(this.toggleExpandButton);

        // Add Close Button
        this.addRenderableWidget(Button.builder(Component.literal("Done"), (btn) -> {
            this.onClose();
        }).bounds(centerX - 100, 120, 200, 20).build());
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
        if (keyEvent.key() == GLFW.GLFW_KEY_TAB) {
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

    private List<String> getAutocompleteSuggestions(String text) {
        Set<String> allNames = new LinkedHashSet<>();
        // Add currently found spawners
        for (SpawnerRenderer.SpawnerInfo s : SpawnerRenderer.getFoundSpawners()) {
            allNames.add(SpawnerRenderer.getMobDisplayName(s.entityType));
        }
        // Add common ones
        allNames.addAll(Arrays.asList("Zombie", "Skeleton", "Spider", "Cave Spider", "Magma Cube", "Blaze", "Silverfish"));
        
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
        
        // Draw title
        graphics.centeredText(this.font, "Spawner Finder - Search", this.width / 2, 20, 0xFFFFFFFF);
        graphics.centeredText(this.font, "Type a mob name to filter nearby spawners (TAB to cycle)", this.width / 2, 40, 0xFFAAAAAA);

        // Draw UI list of suggestions below the buttons
        String currentQuery = this.editBox.getValue().trim();
        List<String> suggestions = getAutocompleteSuggestions(currentQuery);
        
        int startY = 155;
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
}

package github.ssourabh58.spawnerfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
// import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

public class SpawnerFinderClient implements ClientModInitializer {

    private static KeyMapping toggleKey;
    private static KeyMapping expandKey;
    private static KeyMapping searchKey;
    private static KeyMapping historyKey;

    @Override
    public void onInitializeClient() {
        // Create a single renderer instance
        SpawnerRenderer renderer = new SpawnerRenderer();

        // Register the spawner overlay renderer - this renders bright highlights
        // through blocks
        // WorldRenderEvents.LAST.register(renderer);

        // Register the HUD renderer - this shows the closest 5 spawners list
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("spawnerfinder", "hud_overlay"),
                renderer);

        // Register KeyMapping for Toggle
        toggleKey = KeyMappingHelper.registerKeyMapping(VersionHelper.createKeyMapping(
                "key.spawnerfinder.toggle", // Translation key
                InputConstants.KEY_O // Default key 'O'
        ));

        // Register KeyMapping for Expand List
        expandKey = KeyMappingHelper.registerKeyMapping(VersionHelper.createKeyMapping(
                "key.spawnerfinder.expand", // Translation key
                InputConstants.KEY_I // Default key 'I'
        ));

        // Register KeyMapping for Search Screen
        searchKey = KeyMappingHelper.registerKeyMapping(VersionHelper.createKeyMapping(
                "key.spawnerfinder.search", // Translation key
                InputConstants.KEY_U // Default key 'U'
        ));

        // Register KeyMapping for History Screen
        historyKey = KeyMappingHelper.registerKeyMapping(VersionHelper.createKeyMapping(
                "key.spawnerfinder.history", // Translation key
                InputConstants.KEY_H // Default key 'H'
        ));

        // Register Tick Handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SpawnerFinderConfig config = SpawnerFinderConfig.getInstance();
            while (toggleKey.consumeClick()) {
                config.modEnabled = !config.modEnabled;
                config.save();
                VersionHelper.setOverlayMessage(client,
                        Component.literal("Spawner Finder: " + (config.modEnabled ? "§aON" : "§cOFF")), false);
            }

            while (expandKey.consumeClick()) {
                config.expandedList = !config.expandedList;
                config.save();
                VersionHelper.setOverlayMessage(client,
                        Component.literal(
                                "Spawner List: " + (config.expandedList ? "§eExpanded" : "§7Compact")),
                        false);
            }

            while (searchKey.consumeClick()) {
                client.setScreenAndShow(new SpawnerSearchScreen());
            }

            while (historyKey.consumeClick()) {
                client.setScreenAndShow(new SpawnerHistoryScreen());
            }
        });
    }
}
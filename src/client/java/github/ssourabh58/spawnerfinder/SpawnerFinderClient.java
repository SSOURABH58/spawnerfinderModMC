package github.ssourabh58.spawnerfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
// import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class SpawnerFinderClient implements ClientModInitializer {

    private static KeyMapping toggleKey;
    private static KeyMapping expandKey;

    @Override
    public void onInitializeClient() {
        // Create a single renderer instance
        SpawnerRenderer renderer = new SpawnerRenderer();

        // Register the spawner overlay renderer - this renders bright highlights
        // through blocks
        // WorldRenderEvents.LAST.register(renderer);

        // Register the HUD renderer - this shows the closest 5 spawners list
        HudRenderCallback.EVENT.register(renderer);

        // Register KeyBinding for Toggle
        toggleKey = KeyBindingHelper.registerKeyBinding(VersionHelper.createKeyMapping(
                "key.spawnerfinder.toggle", // Translation key
                GLFW.GLFW_KEY_O // Default key 'O'
        ));

        // Register KeyBinding for Expand List
        expandKey = KeyBindingHelper.registerKeyBinding(VersionHelper.createKeyMapping(
                "key.spawnerfinder.expand", // Translation key
                GLFW.GLFW_KEY_I // Default key 'I'
        ));

        // Register Tick Handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SpawnerFinderConfig config = SpawnerFinderConfig.getInstance();
            while (toggleKey.consumeClick()) {
                config.modEnabled = !config.modEnabled;
                config.save();
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("Spawner Finder: " + (config.modEnabled ? "§aON" : "§cOFF")),
                            true);
                }
            }

            while (expandKey.consumeClick()) {
                config.expandedList = !config.expandedList;
                config.save();
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal(
                                    "Spawner List: " + (config.expandedList ? "§eExpanded" : "§7Compact")),
                            true);
                }
            }
        });
    }
}
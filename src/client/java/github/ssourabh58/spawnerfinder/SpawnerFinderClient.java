package github.ssourabh58.spawnerfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public class SpawnerFinderClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Create a single renderer instance
		SpawnerRenderer renderer = new SpawnerRenderer();
		
		// Register the spawner overlay renderer - this renders bright highlights through blocks
		WorldRenderEvents.LAST.register(renderer);
		
		// Register the HUD renderer - this shows the closest 5 spawners list
		HudRenderCallback.EVENT.register(renderer);
	}
}
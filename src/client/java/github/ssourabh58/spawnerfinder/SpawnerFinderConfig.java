package github.ssourabh58.spawnerfinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SpawnerFinderConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(),
            "spawnerfinder.json");

    public boolean modEnabled = true;
    public boolean expandedList = false;

    private static SpawnerFinderConfig instance;

    public static SpawnerFinderConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static SpawnerFinderConfig load() {
        if (!CONFIG_FILE.exists()) {
            SpawnerFinderConfig config = new SpawnerFinderConfig();
            config.save();
            return config;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            return GSON.fromJson(reader, SpawnerFinderConfig.class);
        } catch (IOException e) {
            SpawnerFinder.LOGGER.error("Failed to load SpawnerFinder config", e);
            return new SpawnerFinderConfig();
        }
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            SpawnerFinder.LOGGER.error("Failed to save SpawnerFinder config", e);
        }
    }
}

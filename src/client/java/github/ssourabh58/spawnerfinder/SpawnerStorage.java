package github.ssourabh58.spawnerfinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnerStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SAVE_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "spawnerfinder_saves");
    private static final int MAX_SPAWNERS_PER_SERVER = 25000;

    public static class SavedSpawner {
        public int x;
        public int y;
        public int z;
        public String dimension;
        public String entityTypeId;
        public boolean isTrialSpawner;
        public boolean destroyed;
        public boolean isPinned;
        public long lastSeen;

        public SavedSpawner() {}

        public SavedSpawner(int x, int y, int z, String dimension, String entityTypeId, boolean isTrialSpawner, boolean destroyed, boolean isPinned, long lastSeen) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
            this.entityTypeId = entityTypeId;
            this.isTrialSpawner = isTrialSpawner;
            this.destroyed = destroyed;
            this.isPinned = isPinned;
            this.lastSeen = lastSeen;
        }

        public String getKey() {
            return dimension + ":" + x + "," + y + "," + z;
        }

        public BlockPos getPos() {
            return new BlockPos(x, y, z);
        }
    }

    public static class SavedCluster {
        public String id;
        public List<String> spawnerKeys = new ArrayList<>();
        public int activationX;
        public int activationY;
        public int activationZ;
        public String dimension;
        public boolean isPinned;
        public long dateFound;

        public SavedCluster() {}

        public SavedCluster(String id, List<String> spawnerKeys, BlockPos activationPos, String dimension, boolean isPinned, long dateFound) {
            this.id = id;
            this.spawnerKeys = spawnerKeys;
            this.activationX = activationPos.getX();
            this.activationY = activationPos.getY();
            this.activationZ = activationPos.getZ();
            this.dimension = dimension;
            this.isPinned = isPinned;
            this.dateFound = dateFound;
        }

        public BlockPos getActivationPos() {
            return new BlockPos(activationX, activationY, activationZ);
        }
    }

    private static class StorageData {
        public Map<String, SavedSpawner> spawners = new HashMap<>();
        public Map<String, SavedCluster> clusters = new HashMap<>();
    }

    private final String serverId;
    private final File saveFile;
    private final Map<String, SavedSpawner> spawners = new ConcurrentHashMap<>();
    private final Map<String, SavedCluster> clusters = new ConcurrentHashMap<>();
    private boolean dirty = false;

    private static final Map<String, SpawnerStorage> INSTANCES = new ConcurrentHashMap<>();

    public static synchronized SpawnerStorage getInstance(String serverId) {
        if (serverId == null || serverId.isEmpty()) {
            serverId = "default";
        }
        return INSTANCES.computeIfAbsent(serverId, id -> {
            SpawnerStorage storage = new SpawnerStorage(id);
            storage.load();
            return storage;
        });
    }

    private SpawnerStorage(String serverId) {
        this.serverId = serverId;
        if (!SAVE_DIR.exists()) {
            SAVE_DIR.mkdirs();
        }
        String safeName = serverId.replaceAll("[^a-zA-Z0-9._-]", "_");
        this.saveFile = new File(SAVE_DIR, safeName + ".json");
    }

    public void load() {
        spawners.clear();
        clusters.clear();
        if (!saveFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(saveFile)) {
            StorageData data = GSON.fromJson(reader, StorageData.class);
            if (data != null) {
                if (data.spawners != null) spawners.putAll(data.spawners);
                if (data.clusters != null) clusters.putAll(data.clusters);
            }
        } catch (Exception e) {
            SpawnerFinder.LOGGER.error("Failed to load spawner storage for " + serverId, e);
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        try {
            pruneIfOverCap();
            StorageData data = new StorageData();
            data.spawners.putAll(spawners);
            data.clusters.putAll(clusters);
            try (FileWriter writer = new FileWriter(saveFile)) {
                GSON.toJson(data, writer);
                dirty = false;
            }
        } catch (IOException e) {
            SpawnerFinder.LOGGER.error("Failed to save spawner storage for " + serverId, e);
        }
    }

    public void putSpawner(BlockPos pos, String dimension, String entityTypeId, boolean isTrialSpawner) {
        String key = dimension + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        SavedSpawner existing = spawners.get(key);
        long now = System.currentTimeMillis();

        if (existing == null) {
            spawners.put(key, new SavedSpawner(pos.getX(), pos.getY(), pos.getZ(), dimension, entityTypeId, isTrialSpawner, false, false, now));
            dirty = true;
        } else {
            boolean updated = false;
            if (existing.destroyed) {
                existing.destroyed = false;
                updated = true;
            }
            if (!Objects.equals(existing.entityTypeId, entityTypeId)) {
                existing.entityTypeId = entityTypeId;
                updated = true;
            }
            existing.lastSeen = now;
            if (updated) {
                dirty = true;
            }
        }
    }

    public void putCluster(BlockPos activationPos, String dimension, List<BlockPos> spawnerPositions) {
        List<String> keys = new ArrayList<>();
        for (BlockPos p : spawnerPositions) {
            keys.add(dimension + ":" + p.getX() + "," + p.getY() + "," + p.getZ());
        }
        Collections.sort(keys);
        String clusterId = "cluster_" + dimension + "_" + activationPos.getX() + "_" + activationPos.getY() + "_" + activationPos.getZ();

        SavedCluster existing = clusters.get(clusterId);
        if (existing == null) {
            clusters.put(clusterId, new SavedCluster(clusterId, keys, activationPos, dimension, false, System.currentTimeMillis()));
            dirty = true;
        }
    }

    public void markDestroyed(BlockPos pos, String dimension) {
        String key = dimension + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        SavedSpawner existing = spawners.get(key);
        if (existing != null && !existing.destroyed) {
            existing.destroyed = true;
            existing.lastSeen = System.currentTimeMillis();
            dirty = true;
        }
    }

    public boolean togglePinSpawner(String key) {
        SavedSpawner s = spawners.get(key);
        if (s != null) {
            s.isPinned = !s.isPinned;
            dirty = true;
            save();
            return s.isPinned;
        }
        return false;
    }

    public boolean togglePinCluster(String clusterId) {
        SavedCluster c = clusters.get(clusterId);
        if (c != null) {
            c.isPinned = !c.isPinned;
            dirty = true;
            save();
            return c.isPinned;
        }
        return false;
    }

    public void clearAllPins() {
        for (SavedSpawner s : spawners.values()) {
            if (s.isPinned) {
                s.isPinned = false;
                dirty = true;
            }
        }
        for (SavedCluster c : clusters.values()) {
            if (c.isPinned) {
                c.isPinned = false;
                dirty = true;
            }
        }
        save();
    }

    public Collection<SavedSpawner> getAllSpawners() {
        return spawners.values();
    }

    public Collection<SavedCluster> getAllClusters() {
        return clusters.values();
    }

    private void pruneIfOverCap() {
        if (spawners.size() <= MAX_SPAWNERS_PER_SERVER) return;

        List<SavedSpawner> list = new ArrayList<>(spawners.values());
        list.sort(Comparator.comparingLong(s -> s.lastSeen));

        int toRemove = spawners.size() - MAX_SPAWNERS_PER_SERVER;
        int removed = 0;

        for (SavedSpawner s : list) {
            if (removed >= toRemove) break;
            if (s.destroyed && !s.isPinned) {
                spawners.remove(s.getKey());
                removed++;
            }
        }

        if (removed < toRemove) {
            for (SavedSpawner s : list) {
                if (removed >= toRemove) break;
                if (!s.isPinned && spawners.containsKey(s.getKey())) {
                    spawners.remove(s.getKey());
                    removed++;
                }
            }
        }
    }
}

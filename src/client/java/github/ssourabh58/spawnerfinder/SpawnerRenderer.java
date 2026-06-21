package github.ssourabh58.spawnerfinder;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;


public class SpawnerRenderer implements HudElement {

    public static String searchQuery = "";

    public SpawnerRenderer() {
        searchQuery = SpawnerFinderConfig.getInstance().searchQuery;
    }

    public static boolean modEnabled() {
        return SpawnerFinderConfig.getInstance().modEnabled;
    }

    public static boolean expandedList() {
        return SpawnerFinderConfig.getInstance().expandedList;
    }

    // ponytail: Dynamically discover registered mobs rather than hardcoding.
    private static boolean mobsInitialized = false;
    private static final Map<String, EntityType<?>> mobByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static final List<EntityType<?>> allMobs = new ArrayList<>();

    public static synchronized void initializeMobs() {
        if (mobsInitialized) return;
        mobByName.clear();
        allMobs.clear();
        int count = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type.getCategory() != MobCategory.MISC || SpawnEggItem.byId(type).isPresent()) {
                String displayName = getMobDisplayName(type);
                mobByName.put(displayName, type);
                allMobs.add(type);
                count++;
            }
        }
        SpawnerFinder.LOGGER.info("SpawnerFinder: Auto-detected {} mobs on load.", count);
        mobsInitialized = true;
    }

    public static List<EntityType<?>> getAllMobs() {
        if (!mobsInitialized) {
            initializeMobs();
        }
        return allMobs;
    }

    public static Map<String, EntityType<?>> getMobByNameMap() {
        if (!mobsInitialized) {
            initializeMobs();
        }
        return mobByName;
    }

    public static boolean isVanilla(EntityType<?> type) {
        if (type == null) return false;
        net.minecraft.resources.Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null && "minecraft".equals(id.getNamespace());
    }

    public static boolean isVanillaSpawnerMob(EntityType<?> type) {
        return type == EntityType.ZOMBIE ||
               type == EntityType.SKELETON ||
               type == EntityType.SPIDER ||
               type == EntityType.CAVE_SPIDER ||
               type == EntityType.MAGMA_CUBE ||
               type == EntityType.BLAZE ||
               type == EntityType.SILVERFISH;
    }

    private static final List<SpawnerInfo> foundSpawners = new ArrayList<>();
    private static final List<SpawnerGroup> foundGroups = new ArrayList<>(); // New list for groups
    private static long lastScanTime = 0;
    private static final long SCAN_INTERVAL = 2000; // Scan every 2 seconds

    public static List<SpawnerInfo> getFoundSpawners() {
        return foundSpawners;
    }

    public static class SpawnerInfo {
        public final BlockPos pos;
        public final EntityType<?> entityType;
        public final double distance;

        public SpawnerInfo(BlockPos pos, EntityType<?> entityType, double distance) {
            this.pos = pos;
            this.entityType = entityType;
            this.distance = distance;
        }
    }

    public static class SpawnerGroup {
        public final List<SpawnerInfo> spawners;
        public final BlockPos activationPos;
        public final double distanceToPlayer;

        public SpawnerGroup(List<SpawnerInfo> spawners, BlockPos activationPos, double distanceToPlayer) {
            this.spawners = spawners;
            this.activationPos = activationPos;
            this.distanceToPlayer = distanceToPlayer;
        }
    }

    private void scanForSpawners(Level world, BlockPos playerPos) {
        foundSpawners.clear();
        int chunkRadius = 10; // Approx 160 blocks radius

        // Get player chunk coordinates
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                try {
                    var chunkAccess = world.getChunk(playerChunkX + dx, playerChunkZ + dz, ChunkStatus.FULL, false);
                    if (chunkAccess instanceof LevelChunk chunk) {
                        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                            if (blockEntity instanceof SpawnerBlockEntity spawner) {
                                BlockPos pos = spawner.getBlockPos();
                                EntityType<?> entityType = getSpawnerEntityType(spawner, world, pos);
                                if (entityType != null) {
                                    double distance = Math.sqrt(playerPos.distSqr(pos));
                                    foundSpawners.add(new SpawnerInfo(pos, entityType, distance));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore chunk loading errors
                }
            }
        }

        // Sort by distance
        foundSpawners.sort(Comparator.comparingDouble(s -> s.distance));

        // Calculate groups
        findSpawnerGroups(playerPos);
    }

    private void findSpawnerGroups(BlockPos playerPos) {
        foundGroups.clear();
        if (foundSpawners.size() < 2)
            return;

        List<List<SpawnerInfo>> potentialGroups = new ArrayList<>();

        for (int i = 0; i < foundSpawners.size(); i++) {
            List<SpawnerInfo> currentGroup = new ArrayList<>();
            currentGroup.add(foundSpawners.get(i));

            for (int j = 0; j < foundSpawners.size(); j++) {
                if (i == j)
                    continue;
                SpawnerInfo candidate = foundSpawners.get(j);

                List<SpawnerInfo> testList = new ArrayList<>(currentGroup);
                testList.add(candidate);

                if (getValidActivationSpot(testList) != null) {
                    currentGroup.add(candidate);
                }
            }

            if (currentGroup.size() >= 2) {
                potentialGroups.add(currentGroup);
            }
        }

        for (List<SpawnerInfo> groupList : potentialGroups) {
            boolean isSubset = false;
            for (SpawnerGroup existing : foundGroups) {
                if (existing.spawners.containsAll(groupList)) {
                    isSubset = true;
                    break;
                }
            }

            if (!isSubset) {
                foundGroups.removeIf(existing -> groupList.containsAll(existing.spawners));

                BlockPos activationSpot = getValidActivationSpot(groupList);
                if (activationSpot != null) {
                    double dist = Math.sqrt(playerPos.distSqr(activationSpot));
                    foundGroups.add(new SpawnerGroup(groupList, activationSpot, dist));
                }
            }
        }

        foundGroups.sort((g1, g2) -> {
            int sizeCompare = Integer.compare(g2.spawners.size(), g1.spawners.size());
            if (sizeCompare != 0)
                return sizeCompare;
            return Double.compare(g1.distanceToPlayer, g2.distanceToPlayer);
        });
    }

    private BlockPos getValidActivationSpot(List<SpawnerInfo> list) {
        if (list.isEmpty())
            return null;

        double x = 0, y = 0, z = 0;
        for (SpawnerInfo s : list) {
            x += s.pos.getX();
            y += s.pos.getY();
            z += s.pos.getZ();
        }
        x /= list.size();
        y /= list.size();
        z /= list.size();

        BlockPos centroid = new BlockPos((int) x, (int) y, (int) z);

        for (SpawnerInfo s : list) {
            if (Math.sqrt(centroid.distSqr(s.pos)) > 16.0) {
                return null;
            }
        }
        return centroid;
    }

    private EntityType<?> getSpawnerEntityType(SpawnerBlockEntity spawner, Level world, BlockPos pos) {
        try {
            var displayEntity = spawner.getSpawner().getOrCreateDisplayEntity(world, pos);
            return displayEntity != null ? displayEntity.getType() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int getColorForMobType(EntityType<?> entityType) {
        if (entityType == EntityType.SKELETON)
            return 0xFFFFFFFF;
        if (entityType == EntityType.ZOMBIE)
            return 0xFF00FF00;
        if (entityType == EntityType.SPIDER)
            return 0xFFFF0000;
        if (entityType == EntityType.CAVE_SPIDER)
            return 0xFF0000FF;
        if (entityType == EntityType.MAGMA_CUBE)
            return 0xFFFF8000;
        if (entityType == EntityType.BLAZE)
            return 0xFFFFFF00;
        if (entityType == EntityType.SILVERFISH)
            return 0xFF808080;
        return 0xFFFF00FF;
    }

    public static String getMobDisplayName(EntityType<?> entityType) {
        if (entityType == EntityType.SKELETON)
            return "Skeleton";
        if (entityType == EntityType.ZOMBIE)
            return "Zombie";
        if (entityType == EntityType.SPIDER)
            return "Spider";
        if (entityType == EntityType.CAVE_SPIDER)
            return "Cave Spider";
        if (entityType == EntityType.MAGMA_CUBE)
            return "Magma Cube";
        if (entityType == EntityType.BLAZE)
            return "Blaze";
        if (entityType == EntityType.SILVERFISH)
            return "Silverfish";
        return entityType.getDescription().getString();
    }

    // ponytail: Dynamically resolve spawn egg item or fallback to spawner
    public static net.minecraft.world.item.Item getIconItem(EntityType<?> entityType) {
        if (entityType == null) return Items.SPAWNER;
        return SpawnEggItem.byId(entityType).map(Holder::value).orElse(Items.SPAWNER);
    }

    public static net.minecraft.world.item.Item getIconItemByName(String name) {
        EntityType<?> type = getMobByNameMap().get(name);
        return getIconItem(type);
    }


    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!modEnabled() || mc.player == null)
            return;

        Level world = mc.level;
        BlockPos playerPos = mc.player.blockPosition();
        if (world != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastScanTime > SCAN_INTERVAL) {
                scanForSpawners(world, playerPos);
                lastScanTime = currentTime;
            }
        }

        // Apply filtering
        List<SpawnerInfo> displayedSpawners = new ArrayList<>();
        boolean vanillaOnly = SpawnerFinderConfig.getInstance().vanillaOnly;
        for (SpawnerInfo s : foundSpawners) {
            if (vanillaOnly && !isVanillaSpawnerMob(s.entityType)) {
                continue;
            }
            if (searchQuery.isEmpty() || getMobDisplayName(s.entityType).toLowerCase().contains(searchQuery.toLowerCase())) {
                displayedSpawners.add(s);
            }
        }

        List<SpawnerGroup> displayedGroups = new ArrayList<>();
        for (SpawnerGroup g : foundGroups) {
            boolean matches = false;
            for (SpawnerInfo s : g.spawners) {
                if (vanillaOnly && !isVanillaSpawnerMob(s.entityType)) {
                    continue;
                }
                if (searchQuery.isEmpty() || getMobDisplayName(s.entityType).toLowerCase().contains(searchQuery.toLowerCase())) {
                    matches = true;
                    break;
                }
            }
            if (matches) {
                List<SpawnerInfo> filteredSpawners = new ArrayList<>();
                for (SpawnerInfo s : g.spawners) {
                    if (!vanillaOnly || isVanillaSpawnerMob(s.entityType)) {
                        filteredSpawners.add(s);
                    }
                }
                if (!filteredSpawners.isEmpty()) {
                    displayedGroups.add(new SpawnerGroup(filteredSpawners, g.activationPos, g.distanceToPlayer));
                }
            }
        }

        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();

        // --- Top Center Search Selection ---
        if (!searchQuery.isEmpty()) {
            guiGraphics.centeredText(mc.font, "Search selected: " + searchQuery, guiWidth / 2, 10, 0xFFFFAA00);
        }

        // --- Left Side: Found Spawners List ---
        int leftX = 10;
        int leftY = 10;
        int leftWidth = 240;
        
        // Calculate background height for left menu
        int leftBoxHeight = expandedList() ? (guiHeight - 10) : 124;
        
        // Draw background box
        guiGraphics.fill(leftX - 5, leftY - 5, leftX - 5 + leftWidth, leftY - 5 + leftBoxHeight, 0x77000000);

        String header = String.format("§6§lSpawners found: %d", displayedSpawners.size());
        guiGraphics.text(mc.font, header, leftX, leftY, 0xFFFFFFFF, false);
        leftY += 12;
        guiGraphics.text(mc.font, "----------------", leftX, leftY, 0xFFAAAAAA, false);
        leftY += 14;

        if (!displayedSpawners.isEmpty()) {
            int count = expandedList() ? displayedSpawners.size() : Math.min(5, displayedSpawners.size());
            for (int i = 0; i < count; i++) {
                SpawnerInfo spawner = displayedSpawners.get(i);
                String mobName = getMobDisplayName(spawner.entityType);
                int color = getColorForMobType(spawner.entityType);
                String text = String.format("%s: %d, %d, %d (%.1fm)", mobName, spawner.pos.getX(), spawner.pos.getY(),
                        spawner.pos.getZ(), spawner.distance);
                
                // Draw Icon
                guiGraphics.item(new ItemStack(getIconItem(spawner.entityType)), leftX, leftY);
                
                // Draw Text next to icon
                guiGraphics.text(mc.font, text, leftX + 20, leftY + 4, color, false);
                leftY += 18;
            }
        }

        // --- Right Side: Spawner Groups ---
        if (!displayedGroups.isEmpty()) {
            int rightWidth = 240;
            int rightX = guiWidth - rightWidth - 5;
            int rightY = 10;
            
            // Calculate background height for right menu
            int rightBoxHeight = expandedList() ? (guiHeight - 10) : 130;
            
            // Draw background box
            guiGraphics.fill(rightX - 5, rightY - 5, rightX - 5 + rightWidth, rightY - 5 + rightBoxHeight, 0x77000000);

            String groupHeader = String.format("§b§lSpawner Groups found: %d", displayedGroups.size());
            guiGraphics.text(mc.font, groupHeader, rightX, rightY, 0xFFFFFFFF, false);
            rightY += 12;
            guiGraphics.text(mc.font, "----------------", rightX, rightY, 0xFFAAAAAA, false);
            rightY += 14;

            int detailLines = 0;
            int maxDetailLines = expandedList() ? Integer.MAX_VALUE : 5;

            for (SpawnerGroup group : displayedGroups) {
                if (detailLines >= maxDetailLines) break;

                String groupTitle = String.format("§eGroup of: %d", group.spawners.size());
                guiGraphics.text(mc.font, groupTitle, rightX, rightY, 0xFFFFFF00, false);
                rightY += 12;
                detailLines++;
                if (detailLines >= maxDetailLines) break;

                String activationText = String.format("§7Activate: %d, %d, %d (%.1fm)",
                        group.activationPos.getX(), group.activationPos.getY(), group.activationPos.getZ(),
                        group.distanceToPlayer);
                guiGraphics.text(mc.font, activationText, rightX, rightY, 0xFFCCCCCC, false);
                rightY += 12;
                detailLines++;
                if (detailLines >= maxDetailLines) break;

                for (SpawnerInfo spawner : group.spawners) {
                    String mob = getMobDisplayName(spawner.entityType);
                    String spawnerText = String.format("  %s: %d, %d, %d", mob, spawner.pos.getX(), spawner.pos.getY(),
                            spawner.pos.getZ());
                    
                    // Draw icon inside groups
                    guiGraphics.item(new ItemStack(getIconItem(spawner.entityType)), rightX + 5, rightY);
                    
                    // Draw text next to icon
                    guiGraphics.text(mc.font, spawnerText, rightX + 25, rightY + 4, getColorForMobType(spawner.entityType), false);
                    rightY += 18;
                    detailLines++;
                    if (detailLines >= maxDetailLines) break;
                }

                if (detailLines >= maxDetailLines) break;
                
                guiGraphics.text(mc.font, "-------------", rightX, rightY, 0xFFAAAAAA, false);
                rightY += 12;
                detailLines++;
            }
        }
    }
}

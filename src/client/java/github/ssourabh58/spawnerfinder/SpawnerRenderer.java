package github.ssourabh58.spawnerfinder;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
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
        if (type == null) return false;
        net.minecraft.resources.Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (id == null || !"minecraft".equals(id.getNamespace())) return false;
        String path = id.getPath();
        return "zombie".equals(path) ||
               "skeleton".equals(path) ||
               "spider".equals(path) ||
               "cave_spider".equals(path) ||
               "magma_cube".equals(path) ||
               "blaze".equals(path) ||
               "silverfish".equals(path) ||
               "breeze".equals(path) ||
               "bogged".equals(path) ||
               "stray".equals(path) ||
               "husk".equals(path) ||
               "slime".equals(path);
    }

    private static final List<SpawnerInfo> foundSpawners = new ArrayList<>();
    private static final List<SpawnerGroup> foundGroups = new ArrayList<>(); // New list for groups
    private static long lastScanTime = 0;
    private static final long SCAN_INTERVAL = 2000; // Scan every 2 seconds

    public static List<SpawnerInfo> getFoundSpawners() {
        return foundSpawners;
    }

    public static String getCurrentServerOrWorldId(Minecraft mc) {
        if (mc == null) return "world_local";
        if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) {
            return "server_" + mc.getCurrentServer().ip.toLowerCase().replaceAll("[^a-zA-Z0-9._-]", "_");
        }
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            try {
                var server = mc.getSingleplayerServer();
                var worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                String absolutePath = worldPath.toAbsolutePath().normalize().toString();
                return "singleplayer_" + Math.abs(absolutePath.hashCode());
            } catch (Exception e) {
                return "world_" + mc.getSingleplayerServer().getWorldData().getLevelName();
            }
        }
        return "world_local";
    }

    public static class SpawnerInfo {
        public final BlockPos pos;
        public final EntityType<?> entityType;
        public final double distance;
        public final boolean isTrialSpawner;
        public final boolean isDestroyed;

        public SpawnerInfo(BlockPos pos, EntityType<?> entityType, double distance, boolean isTrialSpawner, boolean isDestroyed) {
            this.pos = pos;
            this.entityType = entityType;
            this.distance = distance;
            this.isTrialSpawner = isTrialSpawner;
            this.isDestroyed = isDestroyed;
        }

        public SpawnerInfo(BlockPos pos, EntityType<?> entityType, double distance, boolean isTrialSpawner) {
            this(pos, entityType, distance, isTrialSpawner, false);
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
        Minecraft mc = Minecraft.getInstance();
        String serverId = getCurrentServerOrWorldId(mc);
        SpawnerStorage storage = SpawnerStorage.getInstance(serverId);
        String currentDimension = world.dimension().identifier().toString();
        int chunkRadius = Math.max(0, SpawnerFinderConfig.getInstance().searchDistance);
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        // 1. Scan active loaded chunks around player and store active spawners
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                try {
                    var chunkAccess = world.getChunk(playerChunkX + dx, playerChunkZ + dz, ChunkStatus.FULL, false);
                    if (chunkAccess instanceof LevelChunk chunk) {
                        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                            boolean isNormal = blockEntity instanceof SpawnerBlockEntity;
                            boolean isTrial = blockEntity instanceof TrialSpawnerBlockEntity;
                            if (isNormal || (isTrial && SpawnerFinderConfig.getInstance().includeTrialSpawners)) {
                                BlockPos pos = blockEntity.getBlockPos();
                                EntityType<?> entityType = getSpawnerEntityType(blockEntity, world, pos);
                                if (entityType != null) {
                                    net.minecraft.resources.Identifier keyId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
                                    if (keyId != null) {
                                        storage.putSpawner(pos, currentDimension, keyId.toString(), isTrial);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    // Ignore chunk loading errors
                }
            }
        }

        // 2. Re-validate saved spawners in loaded chunks (mark destroyed if spawner block is missing)
        for (SpawnerStorage.SavedSpawner saved : storage.getAllSpawners()) {
            if (saved.dimension.equals(currentDimension)) {
                int scx = saved.x >> 4;
                int scz = saved.z >> 4;
                try {
                    var chunkAccess = world.getChunk(scx, scz, ChunkStatus.FULL, false);
                    if (chunkAccess instanceof LevelChunk chunk) {
                        BlockPos spawnerPos = saved.getPos();
                        BlockEntity be = chunk.getBlockEntity(spawnerPos);
                        boolean isNormal = be instanceof SpawnerBlockEntity;
                        boolean isTrial = be instanceof TrialSpawnerBlockEntity;
                        if (!isNormal && !isTrial) {
                            storage.markDestroyed(spawnerPos, currentDimension);
                        }
                    }
                } catch (Exception e) {
                    // Chunk not loaded currently
                }
            }
        }

        storage.save();

        // 3. Populate foundSpawners list from storage for current dimension
        for (SpawnerStorage.SavedSpawner saved : storage.getAllSpawners()) {
            if (saved.dimension.equals(currentDimension)) {
                BlockPos pos = saved.getPos();
                double distance = Math.sqrt(playerPos.distSqr(pos));
                double maxBlockDistance = chunkRadius * 16.0;
                if (chunkRadius == 0) maxBlockDistance = 16.0; // minimum radius for current chunk

                if (distance <= maxBlockDistance) {
                    EntityType<?> entityType = null;
                    if (saved.entityTypeId != null) {
                        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(saved.entityTypeId);
                        if (id != null) {
                            entityType = BuiltInRegistries.ENTITY_TYPE.get(id).map(Holder::value).orElse(null);
                        }
                    }
                    foundSpawners.add(new SpawnerInfo(pos, entityType, distance, saved.isTrialSpawner, saved.destroyed));
                }
            }
        }

        // Sort by distance
        foundSpawners.sort(Comparator.comparingDouble(s -> s.distance));

        // Calculate groups using active spawners
        findSpawnerGroups(playerPos);
    }

    private void findSpawnerGroups(BlockPos playerPos) {
        foundGroups.clear();
        
        // Filter out destroyed spawners for group calculations
        List<SpawnerInfo> activeSpawners = new ArrayList<>();
        for (SpawnerInfo s : foundSpawners) {
            if (!s.isDestroyed) {
                activeSpawners.add(s);
            }
        }

        if (activeSpawners.size() < 2)
            return;

        List<List<SpawnerInfo>> potentialGroups = new ArrayList<>();

        for (int i = 0; i < activeSpawners.size(); i++) {
            List<SpawnerInfo> currentGroup = new ArrayList<>();
            currentGroup.add(activeSpawners.get(i));

            for (int j = 0; j < activeSpawners.size(); j++) {
                if (i == j)
                    continue;
                SpawnerInfo candidate = activeSpawners.get(j);

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

        // Save found clusters into persistent storage
        if (Minecraft.getInstance().level != null) {
            String serverId = getCurrentServerOrWorldId(Minecraft.getInstance());
            SpawnerStorage storage = SpawnerStorage.getInstance(serverId);
            String currentDimension = Minecraft.getInstance().level.dimension().identifier().toString();
            for (SpawnerGroup g : foundGroups) {
                storage.putCluster(g.activationPos, currentDimension, g.spawners);
            }
            storage.save();
        }
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

    private EntityType<?> getSpawnerEntityType(BlockEntity blockEntity, Level world, BlockPos pos) {
        try {
            if (blockEntity instanceof SpawnerBlockEntity spawner) {
                var displayEntity = spawner.getSpawner().getOrCreateDisplayEntity(world, pos);
                return displayEntity != null ? displayEntity.getType() : null;
            } else if (blockEntity instanceof TrialSpawnerBlockEntity trialSpawner) {
                var spawner = trialSpawner.getTrialSpawner();
                var displayEntity = spawner.getStateData().getOrCreateDisplayEntity(spawner, world, spawner.getState());
                return displayEntity != null ? displayEntity.getType() : null;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private int getColorForMobType(EntityType<?> entityType) {
        if (entityType == null) return 0xFFFF00FF;
        net.minecraft.resources.Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (id == null) return 0xFFFF00FF;
        String path = id.getPath();
        if ("skeleton".equals(path))
            return 0xFFFFFFFF;
        if ("zombie".equals(path))
            return 0xFF00FF00;
        if ("spider".equals(path))
            return 0xFFFF0000;
        if ("cave_spider".equals(path))
            return 0xFF0000FF;
        if ("magma_cube".equals(path))
            return 0xFFFF8000;
        if ("blaze".equals(path))
            return 0xFFFFFF00;
        if ("silverfish".equals(path))
            return 0xFF808080;
        if ("breeze".equals(path))
            return 0xFFC0E0FF;
        if ("bogged".equals(path))
            return 0xFF5C714B;
        if ("stray".equals(path))
            return 0xFFA0C0D0;
        if ("husk".equals(path))
            return 0xFFC2B280;
        if ("slime".equals(path))
            return 0xFF00FF80;
        return 0xFFFF00FF;
    }

    public static String getMobDisplayName(EntityType<?> entityType) {
        if (entityType == null) return "Unknown";
        net.minecraft.resources.Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (id == null) return "Unknown";
        String path = id.getPath();
        if ("skeleton".equals(path))
            return "Skeleton";
        if ("zombie".equals(path))
            return "Zombie";
        if ("spider".equals(path))
            return "Spider";
        if ("cave_spider".equals(path))
            return "Cave Spider";
        if ("magma_cube".equals(path))
            return "Magma Cube";
        if ("blaze".equals(path))
            return "Blaze";
        if ("silverfish".equals(path))
            return "Silverfish";
        if ("breeze".equals(path))
            return "Breeze";
        if ("bogged".equals(path))
            return "Bogged";
        if ("stray".equals(path))
            return "Stray";
        if ("husk".equals(path))
            return "Husk";
        if ("slime".equals(path))
            return "Slime";
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
        boolean showDestroyed = SpawnerFinderConfig.getInstance().showDestroyed;

        for (SpawnerInfo s : foundSpawners) {
            if (!showDestroyed && s.isDestroyed) {
                continue;
            }
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
                if (!showDestroyed && s.isDestroyed) {
                    continue;
                }
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
                    if ((showDestroyed || !s.isDestroyed) && (!vanillaOnly || isVanillaSpawnerMob(s.entityType))) {
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

        // Render Pinned Spawners if enabled
        if (SpawnerFinderConfig.getInstance().showPinned) {
            String serverId = getCurrentServerOrWorldId(mc);
            SpawnerStorage storage = SpawnerStorage.getInstance(serverId);
            String currentDimension = world.dimension().identifier().toString();

            List<SpawnerStorage.SavedSpawner> pinnedList = new ArrayList<>();
            for (SpawnerStorage.SavedSpawner s : storage.getAllSpawners()) {
                if (s.isPinned && s.dimension.equals(currentDimension)) {
                    pinnedList.add(s);
                }
            }

            if (!pinnedList.isEmpty()) {
                guiGraphics.text(mc.font, "§e★ Pinned Spawners:", leftX, leftY, 0xFFFFFF00, false);
                leftY += 12;

                int pCount = expandedList() ? pinnedList.size() : Math.min(3, pinnedList.size());
                for (int i = 0; i < pCount; i++) {
                    SpawnerStorage.SavedSpawner s = pinnedList.get(i);
                    double dist = Math.sqrt(playerPos.distSqr(s.getPos()));
                    EntityType<?> type = null;
                    if (s.entityTypeId != null) {
                        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(s.entityTypeId);
                        if (id != null) type = BuiltInRegistries.ENTITY_TYPE.get(id).map(Holder::value).orElse(null);
                    }
                    String mobName = getMobDisplayName(type);
                    String ptext = String.format("§e★ §f%s: %d, %d, %d (%.0fm)", mobName, s.x, s.y, s.z, dist);
                    guiGraphics.item(new ItemStack(Items.NETHER_STAR), leftX, leftY);
                    guiGraphics.text(mc.font, ptext, leftX + 18, leftY + 4, 0xFFFFFF55, false);
                    leftY += 18;
                }
            }
        }

        guiGraphics.text(mc.font, "----------------", leftX, leftY, 0xFFAAAAAA, false);
        leftY += 14;

        if (!displayedSpawners.isEmpty()) {
            int count = expandedList() ? displayedSpawners.size() : Math.min(5, displayedSpawners.size());
            for (int i = 0; i < count; i++) {
                SpawnerInfo spawner = displayedSpawners.get(i);
                String mobName = getMobDisplayName(spawner.entityType);
                int color = spawner.isDestroyed ? 0xFF888888 : getColorForMobType(spawner.entityType);
                
                String text;
                if (spawner.isDestroyed) {
                    text = String.format("§7§m%s§r §c[X]: %d, %d, %d (%.1fm)", mobName, spawner.pos.getX(), spawner.pos.getY(),
                            spawner.pos.getZ(), spawner.distance);
                } else {
                    text = String.format("%s: %d, %d, %d (%.1fm)", mobName, spawner.pos.getX(), spawner.pos.getY(),
                            spawner.pos.getZ(), spawner.distance);
                }
                
                int textOffset = 20;
                if (spawner.isDestroyed) {
                    guiGraphics.item(new ItemStack(Items.BARRIER), leftX, leftY);
                } else if (spawner.isTrialSpawner) {
                    guiGraphics.item(new ItemStack(Items.TRIAL_SPAWNER), leftX, leftY);
                    guiGraphics.item(new ItemStack(getIconItem(spawner.entityType)), leftX + 16, leftY);
                    textOffset = 36;
                } else {
                    guiGraphics.item(new ItemStack(getIconItem(spawner.entityType)), leftX, leftY);
                }
                
                // Draw Text next to icon
                guiGraphics.text(mc.font, text, leftX + textOffset, leftY + 4, color, false);
                leftY += 18;
            }
        }

        // --- Right Side: Spawner Groups & Pinned Clusters ---
        String serverId = getCurrentServerOrWorldId(mc);
        SpawnerStorage storage = SpawnerStorage.getInstance(serverId);
        String currentDimension = world.dimension().identifier().toString();

        List<SpawnerStorage.SavedCluster> pinnedClusters = new ArrayList<>();
        if (SpawnerFinderConfig.getInstance().showPinned) {
            for (SpawnerStorage.SavedCluster c : storage.getAllClusters()) {
                if (c.isPinned && c.dimension.equals(currentDimension)) {
                    pinnedClusters.add(c);
                }
            }
        }

        if (!displayedGroups.isEmpty() || !pinnedClusters.isEmpty()) {
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

            if (!pinnedClusters.isEmpty()) {
                guiGraphics.text(mc.font, "§e★ Pinned Clusters:", rightX, rightY, 0xFFFFFF00, false);
                rightY += 12;

                int cCount = expandedList() ? pinnedClusters.size() : Math.min(3, pinnedClusters.size());
                for (int i = 0; i < cCount; i++) {
                    SpawnerStorage.SavedCluster c = pinnedClusters.get(i);
                    double dist = Math.sqrt(playerPos.distSqr(c.getActivationPos()));
                    String label = (c.mobNamesSummary != null && !c.mobNamesSummary.isEmpty()) ? c.mobNamesSummary : "Cluster of " + c.spawnerKeys.size();
                    String ctext = String.format("§e★ §f%s: %d, %d, %d (%.0fm)", label, c.activationX, c.activationY, c.activationZ, dist);
                    guiGraphics.item(new ItemStack(Items.NETHER_STAR), rightX, rightY);
                    guiGraphics.text(mc.font, ctext, rightX + 18, rightY + 4, 0xFFFFFF55, false);
                    rightY += 18;
                }
            }

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
                    
                    int textOffset = 25;
                    if (spawner.isTrialSpawner) {
                        guiGraphics.item(new ItemStack(Items.TRIAL_SPAWNER), rightX + 5, rightY);
                        guiGraphics.item(new ItemStack(getIconItem(spawner.entityType)), rightX + 21, rightY);
                        textOffset = 41;
                    } else {
                        guiGraphics.item(new ItemStack(getIconItem(spawner.entityType)), rightX + 5, rightY);
                    }
                    
                    // Draw text next to icon
                    guiGraphics.text(mc.font, spawnerText, rightX + textOffset, rightY + 4, getColorForMobType(spawner.entityType), false);
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

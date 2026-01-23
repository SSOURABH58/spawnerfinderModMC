package github.ssourabh58.spawnerfinder;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.entity.EntityType;
import java.util.*;

public class SpawnerRenderer implements WorldRenderEvents.Last, HudRenderCallback {

    public static boolean modEnabled = true;
    public static boolean expandedList = false;

    private static final List<SpawnerInfo> foundSpawners = new ArrayList<>();
    private static final List<SpawnerGroup> foundGroups = new ArrayList<>(); // New list for groups
    private static long lastScanTime = 0;
    private static final long SCAN_INTERVAL = 2000; // Scan every 2 seconds

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

    @Override
    public void onLast(WorldRenderContext context) {
        if (!modEnabled)
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;

        Level world = mc.level;
        BlockPos playerPos = mc.player.blockPosition();

        // Update spawner list periodically
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastScanTime > SCAN_INTERVAL) {
            scanForSpawners(world, playerPos);
            lastScanTime = currentTime;
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
                // Get chunk only if loaded (false arg) - using ChunkStatus.FULL to be precise
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

        // Greedy approach: For each spawner, try to build the largest valid group
        // including it
        for (int i = 0; i < foundSpawners.size(); i++) {
            List<SpawnerInfo> currentGroup = new ArrayList<>();
            currentGroup.add(foundSpawners.get(i));

            // Try adding all other spawners
            for (int j = 0; j < foundSpawners.size(); j++) {
                if (i == j)
                    continue;
                SpawnerInfo candidate = foundSpawners.get(j);

                // Add temporally to check validity
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

        // Filter valid unique maximal groups
        for (List<SpawnerInfo> groupList : potentialGroups) {
            // Check if this group is a subset of another already added group
            boolean isSubset = false;
            for (SpawnerGroup existing : foundGroups) {
                if (existing.spawners.containsAll(groupList)) {
                    isSubset = true;
                    break;
                }
                // Also check if we should replace a smaller subset
                if (groupList.containsAll(existing.spawners)) {
                    // The new one is bigger, but we handle that by just adding and filtering later
                    // or relying on order?
                    // Let's just avoid adding exact duplicates or subsets.
                }
            }

            if (!isSubset) {
                // Check if any existing group is a subset of THIS new group, if so, remove the
                // smaller one
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

    // Returns a valid BlockPos where all spawners are <= 16 blocks away, or null
    private BlockPos getValidActivationSpot(List<SpawnerInfo> list) {
        if (list.isEmpty())
            return null;

        // Calculate centroid
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

        // Verify centroid is within range of ALL spawners
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
        // Return packed ARGB color values
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

    private String getMobDisplayName(EntityType<?> entityType) {
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

    @Override
    public void onHudRender(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!modEnabled || mc.player == null)
            return;

        int lineHeight = 12;

        // --- Left Side: Found Spawners List ---
        int leftX = 10;
        int leftY = 10;

        String header = String.format("§6§lSpawners found: %d", foundSpawners.size());
        guiGraphics.drawString(mc.font, header, leftX, leftY, 0xFFFFFFFF, false);
        leftY += lineHeight;
        guiGraphics.drawString(mc.font, "----------------", leftX, leftY, 0xFFAAAAAA, false);
        leftY += lineHeight + 2;

        if (!foundSpawners.isEmpty()) {
            int count = expandedList ? foundSpawners.size() : Math.min(5, foundSpawners.size());
            for (int i = 0; i < count; i++) {
                SpawnerInfo spawner = foundSpawners.get(i);
                String mobName = getMobDisplayName(spawner.entityType);
                int color = getColorForMobType(spawner.entityType);
                String text = String.format("%s: %d, %d, %d (%.1fm)", mobName, spawner.pos.getX(), spawner.pos.getY(),
                        spawner.pos.getZ(), spawner.distance);
                guiGraphics.drawString(mc.font, text, leftX, leftY, color, false);
                leftY += lineHeight;
            }
        }

        // --- Right Side: Spawner Groups ---
        if (!foundGroups.isEmpty()) {
            int rightX = mc.getWindow().getGuiScaledWidth() - 200; // Align to right, with some padding
            int rightY = 10;

            String groupHeader = String.format("§b§lSpawner Groups found: %d", foundGroups.size());
            guiGraphics.drawString(mc.font, groupHeader, rightX, rightY, 0xFFFFFFFF, false);
            rightY += lineHeight;
            guiGraphics.drawString(mc.font, "----------------", rightX, rightY, 0xFFAAAAAA, false);
            rightY += lineHeight + 2;

            int groupCount = expandedList ? foundGroups.size() : Math.min(2, foundGroups.size());

            for (int i = 0; i < groupCount; i++) {
                SpawnerGroup group = foundGroups.get(i);
                String groupTitle = String.format("§eGroup of: %d", group.spawners.size());
                guiGraphics.drawString(mc.font, groupTitle, rightX, rightY, 0xFFFFFF00, false);
                rightY += lineHeight;

                String activationText = String.format("§7Activate: %d, %d, %d (%.1fm)",
                        group.activationPos.getX(), group.activationPos.getY(), group.activationPos.getZ(),
                        group.distanceToPlayer);
                guiGraphics.drawString(mc.font, activationText, rightX, rightY, 0xFFCCCCCC, false);
                rightY += lineHeight;

                for (SpawnerInfo spawner : group.spawners) {
                    String mob = getMobDisplayName(spawner.entityType);
                    String spawnerText = String.format("  %s: %d, %d, %d", mob, spawner.pos.getX(), spawner.pos.getY(),
                            spawner.pos.getZ());
                    guiGraphics.drawString(mc.font, spawnerText, rightX, rightY, getColorForMobType(spawner.entityType),
                            false);
                    rightY += lineHeight;
                }

                guiGraphics.drawString(mc.font, "-------------", rightX, rightY, 0xFFAAAAAA, false);
                rightY += lineHeight;
            }
        }
    }
}

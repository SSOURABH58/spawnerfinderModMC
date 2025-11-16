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
import net.minecraft.world.entity.EntityType;
import java.util.*;

public class SpawnerRenderer implements WorldRenderEvents.Last, HudRenderCallback {

    private static final List<SpawnerInfo> foundSpawners = new ArrayList<>();
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

    @Override
    public void onLast(WorldRenderContext context) {
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

        // No rendering - only HUD display
    }

    private void scanForSpawners(Level world, BlockPos playerPos) {
        foundSpawners.clear();
        int scanDistance = 128;

        for (int x = -scanDistance; x <= scanDistance; x++) {
            for (int y = -scanDistance; y <= scanDistance; y++) {
                for (int z = -scanDistance; z <= scanDistance; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);

                    if (world.getBlockState(pos).getBlock() == Blocks.SPAWNER) {
                        BlockEntity blockEntity = world.getBlockEntity(pos);
                        if (blockEntity instanceof SpawnerBlockEntity spawner) {
                            EntityType<?> entityType = getSpawnerEntityType(spawner, world, pos);
                            if (entityType != null) {
                                double distance = Math.sqrt(playerPos.distSqr(pos));
                                foundSpawners.add(new SpawnerInfo(pos, entityType, distance));
                            }
                        }
                    }
                }
            }
        }

        // Sort by distance
        foundSpawners.sort(Comparator.comparingDouble(s -> s.distance));
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
        if (entityType == EntityType.SKELETON) {
            return 0xFFFFFFFF; // White
        } else if (entityType == EntityType.ZOMBIE) {
            return 0xFF00FF00; // Green
        } else if (entityType == EntityType.SPIDER) {
            return 0xFFFF0000; // Red
        } else if (entityType == EntityType.CAVE_SPIDER) {
            return 0xFF0000FF; // Blue
        } else if (entityType == EntityType.MAGMA_CUBE) {
            return 0xFFFF8000; // Orange
        } else if (entityType == EntityType.BLAZE) {
            return 0xFFFFFF00; // Yellow
        } else if (entityType == EntityType.SILVERFISH) {
            return 0xFF808080; // Gray
        } else {
            return 0xFFFF00FF; // Magenta for unknown types
        }
    }

    @Override
    public void onHudRender(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || foundSpawners.isEmpty())
            return;

        // Display HUD with closest 5 spawners
        int hudX = 10;
        int hudY = 10;
        int lineHeight = 12;

        guiGraphics.drawString(mc.font, "§lClosest Spawners:", hudX, hudY, 0xFFFFFF);
        hudY += lineHeight + 2;

        int count = Math.min(5, foundSpawners.size());
        for (int i = 0; i < count; i++) {
            SpawnerInfo spawner = foundSpawners.get(i);
            String mobName = getMobDisplayName(spawner.entityType);
            int color = getColorForMobType(spawner.entityType);

            String text = String.format("%s: %d, %d, %d (%.1fm)",
                    mobName,
                    spawner.pos.getX(),
                    spawner.pos.getY(),
                    spawner.pos.getZ(),
                    spawner.distance);

            guiGraphics.drawString(mc.font, text, hudX, hudY, color);
            hudY += lineHeight;
        }
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
}

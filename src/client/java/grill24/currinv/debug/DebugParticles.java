package grill24.currinv.debug;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.component.ClientTick;
import grill24.currinv.component.Command;
import grill24.currinv.component.CommandOption;
import grill24.currinv.navigation.NavigationData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;

@Command("debug")
public class DebugParticles {

    @CommandOption("allParticlesEnabled")
    public static boolean isEnabled = true;

    public static final String NAVIGATION_PARTICLE_KEY = "navigation";
    public static final String SORTING_PARTICLE_KEY = "fullSuiteSorterTargets";
    public static final String SORTING_ACTIVE_CONTAINER_PARTICLE_KEY = "fullSuiteSorterActiveTarget";

    private static final HashMap<String, DebugParticleData> debugParticles = new HashMap<>();

    public static class DebugParticleData {
        public LinkedHashSet<BlockPos> blocks;
        public ParticleEffect particleEffect;

        public enum RenderType {BLOCK_OUTLINE, BLOCK_CENTER, PATH}

        public RenderType renderType;

        public DebugParticleData(LinkedHashSet<BlockPos> blocks, ParticleEffect particleEffect) {
            this.blocks = blocks;
            this.particleEffect = particleEffect;
            this.renderType = RenderType.BLOCK_OUTLINE;
        }

        public DebugParticleData(LinkedHashSet<BlockPos> blocks, ParticleEffect particleEffect, RenderType renderType) {
            this.blocks = blocks;
            this.particleEffect = particleEffect;
            this.renderType = renderType;
        }
    }

    @ClientTick(60)
    public static void spawnParticles(MinecraftClient client) {
        if (isEnabled) {
            debugParticles.forEach((key, debugParticleData) -> {
                spawnParticles(debugParticleData, client.world);
            });
        }
    }

    public static void printNavigationData(CommandContext<?> commandContext, NavigationData navigationData, ClientWorld world, ClientPlayerEntity player) {
        if (navigationData == null)
            return;

        DebugUtility.print(commandContext, "Current Node (" + navigationData.getCurrentPathNodeIndex() + "): " + navigationData.getCurrentNode());
        DebugUtility.print(commandContext, "Next Node: (" + (navigationData.getCurrentPathNodeIndex() + 1) + "): " + navigationData.getNextNode());
        DebugUtility.print(commandContext, "Player Pos: " + BigDecimal.valueOf(player.getX()).setScale(2, RoundingMode.HALF_UP) + ", " + BigDecimal.valueOf(player.getY()).setScale(2, RoundingMode.HALF_UP) + ", " + BigDecimal.valueOf(player.getZ()).setScale(2, RoundingMode.HALF_UP));
        DebugUtility.print(commandContext, "Path Length: " + navigationData.getPath().size());
    }

    public static void spawnParticles(DebugParticleData data, ClientWorld world) {
        spawnParticles(data.blocks, data.particleEffect, data.renderType, world);
    }

    public static void spawnParticles(LinkedHashSet<BlockPos> blocks, ParticleEffect particleEffect, DebugParticleData.RenderType renderType, ClientWorld world) {
        if (world != null) {
            Vec3d lastBlockCenterPos = null;
            for (BlockPos blockPos : blocks) {
                Vec3d centerPos = blockPos.toCenterPos();
                switch (renderType) {
                    case BLOCK_OUTLINE:
                        DebugUtility.getCorners(blockPos).forEach(corner -> world.addParticle(particleEffect, corner.x, corner.y, corner.z, 0, 0, 0));
                        DebugUtility.getEdgeCenters(blockPos).forEach(edgeCenter -> world.addParticle(particleEffect, edgeCenter.x, edgeCenter.y, edgeCenter.z, 0, 0, 0));
                        break;
                    case BLOCK_CENTER:
                        world.addParticle(particleEffect, centerPos.x, centerPos.y, centerPos.z, 0, 0, 0);
                        break;
                    case PATH:
                        Vec3d velocity = lastBlockCenterPos == null ? new Vec3d(0, 0, 0) : centerPos.subtract(lastBlockCenterPos).multiply(0.05);
                        DebugUtility.interpolateBetweenPoints(lastBlockCenterPos, centerPos, 3).forEach(interpolatedPos -> world.addParticle(particleEffect, interpolatedPos.x, interpolatedPos.y, interpolatedPos.z, velocity.x, velocity.y, velocity.z));
                        world.addParticle(particleEffect, centerPos.x, centerPos.y, centerPos.z, velocity.x, velocity.y, velocity.z);
                        lastBlockCenterPos = centerPos;
                        break;
                }
            }
        }
    }

    public static void setDebugParticles(String key, List<BlockPos> blockPosList, ParticleEffect particleEffect, DebugParticleData.RenderType renderType) {
        DebugParticleData debugParticleData = new DebugParticleData(new LinkedHashSet<>(blockPosList), particleEffect, renderType);
        DebugParticleData lastValue = debugParticles.put(key, debugParticleData);

        if (key.equals(SORTING_PARTICLE_KEY)) {
            // Print info on lastValue and new one
            if (lastValue != null)
                System.out.println("Last Value: " + lastValue.blocks);
            else
                System.out.println("Last Value: null");
            System.out.println("New Value: " + debugParticleData.blocks);
        }

        LinkedHashSet<BlockPos> difference = new LinkedHashSet<>(debugParticleData.blocks);
        if (lastValue != null)
            difference.removeAll(lastValue.blocks);

        if (MinecraftClient.getInstance().world != null && !difference.isEmpty())
            spawnParticles(difference, particleEffect, renderType, MinecraftClient.getInstance().world);
    }

    public static void setDebugParticles(String key, List<BlockPos> blockPosList, ParticleEffect particleEffect) {
        setDebugParticles(key, blockPosList, particleEffect, DebugParticleData.RenderType.BLOCK_OUTLINE);
    }

    public static boolean addDebugParticles(String key, List<BlockPos> blockPosList, ParticleEffect particleEffect) {
        DebugParticleData debugParticleData = debugParticles.getOrDefault(key, new DebugParticleData(new LinkedHashSet<>(), particleEffect));
        boolean modified = debugParticleData.blocks.addAll(blockPosList);
        debugParticles.put(key, debugParticleData);

        if (MinecraftClient.getInstance().world != null && modified)
            spawnParticles(debugParticleData, MinecraftClient.getInstance().world);
        return modified;
    }

    public static void removeDebugParticles(String key, List<BlockPos> blockPosList) {
        if (debugParticles.containsKey(key)) {
            DebugParticleData debugParticleData = debugParticles.get(key);
            blockPosList.forEach(debugParticleData.blocks::remove);
        }
    }

    public static void clearDebugParticles(String key) {
        debugParticles.remove(key);
    }
}

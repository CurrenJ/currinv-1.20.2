package grill24.currinv.sorting;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.screen.ScreenHandler;

import java.util.LinkedHashSet;
import java.util.List;

public class ScanNearbyChestsMode implements IFullSuiteSorterMode {
    @Override
    public List<LootableContainerBlockEntity> getContainersToVisit(MinecraftClient client) {
        int searchRadius = 16;
        int containerLimit = 100;

        LinkedHashSet<LootableContainerBlockEntity> containersToVisit = new LinkedHashSet<>();
        if(client.world != null && client.player != null) {

            for (int x = -searchRadius; x < searchRadius; x++) {
                for (int y = -searchRadius; y < searchRadius; y++) {
                    for (int z = -searchRadius; z < searchRadius; z++) {
                        if (containersToVisit.size() >= containerLimit) {
                            return containersToVisit.stream().toList();
                        }
                        BlockEntity blockEntity = client.world.getBlockEntity(client.player.getBlockPos().add(x, y, z));
                        if (blockEntity instanceof LootableContainerBlockEntity lootableContainerBlockEntity && !(blockEntity instanceof HopperBlockEntity) && !containersToVisit.contains(lootableContainerBlockEntity)) {
                            containersToVisit.add(lootableContainerBlockEntity);
                        }
                    }
                }
            }

        }
        return containersToVisit.stream().toList();
    }

    @Override
    public <T extends ScreenHandler> boolean doContainerScreenInteractionTick(MinecraftClient client, HandledScreen<T> screen) {
        return false;
    }

    @Override
    public boolean doContainerInteractionTick(MinecraftClient client) {
        return false;
    }
}

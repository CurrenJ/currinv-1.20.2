package grill24.currinv.sorting;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ScanNearbyChestsMode implements IFullSuiteSorterMode {
    @Override
    public List<LootableContainerBlockEntity> getContainersToVisit(MinecraftClient client) {
        int searchRadius = 16;
        int containerLimit = 100;

        LinkedHashSet<LootableContainerBlockEntity> containersToVisit = new LinkedHashSet<>();
        if (client.world != null && client.player != null) {

            for (int x = -searchRadius; x < searchRadius; x++) {
                for (int y = -searchRadius; y < searchRadius; y++) {
                    for (int z = -searchRadius; z < searchRadius; z++) {
                        if (containersToVisit.size() >= containerLimit) {
                            return new ArrayList<>(containersToVisit);
                        }
                        BlockEntity blockEntity = client.world.getBlockEntity(client.player.getBlockPos().add(x, y, z));
                        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity) {
                            LootableContainerBlockEntity lootableContainerBlockEntity = ((LootableContainerBlockEntity) blockEntity);
                            if (!containersToVisit.contains(lootableContainerBlockEntity))
                                containersToVisit.add(SortingUtility.getOneBlockEntityFromDoubleChests(client, lootableContainerBlockEntity));
                        }
                    }
                }
            }

        }
        return new ArrayList<>(containersToVisit);
    }

    @Override
    public boolean doContainerScreenInteractionTick(MinecraftClient client, Screen screen, List<LootableContainerBlockEntity> containersToVisit, int currentContainerIndex) {
        return true;
    }

    @Override
    public boolean doContainerInteractionTick(MinecraftClient client) {
        return false;
    }
}

package grill24.currinv.sorting;

import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.LinkedHashSet;

public class ScanNearbyChestsMode extends FullSuiteSorterMode {

    public ScanNearbyChestsMode(MinecraftClient client) {
        super(client);

        int searchRadius = 16;
        int containerLimit = 100;

        LinkedHashSet<LootableContainerBlockEntity> containersToVisit = new LinkedHashSet<>();
        if (client.world != null && client.player != null) {

            loop:
            for (int x = -searchRadius; x < searchRadius; x++) {
                for (int y = -searchRadius; y < searchRadius; y++) {
                    for (int z = -searchRadius; z < searchRadius; z++) {
                        if (containersToVisit.size() >= containerLimit) {
                            break loop;
                        }
                        BlockEntity blockEntity = client.world.getBlockEntity(client.player.getBlockPos().add(x, y, z));
                        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof BarrelBlockEntity) {
                            LootableContainerBlockEntity lootableContainerBlockEntity = (LootableContainerBlockEntity) blockEntity;
                            if (!containersToVisit.contains(lootableContainerBlockEntity))
                                containersToVisit.add(SortingUtility.getOneBlockEntityFromDoubleChests(client, lootableContainerBlockEntity));
                        }
                    }
                }
            }

        }
        this.containersToVisit = new ArrayList<>(containersToVisit);
    }

    @Override
    public boolean doContainerScreenInteractionTick(MinecraftClient client, Screen screen) {
        return true;
    }

    @Override
    public boolean doContainerInteractionTick(MinecraftClient client) {
        return false;
    }

    @Override
    public void onContainerAccessFail(MinecraftClient client) {

    }
}

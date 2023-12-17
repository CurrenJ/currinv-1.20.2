package grill24.currinv.sorting;

import grill24.currinv.CurrInvClient;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class CollectItemsMode extends FullSuiteSorterMode {

    private List<Item> itemsToCollect;

    public CollectItemsMode(MinecraftClient client, List<Item> itemsToCollect) {
        super(client);
        this.itemsToCollect = itemsToCollect;
    }

    public Supplier<LootableContainerBlockEntity> getContainersToVisitSupplier(MinecraftClient client) {
        if (client.world != null) {

            for (Item item : itemsToCollect) {
                Optional<ItemQuantityAndSlots> stock = CurrInvClient.fullSuiteSorter.allContainersStockData.getItemStock(item);
                if (stock.isPresent()) {
                    for (var entry : stock.get().slotIds.entrySet()) {
                        BlockPos blockPos = entry.getKey();
                        LootableContainerBlockEntity container = (LootableContainerBlockEntity) client.world.getBlockEntity(blockPos);
                        if (container != null) {
                            containersToVisit.add(container);
                        }
                    }
                }
            }

        }

        return super.getContainersToVisitSupplier(client);
    }


    @Override
    public boolean doContainerScreenInteractionTick(MinecraftClient client, Screen screen) {
        SortingUtility.collectItems(client, screen, itemsToCollect, CurrInvClient.fullSuiteSorter.allContainersStockData, false);
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

package grill24.currinv.sorting;

import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CollectItemsMode implements IFullSuiteSorterMode {

    private List<Item> itemsToCollect;
    private ContainerStockData allContainersStockData;

    @Override
    public List<LootableContainerBlockEntity> getContainersToVisit(MinecraftClient client) {
        ArrayList<LootableContainerBlockEntity> containersWithItems = new ArrayList<>();
        if (client.world != null) {

            for (Item item : itemsToCollect) {
                Optional<ItemQuantityAndSlots> stock = allContainersStockData.getItemStock(item);
                if (stock.isPresent()) {
                    for (var entry : stock.get().slotIds.entrySet()) {
                        BlockPos blockPos = entry.getKey();
                        LootableContainerBlockEntity container = (LootableContainerBlockEntity) client.world.getBlockEntity(blockPos);
                        if (container != null) {
                            containersWithItems.add(container);
                        }
                    }
                }
            }

        }
        return containersWithItems;
    }

    @Override
    public boolean doContainerScreenInteractionTick(MinecraftClient client, Screen screen, List<LootableContainerBlockEntity> containersToVisit, int currentContainerIndex) {
        SortingUtility.collectItems(client, screen, itemsToCollect, allContainersStockData, false);
        return true;
    }

    @Override
    public boolean doContainerInteractionTick(MinecraftClient client) {
        return false;
    }

    public void setItemsToCollect(List<Item> items) {
        itemsToCollect = items;
    }

    public void setStockData(ContainerStockData stockData) {
        allContainersStockData = stockData;
    }
}

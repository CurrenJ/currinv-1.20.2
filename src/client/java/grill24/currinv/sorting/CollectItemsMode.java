package grill24.currinv.sorting;

import grill24.currinv.CurrInvClient;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
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
        if(client.world != null) {

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
    public <T extends ScreenHandler> boolean doContainerScreenInteractionTick(MinecraftClient client, HandledScreen<T> screen) {
        assert client.player != null;
        assert client.interactionManager != null;

        Optional<Inventory> inventory = SortingUtility.tryGetInventoryFromScreen(screen);
        if (inventory.isPresent()) {

            for (Item item : itemsToCollect) {
                Optional<ItemQuantityAndSlots> stock = allContainersStockData.getItemStock(item);
                if (stock.isPresent()) {
                    // TODO: Not use this field from another class.
                    if (stock.get().slotIds.containsKey(CurrInvClient.sorter.lastUsedContainerBlockPos)) {
                        for (Integer slotId : stock.get().slotIds.get(CurrInvClient.sorter.lastUsedContainerBlockPos)) {
                            if (inventory.get().getStack(slotId).getItem().equals(item))
                                SortingUtility.clickSlot(client, screen, slotId, SlotActionType.QUICK_MOVE);
                        }
                    }
                }
            }

        }
        CurrInvClient.sorter.tryInventoryScreen(screen);

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

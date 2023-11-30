package grill24.currinv.sorting;

import grill24.currinv.IDirtyFlag;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class Sorter
{
    public boolean isEnabled, isQuickStackEnabled, isSorting;
    private int currentSortingSlotId, currentStockIndex, currentStockSlotIdsIndex;

    public BlockPos lastUsedContainerBlockPos;
    public HashMap<BlockPos, ContainerStockData> stockData;

    public Sorter()
    {
        isEnabled = false;
        isQuickStackEnabled = false;
        isSorting = false;

        currentSortingSlotId = 0;
        currentStockIndex = 0;
        currentStockSlotIdsIndex = 0;

        stockData = new HashMap<>();
    }

    public <T extends ScreenHandler> void onUpdate(MinecraftClient client, HandledScreen<T> screen)
    {
        Optional<Inventory> screenInventoryOptional = SortingUtility.tryGetInventoryFromScreen(screen);
        if(screenInventoryOptional.isPresent())
        {
            Optional<ContainerStockData> containerStockData = tryGetStockData(lastUsedContainerBlockPos);
            if(containerStockData.isPresent()) {
                if (!isSorting && containerStockData.get().isDirty()) {
                    stopSorting();
                    tryQuickStack(client, screen);
                    tryStartSortContainer(screen);
                }
                containerStockData.get().markClean();

                tryDoSortingTick(client, screen);
            }

        }
    }

    private <T extends ScreenHandler> boolean tryInventoryScreen(HandledScreen<T> screen)
    {
        Optional<Inventory> screenInventoryOptional = SortingUtility.tryGetInventoryFromScreen(screen);
        return screenInventoryOptional.map(this::tryInventoryInventory).orElse(false);
    }

    private boolean tryInventoryInventory(Inventory inventory)
    {
        if((isEnabled || isQuickStackEnabled) && !isSorting)
        {
            Optional<ContainerStockData> containerStockDataOptional = tryGetStockData(lastUsedContainerBlockPos);
            if(containerStockDataOptional.isPresent() && containerStockDataOptional.get().isDirty())
            {
                ContainerStockData containerStockData = containerStockDataOptional.get();
                containerStockData.inventoryInventory(lastUsedContainerBlockPos, inventory);

                containerStockDataOptional.get().markClean();
            }
            else
            {
                ContainerStockData containerStockData = new ContainerStockData(lastUsedContainerBlockPos, inventory);
                stockData.put(lastUsedContainerBlockPos, containerStockData);
            }
            return true;
        }
        return false;
    }

    private <T extends ScreenHandler> boolean tryStartSortContainer(HandledScreen<T> screen)
    {
        if(!isSorting) {
            if(isValidSortingConditions(screen) && tryInventoryScreen(screen)) {
                isSorting = true;
                return true;
            }
        }
        return false;
    }

    private <T extends ScreenHandler> boolean tryDoSortingTick(MinecraftClient client, HandledScreen<T> screen) {
        assert client.player != null;

        if(isSorting && isValidSortingConditions(screen)) {
            Optional<ContainerStockData> containerStockDataOptional = tryGetStockData(lastUsedContainerBlockPos);
            if(containerStockDataOptional.isPresent()) {
                ContainerStockData containerStockData = containerStockDataOptional.get();
                return tryDoSortingTick(client, screen, containerStockData);
            }
        }
        stopSorting();
        return false;
    }

    private <T extends ScreenHandler> boolean tryDoSortingTick(MinecraftClient client, HandledScreen<T> screen, ContainerStockData containerStockData) {
        if(currentStockIndex >= containerStockData.getOrderedStock().size()) {
            stopSorting();
            return false;
        }

        ArrayList<ItemQuantityAndSlots> orderedStock = containerStockData.getOrderedStock();

        // Get the item stack that we are currently sorting.
        ItemQuantityAndSlots itemQuantityAndSlots = orderedStock.get(currentStockIndex);

        // Pick up item stack to be sorted.
        int slotId = itemQuantityAndSlots.slotIds.get(lastUsedContainerBlockPos).get(currentStockSlotIdsIndex);
        SortingUtility.clickSlot(client, screen, slotId);

        // Safety check
        if (!screen.getScreenHandler().getCursorStack().getItem().equals(itemQuantityAndSlots.item)) {
            // Something went wrong. The contents of this screenInventory are not what we expect.
            stopSorting();
            // Hey! Drop it!
            if (!screen.getScreenHandler().getCursorStack().isEmpty()) {
                SortingUtility.clickSlot(client, screen, slotId);
            }

            markDirty();
            return false;
        }

        // Check if we can merge it with the last slot we sorted into.
        // If so, do it.
        boolean canStackWithLastSlot = ScreenHandler.canInsertItemIntoSlot(screen.getScreenHandler().slots.get(
                        Math.max(currentSortingSlotId - 1, 0)),
                screen.getScreenHandler().getCursorStack(),
                true);
        if (canStackWithLastSlot) {
            SortingUtility.clickSlot(client, screen, currentSortingSlotId - 1);
        }

        // Place down any remaining item stack in the cursor into our current sorting slot.
        if (!screen.getScreenHandler().getCursorStack().isEmpty()) {
            SortingUtility.clickSlot(client, screen, currentSortingSlotId);
            currentSortingSlotId++;
        }

        // What's left in the cursor is what was previously in the slot that we sorted into.
        // So, adjust our stock analysis according to the new slot of this item stack.
        ItemStack cursorItemStack = screen.getScreenHandler().getCursorStack();
        Item cursorItem = cursorItemStack.getItem();
        if (!cursorItemStack.isEmpty()) {
            SortingUtility.clickSlot(client, screen, slotId);

            Optional<ItemQuantityAndSlots> displacedStock = containerStockData.getItemStock(cursorItem);
            if (displacedStock.isPresent()) {
                for (int i = 0; i < displacedStock.get().slotIds.get(lastUsedContainerBlockPos).size(); i++) {
                    int stockSlotId = displacedStock.get().slotIds.get(lastUsedContainerBlockPos).get(i);

                    // If our stored stock slot is the slot that we just swapped an item stack into, then update accordingly.
                    if (stockSlotId == currentSortingSlotId - 1) {
                        displacedStock.get().slotIds.get(lastUsedContainerBlockPos).set(i, slotId);
                    }
                }
            }
        }

        if (currentStockSlotIdsIndex < itemQuantityAndSlots.slotIds.get(lastUsedContainerBlockPos).size() - 1) {
            currentStockSlotIdsIndex++;
        } else {
            currentStockSlotIdsIndex = 0;
            currentStockIndex++;

            if (currentStockIndex == orderedStock.size()) {
                stopSorting();
            }
        }

        return true;
    }

    private void stopSorting()
    {
        isSorting = false;
        currentSortingSlotId = 0;
        currentStockIndex = 0;
        currentStockSlotIdsIndex = 0;
    }

    private <T extends ScreenHandler> boolean tryQuickStack(MinecraftClient client, HandledScreen<T> screen)
    {
        if(isValidQuickStackConditions(screen))
        {
            assert client.player != null;
            Inventory playerInventory = client.player.getInventory();

            for(int i = SortingUtility.PLAYER_HOTBAR_SLOTS_END_INDEX; i < playerInventory.size(); i++)
            {
                ItemStack itemStack = playerInventory.getStack(i);
                Item item = itemStack.getItem();

                if(item.getMaxCount() > 1) {
                    Optional<ContainerStockData> stockData = tryGetStockData(lastUsedContainerBlockPos);
                    if(stockData.isPresent()) {
                        Optional<ItemQuantityAndSlots> itemStock = stockData.get().getItemStock(item);
                        if (itemStock.isPresent()) {
                            OptionalInt slotId = screen.getScreenHandler().getSlotIndex(playerInventory, i);
                            if (slotId.isPresent()) {
                                SortingUtility.clickSlot(client, screen, slotId.getAsInt(), SlotActionType.QUICK_MOVE);
                                markDirty();
                            }
                        }
                    }
                }
            }

            return true;
        }
        return false;
    }

    public Optional<ContainerStockData> tryGetStockData(BlockPos blockPos)
    {
        if(stockData.containsKey(blockPos))
            return Optional.of(stockData.get(blockPos));
        return Optional.empty();
    }

    public void onUseContainer(LootableContainerBlockEntity containerBlockEntity) {
        lastUsedContainerBlockPos = containerBlockEntity.getPos();
        tryInventoryInventory(containerBlockEntity);
    }

    private <T extends ScreenHandler> boolean isValidSortingConditions(HandledScreen<T> screen)
    {
        return isEnabled && screen.getScreenHandler().getCursorStack().isEmpty();
    }

    private <T extends ScreenHandler> boolean isValidQuickStackConditions(HandledScreen<T> screen)
    {
        return isQuickStackEnabled && screen.getScreenHandler().getCursorStack().isEmpty();
    }

    public void markDirty()
    {
        tryGetStockData(lastUsedContainerBlockPos).ifPresent(IDirtyFlag::markDirty);
    }
}

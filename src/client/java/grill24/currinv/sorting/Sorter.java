package grill24.currinv.sorting;

import grill24.currinv.IDirtyFlag;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.*;

public class Sorter implements IDirtyFlag
{
    public static final int PLAYER_HOTBAR_SLOTS_END_INDEX = 9;

    public Sorter()
    {
        isDirty = false;

        isEnabled = false;
        isQuickStackEnabled = false;
        isSorting = false;

        currentSortingSlotId = 0;
        currentStockIndex = 0;
        currentStockSlotIdsIndex = 0;

        stockData = new StockData();
        orderedStock = new ArrayList<>();
    }

    public boolean isDirty;
    public boolean isEnabled, isQuickStackEnabled, isSorting;
    private int currentSortingSlotId, currentStockIndex, currentStockSlotIdsIndex;

    public StockData stockData;
    private ArrayList<ItemQuantityAndSlots> orderedStock;

    public void orderStock()
    {
        // Sort items by quantity
        orderedStock.clear();
        for (Map.Entry<Item, ItemQuantityAndSlots> entry: stockData.getStock().entrySet()) {
            System.out.println(entry.getValue());
            orderedStock.add(entry.getValue());
        }
        orderedStock.sort(Collections.reverseOrder());
    }

    public <T extends ScreenHandler> void OnUpdate(MinecraftClient client, HandledScreen<T> screen, Inventory screenInventory)
    {
        tryInventoryInventory(screenInventory);

        if(isDirty()) {
            tryStartSortContainer(screen);
            tryQuickStack(client, screen);
        }
        markClean();

        tryDoSortingTick(client, screen, screenInventory);
    }

    public boolean tryInventoryInventory(Inventory screenInventory)
    {
        if((isEnabled || isQuickStackEnabled) && !isSorting && stockData.isDirty())
        {
            stockData.reset();
            stockData.inventoryInventory(screenInventory);
            return true;
        }
        return false;
    }

    public <T extends ScreenHandler> boolean tryStartSortContainer(HandledScreen<T> screen)
    {
        if(!isSorting) {
            orderStock();
            if(isValidSortingConditions(screen)) {
                isSorting = true;
                return true;
            }
        }
        return false;
    }

    public <T extends ScreenHandler> boolean tryDoSortingTick(MinecraftClient client, HandledScreen<T> screen, Inventory screenInventory) {
        if(isSorting && isValidSortingConditions(screen)) {
            assert client.player != null;

            ItemQuantityAndSlots itemQuantityAndSlots = orderedStock.get(currentStockIndex);

            // Pick up item stack to be sorted.
            int slotId = itemQuantityAndSlots.slotIds.get(currentStockSlotIdsIndex);
            clickSlot(client, screen, slotId);

            // Safety check
            if(!screen.getScreenHandler().getCursorStack().getItem().equals(itemQuantityAndSlots.item)) {
                // Something went wrong. The contents of this screenInventory are not what we expect.
                stopSorting();
                // Hey! Drop it!
                if(!screen.getScreenHandler().getCursorStack().isEmpty()){
                    clickSlot(client, screen, slotId);
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
            if(canStackWithLastSlot)
            {
                clickSlot(client, screen, currentSortingSlotId-1);
            }

            // Place down any remaining item stack in the cursor into our current sorting slot.
            if(!screen.getScreenHandler().getCursorStack().isEmpty()) {
                clickSlot(client, screen, currentSortingSlotId);
                currentSortingSlotId++;
            }

            // What's left in the cursor is what was previously in the slot that we sorted into.
            // So, adjust our stock analysis according to the new slot of this item stack.
            ItemStack cursorItemStack = screen.getScreenHandler().getCursorStack();
            Item cursorItem = cursorItemStack.getItem();
            if(!cursorItemStack.isEmpty()) {
                clickSlot(client, screen, slotId);

                Optional<ItemQuantityAndSlots> displacedStock = stockData.getItemStock(cursorItem);
                if(displacedStock.isPresent()) {
                    for (int i = 0; i < displacedStock.get().slotIds.size(); i++) {
                        int stockSlotId = displacedStock.get().slotIds.get(i);

                        // If our stored stock slot is the slot that we just swapped an item stack into, then update accordingly.
                        if (stockSlotId == currentSortingSlotId - 1) {
                            displacedStock.get().slotIds.set(i, slotId);
                        }
                    }
                }
            }

            if(currentStockSlotIdsIndex < itemQuantityAndSlots.slotIds.size() - 1) {
                currentStockSlotIdsIndex++;
            }
            else {
                currentStockSlotIdsIndex = 0;
                currentStockIndex++;

                if(currentStockIndex == orderedStock.size()) {
                    stopSorting();
                }
            }

            markDirty();
            return true;
        }
        stopSorting();
        return false;
    }

    public void stopSorting()
    {
        isSorting = false;
        currentSortingSlotId = 0;
        currentStockIndex = 0;
        currentStockSlotIdsIndex = 0;
    }

    public <T extends ScreenHandler> boolean tryQuickStack(MinecraftClient client, HandledScreen<T> screen)
    {
        if(isValidQuickStackConditions(screen))
        {
            assert client.player != null;
            Inventory playerInventory = client.player.getInventory();

            for(int i = PLAYER_HOTBAR_SLOTS_END_INDEX; i < playerInventory.size(); i++)
            {
                ItemStack itemStack = playerInventory.getStack(i);
                Item item = itemStack.getItem();

                if(item.getMaxCount() > 1) {
                    Optional<ItemQuantityAndSlots> itemStock = stockData.getItemStock(item);
                    if (itemStock.isPresent()) {
                        OptionalInt slotId = screen.getScreenHandler().getSlotIndex(playerInventory, i);
                        if (slotId.isPresent()) {
                            clickSlot(client, screen, slotId.getAsInt(), SlotActionType.QUICK_MOVE);
                            markDirty();
                        }
                    }
                }
            }

            return true;
        }
        return false;
    }

    public <T extends ScreenHandler> boolean isValidSortingConditions(HandledScreen<T> screen)
    {
        return isEnabled && !orderedStock.isEmpty() && screen.getScreenHandler().getCursorStack().isEmpty();
    }

    public <T extends ScreenHandler> boolean isValidQuickStackConditions(HandledScreen<T> screen)
    {
        return isQuickStackEnabled && screen.getScreenHandler().getCursorStack().isEmpty();
    }

    @Override
    public boolean isDirty() {
        return isDirty;
    }

    @Override
    public void markDirty()
    {
        stockData.markDirty();
        isDirty = true;
    }

    @Override
    public void markClean() {
        isDirty = false;
    }

    public static <T extends ScreenHandler> void clickSlot(MinecraftClient client, HandledScreen<T> screen, int slotId)
    {
        clickSlot(client, screen, slotId, SlotActionType.PICKUP);
    }

    public static <T extends ScreenHandler> void clickSlot(MinecraftClient client, HandledScreen<T> screen, int slotId, SlotActionType slotActionType)
    {
        assert client.interactionManager != null;
        client.interactionManager.clickSlot(screen.getScreenHandler().syncId, slotId, 0, slotActionType, client.player);
    }
}

package grill24.currinv.sorting;

import grill24.currinv.IDirtyFlag;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Optional;

public class StockData implements IDirtyFlag {
    private HashMap<Item, ItemQuantityAndSlots> stock;
    private boolean isDirty;

    public StockData()
    {
        initialize();
    }

    public StockData(Inventory inventory)
    {
        initialize();
        inventoryInventory(inventory);
    }

    private void initialize()
    {
        stock = new HashMap<>();
        isDirty = false;
    }


    // Inventories an inventory.
    public void inventoryInventory(Inventory inventory)
    {
        // Count total quantities of items and save which slots they are in
        for(int slotId = 0; slotId < inventory.size(); slotId++)
        {
            ItemStack itemStack = inventory.getStack(slotId);
            Item item = itemStack.getItem();
            if(!itemStack.isEmpty()) {
                ItemQuantityAndSlots itemQuantityAndSlots = stock.getOrDefault(item, new ItemQuantityAndSlots(item));
                itemQuantityAndSlots.incrementQuantity(itemStack.getCount());
                itemQuantityAndSlots.addSlotId(slotId);
                stock.put(item, itemQuantityAndSlots);
            }
        }
        markClean();
    }

    public Optional<ItemQuantityAndSlots> getItemStock(Item item)
    {
        return Optional.ofNullable(stock.getOrDefault(item, null));
    }

    public HashMap<Item, ItemQuantityAndSlots> getStock()
    {
        return stock;
    }

    public void reset()
    {
        stock.clear();
    }

    @Override
    public void markDirty()
    {
        isDirty = true;
    }

    @Override
    public void markClean() {
        isDirty = false;
    }

    @Override
    public boolean isDirty()
    {
        return isDirty;
    }
}

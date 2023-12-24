package grill24.currinv.sorting;

import grill24.currinv.IDirtyFlag;
import grill24.sizzlib.persistence.PersistenceManager;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class ContainerStockData implements IDirtyFlag {
    private final HashMap<Item, ItemQuantityAndSlots> stock;
    private final ArrayList<ItemQuantityAndSlots> orderedStock;
    private transient boolean isDirty;

    ContainerStockData() {
        isDirty = false;

        stock = new HashMap<>();
        orderedStock = new ArrayList<>();
    }

    ContainerStockData(BlockPos blockPos, Inventory inventory) {
        isDirty = true;

        stock = new HashMap<>();
        orderedStock = new ArrayList<>();

        inventoryInventory(blockPos, inventory);
    }

    ContainerStockData(List<ContainerStockData> containerStockData) {
        isDirty = false;

        stock = new HashMap<>();
        orderedStock = new ArrayList<>();

        // Merge all stock data into one
        for (ContainerStockData containerStockDatum : containerStockData) {
            for (Map.Entry<Item, ItemQuantityAndSlots> entry : containerStockDatum.getStock().entrySet()) {
                ItemQuantityAndSlots itemQuantityAndSlots = stock.getOrDefault(entry.getKey(), new ItemQuantityAndSlots(entry.getKey()));
                itemQuantityAndSlots.incrementQuantity(entry.getValue().quantity);
                // Add slot ids
                for (Map.Entry<BlockPos, ArrayList<Integer>> slotIds : entry.getValue().slotIds.entrySet()) {
                    for (Integer slotId : slotIds.getValue()) {
                        itemQuantityAndSlots.addSlotId(slotIds.getKey(), slotId);
                    }
                }
                stock.put(entry.getKey(), itemQuantityAndSlots);
            }
        }
        orderStock();
    }


    // Inventories an inventory.
    public void inventoryInventory(BlockPos blockPos, Inventory inventory) {
        stock.clear();
        // Count total quantities of items and save which slots they are in
        for (int slotId = 0; slotId < inventory.size(); slotId++) {
            ItemStack itemStack = inventory.getStack(slotId);
            Item item = itemStack.getItem();
            if (!itemStack.isEmpty()) {
                ItemQuantityAndSlots itemQuantityAndSlots = stock.getOrDefault(item, new ItemQuantityAndSlots(item));
                itemQuantityAndSlots.incrementQuantity(itemStack.getCount());
                itemQuantityAndSlots.addSlotId(blockPos, slotId);
                stock.put(item, itemQuantityAndSlots);
            }
        }

        orderStock();
    }

    public Optional<ItemQuantityAndSlots> getItemStock(Item item) {
        return Optional.ofNullable(stock.getOrDefault(item, null));
    }

    public HashMap<Item, ItemQuantityAndSlots> getStock() {
        return stock;
    }

    public void orderStock() {
        orderedStock.clear();
        for (Map.Entry<Item, ItemQuantityAndSlots> entry : stock.entrySet()) {
//            System.out.println(entry.getValue());
            orderedStock.add(entry.getValue());
        }
        orderedStock.sort(Collections.reverseOrder());
    }

    public ArrayList<ItemQuantityAndSlots> getOrderedStock() {
        return orderedStock;
    }

    public void reset() {
        stock.clear();
    }

    @Override
    public void markDirty() {
        isDirty = true;
    }

    @Override
    public void markClean() {
        isDirty = false;
    }

    @Override
    public boolean isDirty() {
        return isDirty;
    }
}

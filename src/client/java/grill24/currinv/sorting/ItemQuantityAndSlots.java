package grill24.currinv.sorting;

import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;

//
public class ItemQuantityAndSlots implements Comparable<ItemQuantityAndSlots> {
    public Item item;
    public int quantity;
    public HashMap<BlockPos, ArrayList<Integer>> slotIds;

    public ItemQuantityAndSlots(Item item) {
        this.item = item;
        quantity = 0;
        slotIds = new HashMap<>();
    }

    public void addSlotId(BlockPos blockPos, int slotId) {
        if(slotIds.containsKey(blockPos))
            slotIds.get(blockPos).add(slotId);
        else
            slotIds.put(blockPos, new ArrayList<>() {{ add(slotId); }});
    }

    public void incrementQuantity(int amount) {
        quantity += amount;
    }

    public String toString() {
        StringBuilder str = new StringBuilder().append(item.getName().getString()).append(" (").append(quantity).append("): ");
        for(var item : slotIds.entrySet()) {
            str.append(item.getKey()).append(": [ ");
            for (int slotId : item.getValue()) {
                str.append(slotId).append(" ");
            }
        }
        str.append("]");
        return str.toString();
    }

    @Override
    public int compareTo(@NotNull ItemQuantityAndSlots o) {
        int quantityComparison = Integer.compare(this.quantity, o.quantity);

        // How we neatly sort non-stackables; i.e. Music Discs
        if (this.item.getMaxCount() == 1 || o.item.getMaxCount() == 1) {
            // Max stack, then lexicographical
            // Sort by max stack first, so that non-stackables are sorted to the tail of the container
            int maxStack = Integer.compare(this.item.getMaxCount(), o.item.getMaxCount());
            if (maxStack != 0) {
                return maxStack;
            }
//                else if(quantityComparison != 0) {
//                    return quantityComparison;
//                }
            else {
                return this.item.toString().compareTo(o.item.toString());
            }
        }
        // Typical case: sort by quantity
        else if (quantityComparison != 0) {
            return quantityComparison;
        }
        // Fallback to lexicographical
        else {
            return this.item.toString().compareTo(o.item.toString());
        }
    }
}

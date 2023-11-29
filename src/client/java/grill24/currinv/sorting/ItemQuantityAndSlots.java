package grill24.currinv.sorting;

import net.minecraft.item.Item;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

//
public class ItemQuantityAndSlots implements Comparable<ItemQuantityAndSlots> {
    public Item item;
    public int quantity;
    public ArrayList<Integer> slotIds;

    public ItemQuantityAndSlots(Item item) {
        this.item = item;
        quantity = 0;
        slotIds = new ArrayList<>();
    }

    public void addSlotId(int slotId) {
        slotIds.add(slotId);
    }

    public void incrementQuantity(int amount) {
        quantity += amount;
    }

    public String toString() {
        StringBuilder str = new StringBuilder(item.getName().getString() + ": " + quantity + " [ ");
        for (int slotId : slotIds) {
            str.append(slotId).append(" ");
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

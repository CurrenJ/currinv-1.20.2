package grill24.currinv.sorting;

import grill24.currinv.CurrInvClient;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

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
        if (slotIds.containsKey(blockPos))
            slotIds.get(blockPos).add(slotId);
        else
            slotIds.put(blockPos, new ArrayList<>() {{
                add(slotId);
            }});
    }

    public void incrementQuantity(int amount) {
        quantity += amount;
    }

    public String toString() {
        StringBuilder str = new StringBuilder().append(item.getName().getString()).append(" (").append(quantity).append("): ");
        for (var item : slotIds.entrySet()) {
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
        return switch (CurrInvClient.config.currentSortingStyle) {
            case QUANTITY -> compareByQuantity(o);
            case LEXICOGRAPHICAL -> compareByLexicographical(o);
            case CREATIVE_MENU -> compareByCreativeMenuOrder(o);
            default -> 0;
        };
    }

    public int compareByQuantity(ItemQuantityAndSlots o) {
        int quantityComparison = Integer.compare(this.quantity, o.quantity);

        // How we neatly sort non-stackables; i.e. Music Discs
        if (this.item.getMaxCount() == 1 || o.item.getMaxCount() == 1) {
            // Max stack, then lexicographical
            // Sort by max stack first, so that non-stackables are sorted to the tail of the container
            int maxStack = Integer.compare(this.item.getMaxCount(), o.item.getMaxCount());
            if (maxStack != 0) {
                return maxStack;
            } else {
                return this.item.toString().compareTo(o.item.toString());
            }
        }
        // Typical case: sort by quantity
        else if (quantityComparison != 0) {
            return quantityComparison;
        }
        // Fallback to lexicographical
        else {
            return compareByLexicographical(o);
        }
    }

    public int compareByLexicographical(ItemQuantityAndSlots o) {
        return o.item.getName().getString().compareTo(this.item.getName().getString());
    }

    public int compareByCreativeMenuOrder(ItemQuantityAndSlots o) {
        if (CurrInvClient.sorter.creativeMenuOrder != null) {
            int myIndex = CurrInvClient.sorter.creativeMenuOrder.getOrDefault(this.item.asItem(), Integer.MAX_VALUE);
            int otherIndex = CurrInvClient.sorter.creativeMenuOrder.getOrDefault(o.item.asItem(), Integer.MAX_VALUE);

            return Integer.compare(otherIndex, myIndex);
        } else {
            return 0;
        }
    }
}

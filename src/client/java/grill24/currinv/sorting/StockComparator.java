package grill24.currinv.sorting;

import java.util.Comparator;

public class StockComparator implements Comparator<ItemQuantityAndSlots> {
    public Sorter.SortingStyle sortingStyle;

    public StockComparator(Sorter.SortingStyle sortingStyle) {
        super();

        this.sortingStyle = sortingStyle;
    }

    @Override
    public int compare(ItemQuantityAndSlots o1, ItemQuantityAndSlots o2) {
        return switch (sortingStyle) {
            case QUANTITY -> o1.compareByQuantity(o2);
            case LEXICOGRAPHICAL -> o1.compareByLexicographical(o2);
            case CREATIVE_MENU -> o1.compareByCreativeMenuOrder(o2);
        };
    }
}

package grill24.currinv.persistence;

import grill24.currinv.sorting.Sorter;
import net.minecraft.nbt.NbtCompound;

public class Config implements IPersistable {
    private final static String DATA_FILE_NAME = "config.dat";

    private final static String CURRENT_SORTING_STYLE_KEY = "currentSortingStyle";
    public Sorter.SortingStyle currentSortingStyle = Sorter.SortingStyle.QUANTITY;
    public Config() {
        PersistenceManager.load(this);
    }

    public void setSortingStyle(Sorter.SortingStyle style) {
        currentSortingStyle = style;
        PersistenceManager.save(this);
    }

    @Override
    public NbtCompound writeToNBT() {
        NbtCompound tag = new NbtCompound();

        tag.putString(CURRENT_SORTING_STYLE_KEY, currentSortingStyle.name());

        return tag;
    }

    @Override
    public void readFromNBT(NbtCompound tag) {
        currentSortingStyle = Sorter.SortingStyle.valueOf(tag.getString(CURRENT_SORTING_STYLE_KEY));
    }

    @Override
    public String getFileName() {
        return DATA_FILE_NAME;
    }

    @Override
    public String toString() {
        return "Config{" +
                "currentSortingStyle=" + currentSortingStyle +
                '}';
    }
}

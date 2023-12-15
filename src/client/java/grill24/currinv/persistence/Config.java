package grill24.currinv.persistence;

import grill24.currinv.sorting.Sorter;
import grill24.sizzlib.component.Command;
import grill24.sizzlib.component.CommandOption;
import net.minecraft.nbt.NbtCompound;

@Command("config")
public class Config implements IPersistable {
    private final static String DATA_FILE_NAME = "config.dat";

    private final static String CURRENT_SORTING_STYLE_KEY = "sortingStyle";
    @CommandOption(value = CURRENT_SORTING_STYLE_KEY, setter = "setCurrentSortingStyle")
    public Sorter.SortingStyle currentSortingStyle = Sorter.SortingStyle.QUANTITY;

    public Config() {
        PersistenceManager.load(this);
    }

    public void setCurrentSortingStyle(Sorter.SortingStyle currentSortingStyle) {
        this.currentSortingStyle = currentSortingStyle;
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

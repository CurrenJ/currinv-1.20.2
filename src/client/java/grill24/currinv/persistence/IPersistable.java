package grill24.currinv.persistence;

import net.minecraft.nbt.NbtCompound;

public interface IPersistable {
    void readFromNBT(NbtCompound tag);

    NbtCompound writeToNBT();

    String getFileName();
}

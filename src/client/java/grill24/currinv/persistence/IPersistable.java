package grill24.currinv.persistence;

import net.minecraft.nbt.NbtCompound;

public interface IPersistable {
    public void readFromNBT(NbtCompound tag);
    public NbtCompound writeToNBT();
    public String getFileName();
}

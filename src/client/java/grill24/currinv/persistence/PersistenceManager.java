package grill24.currinv.persistence;

import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PersistenceManager {
    private static final String RELATIVE_MOD_DATA_DIR = "data/currinv/";

    public static void save(IPersistable persistable) {
        Path modDataDirectory = getModDataDirectory();
        if (!modDataDirectory.toFile().exists()) {
            modDataDirectory.toFile().mkdirs();
        }

        try {
            File file = new File(getModDataDirectory().toFile(), persistable.getFileName());
            NbtCompound tag = persistable.writeToNBT();
            NbtIo.writeCompressed(tag, file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void load(IPersistable persistable) {
        try {
            File file = new File(getModDataDirectory().toFile(), persistable.getFileName());
            if (file.exists()) {
                NbtCompound tag = NbtIo.readCompressed(file);
                persistable.readFromNBT(tag);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Path getModDataDirectory() {
        return Paths.get(String.valueOf(MinecraftClient.getInstance().runDirectory), RELATIVE_MOD_DATA_DIR);
    }
}

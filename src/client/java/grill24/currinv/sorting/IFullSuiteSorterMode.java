package grill24.currinv.sorting;

import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.function.Supplier;

public interface IFullSuiteSorterMode {

    Supplier<LootableContainerBlockEntity> getContainersToVisitSupplier(MinecraftClient client);

    boolean doContainerScreenInteractionTick(MinecraftClient client, Screen screen);

    boolean doContainerInteractionTick(MinecraftClient client);

    void onContainerAccessFail(MinecraftClient client);
}

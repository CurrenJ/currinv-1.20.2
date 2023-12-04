package grill24.currinv.sorting;

import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;

import java.util.List;

public interface IFullSuiteSorterMode {

    List<LootableContainerBlockEntity> getContainersToVisit(MinecraftClient client);

    <T extends ScreenHandler> boolean doContainerScreenInteractionTick(MinecraftClient client, HandledScreen<T> screen, List<LootableContainerBlockEntity> containersToVisit, int currentContainerIndex);
    boolean doContainerInteractionTick(MinecraftClient client);
}

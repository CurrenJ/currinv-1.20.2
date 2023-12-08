package grill24.currinv.sorting;

import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.List;

public interface IFullSuiteSorterMode {

    List<LootableContainerBlockEntity> getContainersToVisit(MinecraftClient client);

    boolean doContainerScreenInteractionTick(MinecraftClient client, Screen screen, List<LootableContainerBlockEntity> containersToVisit, int currentContainerIndex);

    boolean doContainerInteractionTick(MinecraftClient client);
}

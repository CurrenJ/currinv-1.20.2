package grill24.currinv.sorting;

import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.function.Supplier;

public abstract class FullSuiteSorterMode implements IFullSuiteSorterMode {
    protected ArrayList<LootableContainerBlockEntity> containersToVisit = new ArrayList<>();
    protected int currentContainerIndex = 0;

    public FullSuiteSorterMode(MinecraftClient client) {
        containersToVisit = new ArrayList<>();
        currentContainerIndex = -1;
    }

    @Override
    public Supplier<LootableContainerBlockEntity> getContainersToVisitSupplier(MinecraftClient client) {
        return () -> {
            currentContainerIndex++;
            if (currentContainerIndex >= containersToVisit.size())
                return null;

            LootableContainerBlockEntity lootableContainerBlockEntity = containersToVisit.get(currentContainerIndex);
            return lootableContainerBlockEntity;
        };
    }

    @Override
    public abstract boolean doContainerScreenInteractionTick(MinecraftClient client, Screen screen);

    @Override
    public abstract boolean doContainerInteractionTick(MinecraftClient client);

    @Override
    public abstract void onContainerAccessFail(MinecraftClient client);
}

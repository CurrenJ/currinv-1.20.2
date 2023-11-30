package grill24.currinv.sorting;

import grill24.currinv.BlockEntityWithInventory;
import grill24.currinv.ScreenWithInventory;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Optional;

public class SortingUtility {
    public static final int PLAYER_HOTBAR_SLOTS_END_INDEX = 9;

    public static <T extends ScreenHandler> Optional<Inventory> tryGetInventoryFromScreen(HandledScreen<T> screen)
    {
        if(screen instanceof GenericContainerScreen)
        {
            return Optional.of(((GenericContainerScreen) screen).getScreenHandler().getInventory());
        }
        if(screen instanceof ShulkerBoxScreen)
        {
            ShulkerBoxScreenHandler shulkerBoxScreenHandler = ((ShulkerBoxScreen) screen).getScreenHandler();
            ScreenWithInventory screenWithInventory = ((ScreenWithInventory) shulkerBoxScreenHandler);
            return Optional.of(screenWithInventory.currinv_1_20_2$getInventory());
        }
        return Optional.empty();
    }

    public static <T extends ScreenHandler> void clickSlot(MinecraftClient client, HandledScreen<T> screen, int slotId)
    {
        clickSlot(client, screen, slotId, SlotActionType.PICKUP);
    }

    public static <T extends ScreenHandler> void clickSlot(MinecraftClient client, HandledScreen<T> screen, int slotId, SlotActionType slotActionType)
    {
        assert client.interactionManager != null;
        client.interactionManager.clickSlot(screen.getScreenHandler().syncId, slotId, 0, slotActionType, client.player);
    }
}

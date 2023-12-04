package grill24.currinv.sorting;

import grill24.currinv.CurrInvClient;
import grill24.currinv.ScreenWithInventory;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoubleBlockProperties;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

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

    public static <T extends ScreenHandler> void depositItem(MinecraftClient client, HandledScreen<T> screen, Item item) {
        assert client.player != null;
        Inventory playerInventory = client.player.getInventory();

        for (int i = PLAYER_HOTBAR_SLOTS_END_INDEX; i < playerInventory.size(); i++) {
            ItemStack itemStack = playerInventory.getStack(i);

            if (itemStack.getItem().equals(item)) {
                OptionalInt slotId = screen.getScreenHandler().getSlotIndex(playerInventory, i);
                if (slotId.isPresent()) {
                    clickSlot(client, screen, slotId.getAsInt(), SlotActionType.QUICK_MOVE);
                }
            }
        }
    }

    public static <T extends ScreenHandler> void collectItems(MinecraftClient client, HandledScreen<T> screen, List<Item> itemsToCollect, ContainerStockData stockData, boolean isAllowedToInsertIntoHotbar)
    {
        assert client.player != null;
        assert client.interactionManager != null;

        Optional<Inventory> inventory = tryGetInventoryFromScreen(screen);
        if (inventory.isPresent()) {

            for (Item item : itemsToCollect) {
                Optional<ItemQuantityAndSlots> stock = stockData.getItemStock(item);
                if (stock.isPresent()) {
                    // TODO: Not use this field from another class.
                    if (stock.get().slotIds.containsKey(CurrInvClient.sorter.lastUsedContainerBlockPos)) {
                        for (Integer slotId : stock.get().slotIds.get(CurrInvClient.sorter.lastUsedContainerBlockPos)) {
                            int pickupSlotIndex = screen.getScreenHandler().getSlotIndex(inventory.get(), slotId).orElse(slotId);
                            if (inventory.get().getStack(slotId).getItem().equals(item)) {
                                if(isAllowedToInsertIntoHotbar) {
                                    clickSlot(client, screen, pickupSlotIndex, SlotActionType.QUICK_MOVE);
                                } else {
                                    clickSlot(client, screen, pickupSlotIndex);

                                    Inventory playerInventory = client.player.getInventory();
                                    for (int i = PLAYER_HOTBAR_SLOTS_END_INDEX; i < playerInventory.size(); i++) {
                                        if(playerInventory.getStack(i).isEmpty() || (playerInventory.getStack(i).getItem().equals(item) && playerInventory.getStack(i).getCount() < playerInventory.getStack(i).getMaxCount())) {
                                            int slotIndex = screen.getScreenHandler().getSlotIndex(playerInventory, i).getAsInt();
                                            System.out.println(slotIndex);
                                            System.out.println(screen.getScreenHandler().getCursorStack());
                                            clickSlot(client, screen, slotIndex);
                                            if(screen.getScreenHandler().getCursorStack().isEmpty())
                                                break;
                                        }
                                    }

                                    if(!screen.getScreenHandler().getCursorStack().isEmpty()) {
                                        clickSlot(client, screen, slotId);
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
        CurrInvClient.sorter.tryInventoryScreen(screen);
    }

    public static BlockPos getOneBlockPosFromDoubleChests(MinecraftClient client, BlockPos pos) {
        return getOneBlockPosFromDoubleChests(client, pos, DoubleBlockProperties.Type.FIRST);
    }

    public static BlockPos getOneBlockPosFromDoubleChests(MinecraftClient client, BlockPos pos, DoubleBlockProperties.Type chestHalfTypeToReturn) {
        if(client.world != null && client.world.getBlockState(pos).getBlock() instanceof ChestBlock)
        {
            DoubleBlockProperties.Type type = ChestBlock.getDoubleBlockType(client.world.getBlockState(pos));
            if(type == DoubleBlockProperties.Type.SINGLE)
                return pos;
            else {
                for (BlockPos cardinal : getCardinals(pos))
                {
                    if(client.world.getBlockState(cardinal).getBlock() instanceof ChestBlock)
                    {
                        DoubleBlockProperties.Type otherChestType = ChestBlock.getDoubleBlockType(client.world.getBlockState(cardinal));
                        if(otherChestType != type && otherChestType != DoubleBlockProperties.Type.SINGLE && client.world.getBlockState(pos).get(ChestBlock.FACING) == client.world.getBlockState(cardinal).get(ChestBlock.FACING))
                        {
                            if(type == chestHalfTypeToReturn)
                                return pos;
                            else
                                return cardinal;
                        }
                    }
                }
            }
        }
        return pos;
    }

    public static LootableContainerBlockEntity getOneBlockEntityFromDoubleChests(MinecraftClient client, LootableContainerBlockEntity blockEntity)
    {
        BlockPos blockPos = getOneBlockPosFromDoubleChests(client, blockEntity.getPos());
        if(client.world != null && client.world.getBlockEntity(blockPos) instanceof LootableContainerBlockEntity lootableContainerBlockEntity)
            return lootableContainerBlockEntity;
        return blockEntity;
    }

    private static List<BlockPos> getCardinals(BlockPos pos) {
        List<BlockPos> adjacent = new ArrayList<>();
        adjacent.add(pos.north());
        adjacent.add(pos.east());
        adjacent.add(pos.south());
        adjacent.add(pos.west());
        return adjacent;
    }
}

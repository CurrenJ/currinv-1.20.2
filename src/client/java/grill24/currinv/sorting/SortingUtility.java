package grill24.currinv.sorting;

import grill24.currinv.CurrInvClient;
import grill24.currinv.debug.DebugUtility;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoubleBlockProperties;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class SortingUtility {
    public static final int PLAYER_HOTBAR_SLOTS_END_INDEX = 9;

    public static Optional<Inventory> tryGetInventoryFromScreen(HandledScreen<?> screen) {
        if (screen instanceof GenericContainerScreen) {
            return Optional.of(((GenericContainerScreen) screen).getScreenHandler().getInventory());
        }
        if (screen instanceof ShulkerBoxScreen) {
            ShulkerBoxScreenHandler shulkerBoxScreenHandler = ((ShulkerBoxScreen) screen).getScreenHandler();
            ScreenWithInventory screenWithInventory = ((ScreenWithInventory) shulkerBoxScreenHandler);
            return Optional.of(screenWithInventory.currinv_1_20_2$getInventory());
        }
        return Optional.empty();
    }

    public static void clickSlot(MinecraftClient client, HandledScreen<?> screen, int slotId) {
        clickSlot(client, screen, slotId, SlotActionType.PICKUP);
    }

    public static void clickSlot(MinecraftClient client, HandledScreen<?> screen, int slotId, SlotActionType slotActionType) {
        assert client.interactionManager != null;
        client.interactionManager.clickSlot(screen.getScreenHandler().syncId, slotId, 0, slotActionType, client.player);
    }

    public static boolean depositItem(MinecraftClient client, Screen screen, Item item) {
        assert client.player != null;
        PlayerInventory playerInventory = client.player.getInventory();

        if (screen instanceof HandledScreen<?> handledScreen) {
            for (int i = PLAYER_HOTBAR_SLOTS_END_INDEX; i < playerInventory.main.size(); i++) {
                OptionalInt slotIdOptional = handledScreen.getScreenHandler().getSlotIndex(playerInventory, i);
                if (slotIdOptional.isPresent()) {
                    int slotId = slotIdOptional.getAsInt();
                    ItemStack itemStack = handledScreen.getScreenHandler().getSlot(slotId).getStack();

                    if (itemStack.getItem().equals(item)) {
                        clickSlot(client, handledScreen, slotId, SlotActionType.QUICK_MOVE);

                        // Failed to deposit items.
                        if (!handledScreen.getScreenHandler().getSlot(slotId).getStack().isEmpty())
                            return false;
                    }
                }
            }

            // Success.
            return true;
        }
        // Failure.
        return false;
    }

    public static boolean collectItems(MinecraftClient client, Screen screen, List<Item> itemsToCollect, ContainerStockData stockData, boolean isAllowedToInsertIntoHotbar) {
        assert client.player != null;
        assert client.interactionManager != null;

        if (!(screen instanceof HandledScreen<?> handledScreen))
            return false;

        Optional<Inventory> inventory = tryGetInventoryFromScreen(handledScreen);
        if (inventory.isPresent()) {

            for (Item item : itemsToCollect) {
                Optional<ItemQuantityAndSlots> stock = stockData.getItemStock(item);
                if (stock.isPresent()) {
                    // TODO: Not use this field from another class.
                    if (stock.get().slotIds.containsKey(CurrInvClient.sorter.lastUsedContainerBlockPos)) {
                        for (Integer slotId : stock.get().slotIds.get(CurrInvClient.sorter.lastUsedContainerBlockPos)) {
                            int pickupSlotIndex = handledScreen.getScreenHandler().getSlotIndex(inventory.get(), slotId).orElse(slotId);
                            if (inventory.get().getStack(slotId).getItem().equals(item)) {
                                if (isAllowedToInsertIntoHotbar) {
                                    clickSlot(client, handledScreen, pickupSlotIndex, SlotActionType.QUICK_MOVE);
                                } else {
                                    clickSlot(client, handledScreen, pickupSlotIndex);

                                    Inventory playerInventory = client.player.getInventory();
                                    for (int i = PLAYER_HOTBAR_SLOTS_END_INDEX; i < playerInventory.size(); i++) {
                                        if (playerInventory.getStack(i).isEmpty() || (playerInventory.getStack(i).getItem().equals(item) && playerInventory.getStack(i).getCount() < playerInventory.getStack(i).getMaxCount())) {
                                            OptionalInt slotIndex = handledScreen.getScreenHandler().getSlotIndex(playerInventory, i);
                                            if (slotIndex.isPresent()) {
                                                System.out.println(slotIndex);
                                                System.out.println(handledScreen.getScreenHandler().getCursorStack());
                                                clickSlot(client, handledScreen, slotIndex.getAsInt());
                                                if (handledScreen.getScreenHandler().getCursorStack().isEmpty())
                                                    break;
                                            } else {
                                                DebugUtility.print(client, "Could not find slot in inventory for index " + i);
                                            }
                                        }
                                    }

                                    if (!handledScreen.getScreenHandler().getCursorStack().isEmpty()) {
                                        clickSlot(client, handledScreen, slotId);
                                        CurrInvClient.sorter.tryInventoryScreen(handledScreen);
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }

        CurrInvClient.sorter.tryInventoryScreen(handledScreen);
        return true;
    }

    public static BlockPos getOneBlockPosFromDoubleChests(MinecraftClient client, BlockPos pos) {
        return getOneBlockPosFromDoubleChests(client, pos, DoubleBlockProperties.Type.FIRST);
    }

    public static BlockPos getOneBlockPosFromDoubleChests(MinecraftClient client, BlockPos pos, DoubleBlockProperties.Type chestHalfTypeToReturn) {
        if (client.world != null && client.world.getBlockState(pos).getBlock() instanceof ChestBlock) {
            DoubleBlockProperties.Type type = ChestBlock.getDoubleBlockType(client.world.getBlockState(pos));
            if (type == DoubleBlockProperties.Type.SINGLE)
                return pos;
            else {
                for (BlockPos cardinal : getCardinals(pos)) {
                    if (client.world.getBlockState(cardinal).getBlock() instanceof ChestBlock) {
                        DoubleBlockProperties.Type otherChestType = ChestBlock.getDoubleBlockType(client.world.getBlockState(cardinal));
                        if (otherChestType != type && otherChestType != DoubleBlockProperties.Type.SINGLE && client.world.getBlockState(pos).get(ChestBlock.FACING) == client.world.getBlockState(cardinal).get(ChestBlock.FACING)) {
                            if (type == chestHalfTypeToReturn)
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

    public static LootableContainerBlockEntity getOneBlockEntityFromDoubleChests(MinecraftClient client, LootableContainerBlockEntity blockEntity) {
        BlockPos blockPos = getOneBlockPosFromDoubleChests(client, blockEntity.getPos());
        if (client.world != null && client.world.getBlockEntity(blockPos) instanceof LootableContainerBlockEntity lootableContainerBlockEntity)
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

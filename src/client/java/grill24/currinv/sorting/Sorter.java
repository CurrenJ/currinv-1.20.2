package grill24.currinv.sorting;

import grill24.currinv.CurrInvClient;
import grill24.currinv.IDirtyFlag;
import grill24.sizzlib.component.*;
import grill24.sizzlib.persistence.IFileProvider;
import grill24.sizzlib.persistence.PersistenceManager;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerListener;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.OptionalInt;

@Command
public class Sorter implements IFileProvider {
    public transient boolean isEnabled, isSorting;

    @CommandOption
    public boolean isSortingEnabled;

    @CommandOption
    public boolean isQuickStackEnabled;
    private transient int currentSortingSlotId, currentStockIndex, currentStockSlotIdsIndex;

    public transient BlockPos lastUsedContainerBlockPos;

    @CommandOption(readOnly = true)
    public HashMap<Identifier, HashMap<BlockPos, ContainerStockData>> stockDataByDimension;


    public enum SortingStyle {QUANTITY, LEXICOGRAPHICAL, CREATIVE_MENU}

    public SortingStyle currentSortingStyle = SortingStyle.CREATIVE_MENU;

    public HashMap<Item, Integer> creativeMenuOrder;

    public Sorter() {
        isEnabled = true;
        isSortingEnabled = false;
        isQuickStackEnabled = false;
        isSorting = false;

        currentSortingSlotId = 0;
        currentStockIndex = 0;
        currentStockSlotIdsIndex = 0;

        stockDataByDimension = new HashMap<>();

        PersistenceManager.load(this);
    }

    @ScreenTick
    public void onUpdate(MinecraftClient client, Screen screen) {
        if (isEnabled && screen instanceof HandledScreen<?> handledScreen && !CurrInvClient.config.isContainerExemptFromSorting(lastUsedContainerBlockPos)) {
            Optional<Inventory> screenInventoryOptional = SortingUtility.tryGetInventoryFromScreen(handledScreen);
            if (screenInventoryOptional.isPresent()) {
                tryInventoryScreen(handledScreen);
                Optional<ContainerStockData> containerStockData = tryGetStockData(lastUsedContainerBlockPos);
                if (containerStockData.isPresent()) {
                    if (!isSorting && containerStockData.get().isDirty()) {
                        stopSorting();
                        tryQuickStack(client, handledScreen);
                        tryStartSortContainer(handledScreen);
                        containerStockData.get().markClean();
                    }

                    tryDoSortingTick(client, handledScreen);
                } else {
                    stopSorting();
                }
            }
        }
    }

    @ScreenInit
    public void onScreenInit(MinecraftClient client, Screen screen) {
        HandledScreen<?> handledScreen = ((HandledScreen<?>) screen);
        handledScreen.getScreenHandler().addListener(new ScreenHandlerListener() {
            @Override
            public void onSlotUpdate(ScreenHandler handler, int slotId, ItemStack stack) {
                CurrInvClient.sorter.markDirty();
            }

            @Override
            public void onPropertyUpdate(ScreenHandler handler, int property, int value) {
            }
        });
    }

    public <T extends ScreenHandler> boolean tryInventoryScreen(HandledScreen<T> screen) {
        Optional<Inventory> screenInventoryOptional = SortingUtility.tryGetInventoryFromScreen(screen);
        return screenInventoryOptional.map(this::tryInventoryInventory).orElse(false);
    }

    private boolean tryInventoryInventory(Inventory inventory) {
        if (isEnabled && !isSorting && !CurrInvClient.config.isContainerExemptFromSorting(lastUsedContainerBlockPos)) {
            Optional<ContainerStockData> containerStockDataOptional = tryGetStockData(lastUsedContainerBlockPos);
            if (containerStockDataOptional.isPresent() && containerStockDataOptional.get().isDirty()) {
                ContainerStockData containerStockData = containerStockDataOptional.get();
                containerStockData.inventoryInventory(lastUsedContainerBlockPos, inventory);
            } else {
                ContainerStockData containerStockData = new ContainerStockData(lastUsedContainerBlockPos, inventory);

                Identifier dimension = MinecraftClient.getInstance().world.getDimensionKey().getValue();
                HashMap<BlockPos, ContainerStockData> stockData = stockDataByDimension.getOrDefault(dimension, new HashMap<>());
                stockData.put(lastUsedContainerBlockPos, containerStockData);
                stockDataByDimension.put(dimension, stockData);
            }
            PersistenceManager.save(this);
            return true;
        }
        return false;
    }

    private <T extends ScreenHandler> boolean tryStartSortContainer(HandledScreen<T> screen) {
        if (!isSorting) {
            if (isValidSortingConditions(screen)) {
                tryGenerateCreativeMenuOrderLookup();
                isSorting = true;
                return true;
            }
        }
        return false;
    }

    private <T extends ScreenHandler> boolean tryDoSortingTick(MinecraftClient client, HandledScreen<T> screen) {
        assert client.player != null;

        if (isSorting && isValidSortingConditions(screen)) {
            Optional<ContainerStockData> containerStockDataOptional = tryGetStockData(lastUsedContainerBlockPos);
            if (containerStockDataOptional.isPresent()) {
                ContainerStockData containerStockData = containerStockDataOptional.get();
                return tryDoSortingTick(client, screen, containerStockData);
            }
        }
        stopSorting();
        return false;
    }

    private <T extends ScreenHandler> boolean tryDoSortingTick(MinecraftClient client, HandledScreen<T> screen, ContainerStockData containerStockData) {
        if (currentStockIndex >= containerStockData.getOrderedStock().size()) {
            stopSorting();
            return false;
        }

        ArrayList<ItemQuantityAndSlots> orderedStock = containerStockData.getOrderedStock();

        // Get the item stack that we are currently sorting.
        ItemQuantityAndSlots itemQuantityAndSlots = orderedStock.get(currentStockIndex);

        // Pick up item stack to be sorted.
        int slotId = itemQuantityAndSlots.slotIds.get(lastUsedContainerBlockPos).get(currentStockSlotIdsIndex);
        SortingUtility.clickSlot(client, screen, slotId);

        // Safety check
        if (!screen.getScreenHandler().getCursorStack().getItem().equals(itemQuantityAndSlots.item)) {
            // Something went wrong. The contents of this screenInventory are not what we expect.
            stopSorting();
            // Hey! Drop it!
            if (!screen.getScreenHandler().getCursorStack().isEmpty()) {
                SortingUtility.clickSlot(client, screen, slotId);
            }

            markDirty();
            return false;
        }

        // Check if we can merge it with the last slot we sorted into.
        // If so, do it.
        boolean canStackWithLastSlot = ScreenHandler.canInsertItemIntoSlot(screen.getScreenHandler().slots.get(
                        Math.max(currentSortingSlotId - 1, 0)),
                screen.getScreenHandler().getCursorStack(),
                true);
        if (canStackWithLastSlot) {
            SortingUtility.clickSlot(client, screen, currentSortingSlotId - 1);
        }

        // Place down any remaining item stack in the cursor into our current sorting slot.
        if (!screen.getScreenHandler().getCursorStack().isEmpty()) {
            SortingUtility.clickSlot(client, screen, currentSortingSlotId);
            currentSortingSlotId++;
        }

        // What's left in the cursor is what was previously in the slot that we sorted into.
        // So, adjust our stock analysis according to the new slot of this item stack.
        ItemStack cursorItemStack = screen.getScreenHandler().getCursorStack();
        Item cursorItem = cursorItemStack.getItem();
        if (!cursorItemStack.isEmpty()) {
            SortingUtility.clickSlot(client, screen, slotId);

            Optional<ItemQuantityAndSlots> displacedStock = containerStockData.getItemStock(cursorItem);
            if (displacedStock.isPresent()) {
                for (int i = 0; i < displacedStock.get().slotIds.get(lastUsedContainerBlockPos).size(); i++) {
                    int stockSlotId = displacedStock.get().slotIds.get(lastUsedContainerBlockPos).get(i);

                    // If our stored stock slot is the slot that we just swapped an item stack into, then update accordingly.
                    if (stockSlotId == currentSortingSlotId - 1) {
                        displacedStock.get().slotIds.get(lastUsedContainerBlockPos).set(i, slotId);
                    }
                }
            }
        }

        if (currentStockSlotIdsIndex < itemQuantityAndSlots.slotIds.get(lastUsedContainerBlockPos).size() - 1) {
            currentStockSlotIdsIndex++;
        } else {
            currentStockSlotIdsIndex = 0;
            currentStockIndex++;

            if (currentStockIndex == orderedStock.size()) {
                stopSorting();

                tryInventoryScreen(screen);
                CurrInvClient.fullSuiteSorter.setAllContainersStockData(this);
            }
        }

        return true;
    }

    public void stopSorting() {
        currentSortingSlotId = 0;
        currentStockIndex = 0;
        currentStockSlotIdsIndex = 0;
        if (isSorting)
            isSorting = false;
    }

    private <T extends ScreenHandler> boolean tryQuickStack(MinecraftClient client, HandledScreen<T> screen) {
        if (isValidQuickStackConditions(screen)) {
            assert client.player != null;
            Inventory playerInventory = client.player.getInventory();

            for (int i = SortingUtility.PLAYER_HOTBAR_SLOTS_END_INDEX; i < playerInventory.size(); i++) {
                ItemStack itemStack = playerInventory.getStack(i);
                Item item = itemStack.getItem();

                if (item.getMaxCount() > 1) {
                    Optional<ContainerStockData> stockData = tryGetStockData(lastUsedContainerBlockPos);
                    if (stockData.isPresent()) {
                        Optional<ItemQuantityAndSlots> itemStock = stockData.get().getItemStock(item);
                        if (itemStock.isPresent()) {
                            OptionalInt slotId = screen.getScreenHandler().getSlotIndex(playerInventory, i);
                            if (slotId.isPresent()) {
                                SortingUtility.clickSlot(client, screen, slotId.getAsInt(), SlotActionType.QUICK_MOVE);
                                markDirty();
                            }
                        }
                    }
                }
            }

            return true;
        }
        return false;
    }

    public Optional<ContainerStockData> tryGetStockData(BlockPos blockPos) {
        Identifier dimension = MinecraftClient.getInstance().world.getDimensionKey().getValue();
        if (stockDataByDimension.containsKey(dimension)) {
            HashMap<BlockPos, ContainerStockData> stockData = stockDataByDimension.get(dimension);
            if (stockData.containsKey(blockPos))
                return Optional.of(stockData.get(blockPos));
        }

        return Optional.empty();
    }

    public void removeData(BlockPos blockPos) {
        Identifier dimension = MinecraftClient.getInstance().world.getDimensionKey().getValue();
        if (stockDataByDimension.containsKey(dimension)) {
            HashMap<BlockPos, ContainerStockData> stockData = stockDataByDimension.get(dimension);
            if (stockData.containsKey(blockPos))
                stockData.remove(blockPos);
        }
    }

    public void onUseContainer(MinecraftClient client, LootableContainerBlockEntity containerBlockEntity) {
        lastUsedContainerBlockPos = SortingUtility.getOneBlockPosFromDoubleChests(client, containerBlockEntity.getPos());
        markDirty();
    }

    private <T extends ScreenHandler> boolean isValidSortingConditions(HandledScreen<T> screen) {
        return isEnabled && isSortingEnabled && screen.getScreenHandler().getCursorStack().isEmpty();
    }

    private <T extends ScreenHandler> boolean isValidQuickStackConditions(HandledScreen<T> screen) {
        return isEnabled && isQuickStackEnabled && screen.getScreenHandler().getCursorStack().isEmpty();
    }

    public void tryGenerateCreativeMenuOrderLookup() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (creativeMenuOrder == null && client != null && client.player != null) {
            boolean shouldShowOperatorTab = client.options.getOperatorItemsTab().getValue() && client.player.isCreativeLevelTwoOp();
            ItemGroups.updateDisplayContext(client.player.networkHandler.getEnabledFeatures(), shouldShowOperatorTab, client.player.getWorld().getRegistryManager());

            creativeMenuOrder = new HashMap<>();
            ItemGroup searchGroup = Registries.ITEM_GROUP.getOrThrow(ItemGroups.SEARCH);
            ItemStack[] itemStacks = searchGroup.getDisplayStacks().toArray(new ItemStack[0]);
            for (int i = 0; i < itemStacks.length; i++) {
                Item item = itemStacks[i].getItem();
                creativeMenuOrder.put(item, i);
            }
        }
    }

    @Override
    public File getFile() {
        String name = ComponentUtility.convertDeclarationToCamel(getClass().getSimpleName()) + ".json";
        return CurrInvClient.config.getWorldAssociatedFile(name);
    }

    public void markDirty() {
        tryGetStockData(lastUsedContainerBlockPos).ifPresent(IDirtyFlag::markDirty);
    }
}

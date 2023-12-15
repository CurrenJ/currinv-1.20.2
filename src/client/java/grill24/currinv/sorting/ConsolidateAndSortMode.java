package grill24.currinv.sorting;

import grill24.currinv.CurrInvClient;
import grill24.currinv.debug.DebugUtility;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class ConsolidateAndSortMode extends FullSuiteSorterMode {

    private static final int RANGE = 16;
    private List<Instruction> instructions;

    private record Instruction(BlockPos pos, State state, Item item) {
    }

    private enum State {
        Gather,
        Consolidate,
        Sort,
        Return
    }

    public ConsolidateAndSortMode(MinecraftClient client) {
        super(client);

        generateInstructions(client, CurrInvClient.fullSuiteSorter.allContainersStockData);

        if (client.world != null) {

            for (Instruction instruction : instructions) {
                if (client.world.getBlockEntity(instruction.pos) instanceof LootableContainerBlockEntity blockEntity)
                    containersToVisit.add(blockEntity);
            }

        }
    }

    @Override
    public boolean doContainerScreenInteractionTick(MinecraftClient client, Screen screen) {
        if (instructions.size() <= currentContainerIndex)
            return true;


        Instruction instruction = instructions.get(currentContainerIndex);
        switch (instruction.state) {
            case Gather:
                DebugUtility.print(client, "Gathering " + instruction.item.getName().getString() + " from " + instruction.pos.toString());
                SortingUtility.collectItems(client, screen, Collections.singletonList(instruction.item), CurrInvClient.fullSuiteSorter.allContainersStockData, false);
                CurrInvClient.sorter.isSortingEnabled = true;
                instructions.set(currentContainerIndex, new Instruction(instruction.pos, State.Sort, instruction.item));
                break;
            case Consolidate:
                DebugUtility.print(client, "Consolidating " + instruction.item.getName().getString() + " at " + instruction.pos.toString());
                SortingUtility.depositItem(client, screen, instruction.item);
                CurrInvClient.sorter.isSortingEnabled = true;
                instructions.set(currentContainerIndex, new Instruction(instruction.pos, State.Sort, instruction.item));
                break;
            case Sort:
                if (!CurrInvClient.sorter.isSorting) {
                    CurrInvClient.sorter.isSortingEnabled = false;
                    return true;
                }
                break;
            case Return:
                SortingUtility.depositItem(client, screen, instruction.item);
                CurrInvClient.sorter.isSortingEnabled = true;
                instructions.set(currentContainerIndex, new Instruction(instruction.pos, State.Sort, instruction.item));
                break;
        }

        return false;
    }

    @Override
    public boolean doContainerInteractionTick(MinecraftClient client) {
        return false;
    }

    @Override
    public void onContainerAccessFail(MinecraftClient client) {
        // If we can't access a container, remove it's consolidation step from the instructions list
        // and deposit the items back to where they came from
        int nextConsolidationInstructionIndex = -1;
        FullSuiteSorter fullSuiteSorter = CurrInvClient.fullSuiteSorter;
        Item currentItem = instructions.get(currentContainerIndex).item;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if (instruction.item.equals(currentItem)) {
                if (instruction.state == State.Consolidate && i >= currentContainerIndex) {
                    nextConsolidationInstructionIndex = i;
                    break;
                } else {
                    instructions.set(i, new Instruction(instruction.pos, State.Return, instruction.item));

                    // If we're depositing items from a container that we've already gathered from, we need to update the container index
                    currentContainerIndex--;
                }
            }
        }

        instructions.remove(nextConsolidationInstructionIndex);
        currentContainerIndex--;
    }

    public void generateInstructions(MinecraftClient client, ContainerStockData allContainersStockData) {
        instructions = new ArrayList<>();

        allContainersStockData.getStock().forEach((item, itemQuantityAndSlots) -> {
            // Don't consolidate shulker boxes
            if (!item.equals(Items.SHULKER_BOX)) {

                LinkedHashSet<ItemQuantityAndSlots> containers = new LinkedHashSet<>();
                LinkedHashSet<BlockPos> containerPositions = new LinkedHashSet<>();

                if (itemQuantityAndSlots.slotIds.size() >= 2) {
                    itemQuantityAndSlots.slotIds.forEach((blockPos, slotIds) -> {
                        Optional<ContainerStockData> containerStockData = CurrInvClient.sorter.tryGetStockData(blockPos);
                        if (containerStockData.isPresent()) {
                            Optional<ItemQuantityAndSlots> containerItemQuantityAndSlots = containerStockData.get().getItemStock(item);
                            if (containerItemQuantityAndSlots.isPresent() && !containerItemQuantityAndSlots.get().slotIds.isEmpty()) {
                                if (!containerPositions.contains(SortingUtility.getOneBlockPosFromDoubleChests(client, blockPos))
                                        && blockPos.getManhattanDistance(client.player.getBlockPos()) < RANGE
                                        && client.world.getBlockEntity(blockPos) instanceof LootableContainerBlockEntity) {
                                    containerPositions.add(blockPos);
                                    containers.add(containerItemQuantityAndSlots.get());
                                }
                            }
                        }
                    });
                }

                List<ItemQuantityAndSlots> containersByQuantity = new ArrayList<>(containers);
                containersByQuantity.sort(Comparator.naturalOrder());

                if (containers.size() >= 2) {
                    for (int i = 0; i < containers.size(); i++) {
                        BlockPos pos = containersByQuantity.get(i).slotIds.keySet().iterator().next();
                        if (i < containers.size() - 1) {
                            instructions.add(new Instruction(pos, State.Gather, item));
                        } else {
                            instructions.add(new Instruction(pos, State.Consolidate, item));
                        }
                    }
                }

            }
        });
    }
}

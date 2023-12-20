package grill24.currinv.sorting;

import com.google.common.collect.Lists;
import grill24.currinv.CurrInvClient;
import grill24.currinv.debug.DebugUtility;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.*;
import java.util.function.Supplier;

public class ConsolidateAndSortMode extends FullSuiteSorterMode {

    private static final int RANGE = 16;
    private List<Instruction> instructions;

    private int currentInstructionIndex;

    private boolean isSorting;

    private record Instruction(Queue<BlockPos> blockPos, State state, Item item) {
    }

    private enum State {
        Gather,
        Consolidate,
        Return
    }

    public ConsolidateAndSortMode(MinecraftClient client) {
        super(client);
    }

    @Override
    public Supplier<LootableContainerBlockEntity> getContainersToVisitSupplier(MinecraftClient client) {
        generateInstructions(client, CurrInvClient.fullSuiteSorter.allContainersStockData);

        return () -> {
            while (currentInstructionIndex < instructions.size()) {
                if (currentInstructionIndex < 0 || instructions.get(currentInstructionIndex).blockPos.isEmpty()) {
                    currentInstructionIndex++;
                    if (currentInstructionIndex >= instructions.size())
                        return null;
                } else {
                    instructions.get(currentInstructionIndex).blockPos.remove();
                }
                Instruction instruction = instructions.get(currentInstructionIndex);

                // Don't gather from chests we are going to immediately consolidate into.
                if (instruction.state == State.Gather && currentInstructionIndex < instructions.size() - 1) {
                    Instruction nextConsolidateInstruction = instructions.get(currentInstructionIndex + 1);

                    if (instruction.item.equals(nextConsolidateInstruction.item))
                        instruction.blockPos.remove(nextConsolidateInstruction.blockPos.peek());
                    else
                        DebugUtility.print(client, "SUSPICIOUS BEHAVIOUR");
                }

                if (client.world.getBlockEntity(instructions.get(currentInstructionIndex).blockPos.peek()) instanceof LootableContainerBlockEntity lootableContainerBlockEntity) {
                    return lootableContainerBlockEntity;
                }
            }
            return null;
        };
    }

    @Override
    public boolean doContainerScreenInteractionTick(MinecraftClient client, Screen screen) {
        if (instructions.size() <= currentInstructionIndex)
            return true;


        if (isSorting) {
            if (!CurrInvClient.sorter.isSorting) {
                CurrInvClient.sorter.isSortingEnabled = false;
                isSorting = false;
                return true;
            }
        } else {
            Instruction instruction = instructions.get(currentInstructionIndex);
            switch (instruction.state) {
                case Gather:
                    DebugUtility.print(client, "Gathering " + instruction.item.getName().getString() + " from " + instruction.blockPos.peek().toString());

                    // There should always be a consolidate instruction after every valid gather instruction.
                    if (currentInstructionIndex < instructions.size() - 1) {
                        Instruction nextConsolidateInstruction = instructions.get(currentInstructionIndex + 1);


                        // Try collect items from container.
                        boolean hasCollectedAllItems = SortingUtility.collectItems(client, screen, Collections.singletonList(instruction.item), CurrInvClient.fullSuiteSorter.allContainersStockData, false);
                        if (!hasCollectedAllItems) {
                            if (currentInstructionIndex < instructions.size() - 1) {
                                // blockPos field of this instruction is a ref to the blockPos field in the next consolidation
                                Instruction dropOff = new Instruction(nextConsolidateInstruction.blockPos, State.Consolidate, instruction.item);
                                // blockPos field of this instruction is a copy
                                Instruction remainingCollection = new Instruction(instruction.blockPos, instruction.state, instruction.item);
                                instructions.add(currentInstructionIndex + 1, dropOff);
                                instructions.add(currentInstructionIndex + 2, remainingCollection);

                                // Force us to advance to the next instruction
                                skipCurrentInstruction();
                            }
                        } // If everything is well, continue down gather queue.

                        // Switch over to sorting
                        CurrInvClient.sorter.isSortingEnabled = true;
                        isSorting = true;
                    } else {
                        DebugUtility.print(client, "Suspicious consolidation behaviour...");
                    }
                    break;
                case Consolidate:
                    DebugUtility.print(client, "Consolidating " + instruction.item.getName().getString() + " at " + instruction.blockPos.peek().toString());

                    boolean hasDepositedAllItems = SortingUtility.depositItem(client, screen, instruction.item);

                    if (hasDepositedAllItems) {
                        // We're done here. Force advance to the next instruction (by clearing the blockPos queues; in this case we avoid resetting the linked queues as well)
                        instructions.set(currentInstructionIndex, new Instruction(new LinkedList<>(), instruction.state, instruction.item));
                    } // If failed to deposit all items, we will continue to attempt to deposit down the blockPos queue.

                    // Switch over to sorting
                    CurrInvClient.sorter.isSortingEnabled = true;
                    isSorting = true;
                    break;
                case Return:
                    // Uh so the new instruction queuing rework mgiht have inadvertantly solved this returning on failure issue?
                    SortingUtility.depositItem(client, screen, instruction.item);

                    // Switch over to sorting
                    CurrInvClient.sorter.isSortingEnabled = true;
                    isSorting = true;
                    break;
            }
        }
        return false;
    }

    private void skipCurrentInstruction() {
        Instruction instruction = instructions.get(currentInstructionIndex);
        instructions.set(currentInstructionIndex, new Instruction(new LinkedList<>(), instruction.state, instruction.item));
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
        Item currentItem = instructions.get(currentInstructionIndex).item;
        for (int i = 0; i < instructions.size(); i++) {
            Instruction instruction = instructions.get(i);
            if (instruction.item.equals(currentItem)) {
                if (instruction.state == State.Consolidate && i >= currentInstructionIndex) {
                    nextConsolidationInstructionIndex = i;
                    break;
                } else {
                    instructions.set(i, new Instruction(instruction.blockPos, State.Return, instruction.item));

                    // If we're depositing items from a container that we've already gathered from, we need to update the container index
                    currentInstructionIndex--;
                }
            }
        }

        instructions.remove(nextConsolidationInstructionIndex);
        currentInstructionIndex--;
    }

    public void generateInstructions(MinecraftClient client, ContainerStockData allContainersStockData) {
        instructions = new ArrayList<>();
        currentInstructionIndex = -1;

        // For each Item we have data on...
        allContainersStockData.getStock().forEach((item, itemQuantityAndSlots) -> {
            // Don't consolidate shulker box items
            if (!item.equals(Items.SHULKER_BOX)) {

                // Stage 1: Identify list of eligible containers.
                LinkedHashSet<ItemQuantityAndSlots> containersStockData = new LinkedHashSet<>();
                LinkedHashSet<BlockPos> containerPositions = new LinkedHashSet<>();

                // Can't consolidate items that aren't distributed across > 1 container.
                if (itemQuantityAndSlots.slotIds.size() >= 2) {
                    // For each container that has this Item...
                    itemQuantityAndSlots.slotIds.forEach((blockPos, slotIds) -> {
                        // Get the stock data we have for just this container...
                        Optional<ContainerStockData> containerStockData = CurrInvClient.sorter.tryGetStockData(blockPos);
                        if (containerStockData.isPresent()) {
                            Optional<ItemQuantityAndSlots> containerItemQuantityAndSlots = containerStockData.get().getItemStock(item);
                            // Get the stock data we have for just this item in just this container...
                            if (containerItemQuantityAndSlots.isPresent() && !containerItemQuantityAndSlots.get().slotIds.isEmpty()) {

                                // If this container is eligible (isn't already in list, is within range, and block entity exists), then add to list
                                if (!containerPositions.contains(SortingUtility.getOneBlockPosFromDoubleChests(client, blockPos))
                                        && blockPos.getManhattanDistance(client.player.getBlockPos()) < RANGE
                                        && client.world.getBlockEntity(blockPos) instanceof LootableContainerBlockEntity) {
                                    containerPositions.add(blockPos);
                                    containersStockData.add(containerItemQuantityAndSlots.get());
                                }

                            }
                        }
                    });
                }

                // Stage 2: Generate collect and consolidate instructions from the list of eligible containers.
                if (containersStockData.size() >= 2) {
                    List<ItemQuantityAndSlots> containerStockDataByQuantity = new ArrayList<>(containersStockData);
                    containerStockDataByQuantity.sort(new StockComparator(Sorter.SortingStyle.QUANTITY));

                    List<BlockPos> containersByQuantity = containerStockDataByQuantity.stream().map((data) -> data.slotIds.keySet().iterator().next()).toList();

                    // TODO: Update operation logic to execute these instructions using the queues!
                    instructions.add(new Instruction(new LinkedList<>(containersByQuantity), State.Gather, item));
                    instructions.add(new Instruction(new LinkedList<>(Lists.reverse(containersByQuantity)), State.Consolidate, item));
                }

            }
        });
    }
}

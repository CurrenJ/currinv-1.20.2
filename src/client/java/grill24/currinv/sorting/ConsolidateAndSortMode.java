package grill24.currinv.sorting;

import grill24.currinv.CurrInvClient;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class ConsolidateAndSortMode implements IFullSuiteSorterMode {

    private List<Instruction> instructions;

    private record Instruction(BlockPos pos, State state, Item item) {
    }

    private enum State {
        Gather,
        Consolidate,
        Sort,
    }

    @Override
    public List<LootableContainerBlockEntity> getContainersToVisit(MinecraftClient client) {
        generateInstructions(client, CurrInvClient.fullSuiteSorter.allContainersStockData);

        ArrayList<LootableContainerBlockEntity> containersToVisit = new ArrayList<>();

        if (client.world != null) {

            for (Instruction instruction : instructions) {
                if (client.world.getBlockEntity(instruction.pos) instanceof LootableContainerBlockEntity blockEntity)
                    containersToVisit.add(blockEntity);
            }

        }

        return containersToVisit;
    }

    @Override
    public boolean doContainerScreenInteractionTick(MinecraftClient client, Screen screen, List<LootableContainerBlockEntity> containersToVisit, int currentContainerIndex) {

        if (instructions.size() <= currentContainerIndex)
            return true;


        Instruction instruction = instructions.get(currentContainerIndex);
        switch (instruction.state) {
            case Gather:
                SortingUtility.collectItems(client, screen, Collections.singletonList(instruction.item), CurrInvClient.fullSuiteSorter.allContainersStockData, false);
                CurrInvClient.sorter.isSortingEnabled = true;
                instructions.set(currentContainerIndex, new Instruction(instruction.pos, State.Sort, instruction.item));
                break;
            case Consolidate:
                SortingUtility.depositItem(client, screen, instruction.item);
                CurrInvClient.sorter.isSortingEnabled = true;
                instructions.set(currentContainerIndex, new Instruction(instruction.pos, State.Sort, instruction.item));
                break;
            case Sort:
                if (!CurrInvClient.sorter.isSorting) {
                    CurrInvClient.sorter.isSortingEnabled = false;
                    return true;
                }
        }

        return false;
    }

    @Override
    public boolean doContainerInteractionTick(MinecraftClient client) {
        return false;
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
                                if (!containerPositions.contains(SortingUtility.getOneBlockPosFromDoubleChests(client, blockPos))) {
                                    containerPositions.add(blockPos);
                                    containers.add(containerItemQuantityAndSlots.get());
                                }
                            }
                        }
                    });
                }

                List<ItemQuantityAndSlots> containersByQuantity = new ArrayList<>(containers);
                containersByQuantity.sort(Comparator.naturalOrder());

                for (int i = 0; i < containers.size(); i++) {
                    BlockPos pos = containersByQuantity.get(i).slotIds.keySet().iterator().next();
                    if (i < containers.size() - 1) {
                        instructions.add(new Instruction(pos, State.Gather, item));
                    } else {
                        instructions.add(new Instruction(pos, State.Consolidate, item));
                    }
                }

            }
        });
    }
}

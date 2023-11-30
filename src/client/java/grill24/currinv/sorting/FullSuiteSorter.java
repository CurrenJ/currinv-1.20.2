package grill24.currinv.sorting;

import grill24.currinv.CurrInvClient;
import grill24.currinv.navigation.NavigationUtility;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.List;

public class FullSuiteSorter {
    private List<LootableContainerBlockEntity> containersToSort;
    private int containerIndex;
    private enum SortState { IDLE, START, NAVIGATE, LOOK_AT_CONTAINER, PRE_SORT, SORT, NEXT_CONTAINER, FINISH }
    private SortState sortState;
    private BlockPos placeToStand;
    private List<ContainerStockData> allContainersStockData;

    public FullSuiteSorter() {
        sortState = SortState.IDLE;
        containersToSort = new ArrayList<>();
        allContainersStockData = new ArrayList<>();
    }

    public void tryStartFullSuiteSort(MinecraftClient client) {
        sortState = SortState.START;
    }

    public void onUpdateTick(MinecraftClient client) {
        assert client.player != null;
        CurrInvClient.sorter.isEnabled = true;
        switch (sortState) {
            case START:
                containerIndex = 0;
                sortState = SortState.NAVIGATE;
                allContainersStockData.clear();
                scanForContainers(client.world, client.player);
                break;
            case NAVIGATE:
                navigateToContainer(client);
                break;
            case LOOK_AT_CONTAINER:
                lookAtContainer(client);
                break;
            case PRE_SORT:
                openContainer(client);
                break;
            case SORT:
                waitForSortToFinish(client);
                break;
            case NEXT_CONTAINER:
                nextContainer();
                break;
            case FINISH:
                finish();
                break;
        }
    }

    private void scanForContainers(ClientWorld world, ClientPlayerEntity player)
    {
        int searchRadius = 16;
        int containerLimit = 100;

        containersToSort.clear();
        for (int x = -searchRadius; x < searchRadius; x++) {
            for (int y = -searchRadius; y < searchRadius; y++) {
                for (int z = -searchRadius; z < searchRadius; z++) {
                    if (containersToSort.size() >= containerLimit) {
                        return;
                    }
                    BlockEntity blockEntity = world.getBlockEntity(player.getBlockPos().add(x, y, z));
                    if (blockEntity instanceof LootableContainerBlockEntity) {
                        containersToSort.add((LootableContainerBlockEntity) blockEntity);
                    }
                }
            }
        }
    }

    private void navigateToContainer(MinecraftClient client) {
        LootableContainerBlockEntity container = containersToSort.get(containerIndex);
        if (container != null) {
            if(client.player.getBlockPos().equals(placeToStand) && !CurrInvClient.navigator.isNavigating() && !CurrInvClient.navigator.isSearchingForPath()) {
                sortState = SortState.LOOK_AT_CONTAINER;
            } else if(!CurrInvClient.navigator.isNavigating() && !CurrInvClient.navigator.isSearchingForPath()) {
                placeToStand = getPlaceToStandByContainer(client.world, client.player, container.getPos());
                if (placeToStand != null) {
                    CurrInvClient.navigator.startNavigationToPosition(client.player.getBlockPos(), placeToStand, false);
                } else {
                    sortState = SortState.NEXT_CONTAINER;
                }
            }
        }
    }

    private void lookAtContainer(MinecraftClient client) {
        assert client.interactionManager != null;

        LootableContainerBlockEntity container = containersToSort.get(containerIndex);
        if (container != null) {
            Vector2d pitchAndYaw = NavigationUtility.getPitchAndYawToLookTowards(client.player, container.getPos());
            client.player.setYaw((float) pitchAndYaw.y);
            client.player.setPitch((float) pitchAndYaw.x);
            sortState = SortState.PRE_SORT;
        }
        else {
            sortState = SortState.NEXT_CONTAINER;
        }
    }

    private void openContainer(MinecraftClient client) {
        assert client.interactionManager != null;

        LootableContainerBlockEntity container = containersToSort.get(containerIndex);
        if (container != null) {
            if(!CurrInvClient.sorter.isSorting) {
                BlockHitResult blockHitResult = (BlockHitResult)client.crosshairTarget;
                ActionResult actionResult = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHitResult);
                if(actionResult == ActionResult.SUCCESS) {
                    sortState = SortState.SORT;
                } else {
                    sortState = SortState.NEXT_CONTAINER;
                }
            }
        } else {
            sortState = SortState.NEXT_CONTAINER;
        }
    }

    private void waitForSortToFinish(MinecraftClient client) {
        if(!CurrInvClient.sorter.isSorting) {
            sortState = SortState.NEXT_CONTAINER;

            assert client.player != null;
            client.player.closeScreen();
        }
    }

    private void nextContainer()
    {
        containerIndex++;
        placeToStand = null;
        if(containerIndex >= containersToSort.size()) {
            sortState = SortState.FINISH;
        } else {
            sortState = SortState.NAVIGATE;
        }
    }

    private void finish()
    {
        CurrInvClient.sorter.isEnabled = false;

        CurrInvClient.sorter.stockData.forEach((key, value) -> allContainersStockData.add(value));
        List<ItemQuantityAndSlots> allStock = new ContainerStockData(allContainersStockData).getOrderedStock();

        sortState = SortState.IDLE;
    }

    private static BlockPos getPlaceToStandByContainer(ClientWorld world, ClientPlayerEntity player, BlockPos containerPos)
    {
        for (BlockPos adjacent : NavigationUtility.getCardinals(containerPos)) {
            for(int dy = 0; dy >= -2; dy--){
                BlockPos blockPos = adjacent.up(dy);
                if (canAccessContainerFromBlockPos(world, player, containerPos, blockPos)) {
                    return blockPos;
                }
            }
        }
        return null;
    }

    private static boolean canAccessContainerFromBlockPos(ClientWorld world, ClientPlayerEntity player, BlockPos containerPos, BlockPos blockPos)
    {
        return NavigationUtility.canPlayerStandOnBlockBelow(world, player, blockPos) && NavigationUtility.hasSpaceForPlayerToStandAtBlockPos(world, player, blockPos);
    }

    public <T extends ScreenHandler> void onScreenTick(MinecraftClient client, HandledScreen<T> screen) {
        if (!isDoneSorting()) {

        }
    }

    private boolean isDoneSorting() {
        return sortState == SortState.FINISH;
    }
}

package grill24.currinv.sorting;

import grill24.currinv.CurrInvClient;
import grill24.currinv.debug.DebugParticles;
import grill24.currinv.debug.DebugUtility;
import grill24.currinv.navigation.NavigationUtility;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2d;

import java.util.*;
import java.util.stream.IntStream;

public class FullSuiteSorter {
    private List<LootableContainerBlockEntity> containersToVisit;
    private int containerIndex;
    private enum State {
        IDLE,
        START,
        BEGIN_NAVIGATE_TO_CONTAINER,
        WAIT_TO_ARRIVE_AT_CONTAINER,
        LOOK_AT_CONTAINER,
        OPEN_CONTAINER,
        WAIT_FOR_CONTAINER_SCREEN,
        DO_CONTAINER_SCREEN_INTERACTION,
        CLOSE_CONTAINER,
        NEXT_CONTAINER,
        FINISH
    }
    private State state;

    private State lastState;
    private BlockPos placeToStand;
    private ContainerStockData allContainersStockData;

    private IFullSuiteSorterMode mode;

    public boolean isDebugModeEnabled;
    public boolean isDebugVerbose;

    public FullSuiteSorter() {
        state = State.IDLE;
        containersToVisit = new ArrayList<>();
        allContainersStockData = new ContainerStockData();
    }

    public void tryStart() {
        if(state == State.IDLE) {
            state = State.START;
        } else {
            finish();
        }
    }

    public void analyzeNearbyContainers() {
        mode = new ScanNearbyChestsMode();
        tryStart();
    }

    public void takeItemsFromNearbyContainers(@NotNull List<Item> items)
    {
        mode = new CollectItemsMode();
        CollectItemsMode collectItemsMode = (CollectItemsMode) mode;
        collectItemsMode.setItemsToCollect(items);
        collectItemsMode.setStockData(allContainersStockData);
        tryStart();
    }

    public void onUpdateTick(MinecraftClient client) {
        assert client.player != null;
        switch (state) {
            case START:
                start(client);
                break;
            case BEGIN_NAVIGATE_TO_CONTAINER:
                startNavigationToContainer(client);
                break;
            case WAIT_TO_ARRIVE_AT_CONTAINER:
                waitForNavigationToContainer(client);
                break;
            case LOOK_AT_CONTAINER:
                lookAtContainer(client);
                break;
            case OPEN_CONTAINER:
                openContainer(client);
                break;
            case WAIT_FOR_CONTAINER_SCREEN:
                // Handled in onScreenUpdateTick
                break;
            case DO_CONTAINER_SCREEN_INTERACTION:
                doContainerInteraction(client);
                break;
            case CLOSE_CONTAINER:
                // Handled in onScreenUpdateTick
                break;
            case NEXT_CONTAINER:
                nextContainer(client);
                break;
            case FINISH:
                finish();
                break;
        }

        if(state != lastState)
            onStateChanged(client);
        lastState = state;
    }

    public <T extends ScreenHandler> void onScreenUpdateTick(MinecraftClient client, HandledScreen<T> screen)
    {
        switch (state) {
            case DO_CONTAINER_SCREEN_INTERACTION:
                doContainerScreenInteraction(client, screen);
                break;
            case WAIT_FOR_CONTAINER_SCREEN:
                waitForContainerScreenToRender(client);
                break;
            case CLOSE_CONTAINER:
                closeOpenContainer(client);
                break;
        }

        if(state != lastState)
            onStateChanged(client);
        lastState = state;
    }

    private void start(MinecraftClient client)
    {
        // If we're already running, stop.
        if(state != State.START) {
            state = State.IDLE;
            return;
        }

        containerIndex = 0;
        state = State.BEGIN_NAVIGATE_TO_CONTAINER;

        // Update our stock reference from the sorter data.
        ArrayList<ContainerStockData> data = new ArrayList<>();
        CurrInvClient.sorter.stockData.forEach((key, value) -> data.add(value));
        allContainersStockData = new ContainerStockData(data);

        containersToVisit = mode.getContainersToVisit(client);
    }

    private void startNavigationToContainer(MinecraftClient client) {
        if(containerIndex >= containersToVisit.size()) {
            state = State.FINISH;
            return;
        }

        LootableContainerBlockEntity container = containersToVisit.get(containerIndex);
        if (container != null) {
            if(!CurrInvClient.navigator.isNavigating() && !CurrInvClient.navigator.isSearchingForPath()){
                placeToStand = getPlaceToStandByContainer(client.world, client.player, container.getPos());
                if (placeToStand != null) {
                    CurrInvClient.navigator.startNavigationToPosition(client.player.getBlockPos(), placeToStand, false, 3000);
                    state = State.WAIT_TO_ARRIVE_AT_CONTAINER;
                } else {
                    state = State.NEXT_CONTAINER;
                }
            }
        }
    }

    private void waitForNavigationToContainer(MinecraftClient client)
    {
        if(!CurrInvClient.navigator.isNavigating() && !CurrInvClient.navigator.isSearchingForPath()) {
            if(client.player != null && client.player.getBlockPos().equals(placeToStand)) {
                state = State.LOOK_AT_CONTAINER;
            } else {
                state = State.NEXT_CONTAINER;
            }
        }
    }

    private void lookAtContainer(MinecraftClient client) {
        LootableContainerBlockEntity container = containersToVisit.get(containerIndex);
        if (client.player != null && container != null) {
            Vector2d pitchAndYaw = NavigationUtility.getPitchAndYawToLookTowardsBlockFace(client.player, container.getPos());
            client.player.setYaw((float) pitchAndYaw.y);
            client.player.setPitch((float) pitchAndYaw.x);
            if(client.crosshairTarget instanceof BlockHitResult blockHitResult && blockHitResult.getBlockPos().equals(container.getPos())) {
                state = State.OPEN_CONTAINER;
            }
        }
        else {
            state = State.NEXT_CONTAINER;
        }
    }

    private void openContainer(MinecraftClient client) {
        assert client.interactionManager != null;

        LootableContainerBlockEntity container = containersToVisit.get(containerIndex);
        if (container != null && client.crosshairTarget instanceof BlockHitResult blockHitResult) {
            ActionResult actionResult = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHitResult);
            if(blockHitResult.getBlockPos().equals(container.getPos())) {
                if (actionResult == ActionResult.SUCCESS) {
                    // TODO Fail to open blocked chests gets us stuck in the netxt state. Need to handle this.
                    state = State.WAIT_FOR_CONTAINER_SCREEN;
                } else {
                    state = State.NEXT_CONTAINER;
                }
            } else {
                DebugUtility.print(client, "§cFailed to open container at " + container.getPos() + " because the block hit result was " + blockHitResult.getBlockPos() + " instead.");
                state = State.NEXT_CONTAINER;
            }
        } else {
            state = State.NEXT_CONTAINER;
        }
    }

    private void waitForContainerScreenToRender(MinecraftClient client)
    {
        assert client.player != null;

        if(client.player.currentScreenHandler != null)
        {
            state = State.DO_CONTAINER_SCREEN_INTERACTION;
        }
    }

    private void doContainerInteraction(MinecraftClient client) {
        if(mode.doContainerInteractionTick(client))
            state = State.CLOSE_CONTAINER;
    }

    private <T extends ScreenHandler> void doContainerScreenInteraction(MinecraftClient client, HandledScreen<T> screen) {
        if(mode.doContainerScreenInteractionTick(client, screen))
            state = State.CLOSE_CONTAINER;
    }

    private void closeOpenContainer(MinecraftClient client)
    {
        assert client.player != null;
        client.player.closeScreen();
        if(client.currentScreen == null)
        {
            state = State.NEXT_CONTAINER;
        }
    }

    private void nextContainer(MinecraftClient client)
    {
        closeOpenContainer(client);
        if(client.currentScreen == null)
        {
            containerIndex++;
            placeToStand = null;
            if(containerIndex >= containersToVisit.size()) {
                state = State.FINISH;
            } else {
                state = State.BEGIN_NAVIGATE_TO_CONTAINER;
            }
        }
    }

    private void finish()
    {
        CurrInvClient.sorter.isSortingEnabled = false;

        state = State.IDLE;
    }

    private void onStateChanged(MinecraftClient client)
    {
        if(isDebugModeEnabled) {
            String color = "§f";
            switch (state) {
                case IDLE:
                    color = "§f";
                    break;
                case START:
                    color = "§1";
                    break;
                case BEGIN_NAVIGATE_TO_CONTAINER:
                    color = "§2";
                    break;
                case WAIT_TO_ARRIVE_AT_CONTAINER:
                    color = "§3";
                    break;
                case LOOK_AT_CONTAINER:
                    color = "§4";
                    break;
                case OPEN_CONTAINER:
                    color = "§5";
                    break;
                case WAIT_FOR_CONTAINER_SCREEN:
                    color = "§6";
                    break;
                case DO_CONTAINER_SCREEN_INTERACTION:
                    color = "§7";
                    break;
                case CLOSE_CONTAINER:
                    color = "§8";
                    break;
                case NEXT_CONTAINER:
                    color = "§9";
                    break;
                case FINISH:
                    color = "§a";
                    break;
            }
            if(isDebugVerbose) {
                DebugUtility.print(client, "State: " + color + state.toString());
            } else {
                if(state == State.NEXT_CONTAINER && lastState == State.BEGIN_NAVIGATE_TO_CONTAINER) {
                    DebugUtility.print(client, "State: §cFailed to find open space adjacent to container. Moving on to next container. " + containersToVisit.get(containerIndex).getPos());
                } else if(state == State.NEXT_CONTAINER && lastState == State.WAIT_TO_ARRIVE_AT_CONTAINER) {
                    DebugUtility.print(client, "State: §cFailed to find path to container. Moving on to next container. " + containersToVisit.get(containerIndex).getPos());
                } else if(state == State.NEXT_CONTAINER && lastState != State.CLOSE_CONTAINER) {
                    DebugUtility.print(client, "State: §cFailed in state " + lastState.name() + ". Moving on to next container. " + containersToVisit.get(containerIndex).getPos());
                } else if(state == State.NEXT_CONTAINER) {
                    DebugUtility.print(client, "State: §aSuccessfully closed container. Moving on to next container.");
                } else if(state == State.FINISH) {
                    DebugUtility.print(client, "State: §aFinished sorting.");
                }
            }
        }
    }

    private static BlockPos getPlaceToStandByContainer(ClientWorld world, ClientPlayerEntity player, BlockPos containerPos)
    {
        BlockPos closest = null;
        int closestDistToPlayer = Integer.MAX_VALUE;
        for (BlockPos adjacent : NavigationUtility.getCardinals(containerPos)) {
            for(int dy = 0; dy >= -2; dy--){
                BlockPos blockPos = adjacent.up(dy);
                if (canAccessContainerFromBlockPos(world, player, containerPos, blockPos) && player.getBlockPos().getManhattanDistance(blockPos) < closestDistToPlayer) {
                    closest = blockPos;
                    closestDistToPlayer = player.getBlockPos().getManhattanDistance(blockPos);
                }
            }
        }
        return closest;
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
        return state == State.FINISH;
    }

    // ---- Debug ----
    public void updateDebugParticles(boolean isEnabled) {
        if (isEnabled && state != State.IDLE) {
            if(containerIndex < containersToVisit.size()) {
                BlockPos activeContainer = containersToVisit.get(containerIndex).getPos();

                DebugParticles.setDebugParticles(DebugParticles.SORTING_PARTICLE_KEY,
                        IntStream.range(0, containersToVisit.size())
                                .filter(i -> i > containerIndex)
                                .mapToObj(containersToVisit::get)
                                .map(BlockEntity::getPos).toList(),
                        ParticleTypes.SOUL);
                DebugParticles.setDebugParticles(DebugParticles.SORTING_ACTIVE_CONTAINER_PARTICLE_KEY, Collections.singletonList(activeContainer), ParticleTypes.FLAME);
            }
        } else {
            DebugParticles.clearDebugParticles(DebugParticles.SORTING_PARTICLE_KEY);
            DebugParticles.clearDebugParticles(DebugParticles.SORTING_ACTIVE_CONTAINER_PARTICLE_KEY);
        }
    }
}

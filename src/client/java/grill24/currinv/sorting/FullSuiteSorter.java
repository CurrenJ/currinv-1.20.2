package grill24.currinv.sorting;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import grill24.currinv.component.*;
import grill24.currinv.debug.DebugParticles;
import grill24.currinv.debug.DebugUtility;
import grill24.currinv.navigation.NavigationUtility;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.DoubleBlockProperties;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector2d;

import java.util.*;
import java.util.stream.IntStream;

@Command
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
    private Queue<BlockPos> placesToStand;
    private BlockPos placeToStand;
    public ContainerStockData allContainersStockData;

    public float lookRadius;

    private IFullSuiteSorterMode mode;

    @CommandOption("debug")
    public boolean isDebugModeEnabled;

    @CommandOption("debugParticles")
    public boolean isDebugParticlesEnabled;

    @CommandOption("debugVerbose")
    public boolean isDebugVerbose;

    public FullSuiteSorter() {
        state = State.IDLE;
        containersToVisit = new ArrayList<>();
        allContainersStockData = new ContainerStockData();
    }

    public void tryStart(CommandContext<FabricClientCommandSource> commandContext) {
        if (state == State.IDLE) {
            DebugUtility.print(commandContext, "Starting full-suite sorter operation...");
            state = State.START;
        } else {
            DebugUtility.print(commandContext, "Canceling full-suite sorter operation...");
            finish();
        }
    }

    @CommandAction("scan")
    public void analyzeNearbyContainers(CommandContext<FabricClientCommandSource> commandContext) {
        mode = new ScanNearbyChestsMode();

        tryStart(commandContext);
    }

    @CommandAction(value = "collect", arguments = {ItemStackArgumentType.class}, argumentKeys = {"item"})
    public void takeItemsFromNearbyContainers(CommandContext<FabricClientCommandSource> commandContext) {
        mode = new CollectItemsMode();
        CollectItemsMode collectItemsMode = (CollectItemsMode) mode;
        Item item = ItemStackArgumentType.getItemStackArgument(commandContext, "item").getItem();
        collectItemsMode.setItemsToCollect(Collections.singletonList(item));
        collectItemsMode.setStockData(allContainersStockData);

        tryStart(commandContext);
    }

    @CommandAction("consolidate")
    public void consolidateAndSort(CommandContext<FabricClientCommandSource> commandContext) {
        mode = new ConsolidateAndSortMode();

        tryStart(commandContext);
    }

    @ClientTick
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

        if (state != lastState)
            onStateChanged(client);
        lastState = state;
    }

    @ScreenTick
    public void onScreenUpdateTick(MinecraftClient client, Screen screen) {
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

        if (state != lastState)
            onStateChanged(client);
        lastState = state;
    }

    private void start(MinecraftClient client) {
        // If we're already running, stop.
        if (state != State.START) {
            state = State.IDLE;
            return;
        }

        containerIndex = 0;
        state = State.BEGIN_NAVIGATE_TO_CONTAINER;

        // Update our stock reference from the sorter data.
        setAllContainersStockData(CurrInvClient.sorter);
        containersToVisit = mode.getContainersToVisit(client);
    }

    public void setAllContainersStockData(Sorter sorter) {
        ArrayList<ContainerStockData> data = new ArrayList<>();
        CurrInvClient.sorter.stockData.forEach((key, value) -> data.add(value));
        allContainersStockData = new ContainerStockData(data);
    }

    private void startNavigationToContainer(MinecraftClient client) {
        if (containerIndex >= containersToVisit.size()) {
            state = State.FINISH;
            return;
        }

        LootableContainerBlockEntity container = containersToVisit.get(containerIndex);
        if (container != null) {
            if (!CurrInvClient.navigator.isNavigating() && !CurrInvClient.navigator.isSearchingForPath()) {
                if (placesToStand == null)
                    placesToStand = getPlacesToStandByContainer(client.interactionManager, client.world, client.player, container.getPos());

                if (!placesToStand.isEmpty()) {
                    placeToStand = placesToStand.poll();
                    CurrInvClient.navigator.startNavigationToPosition(client.player.getBlockPos(), placeToStand, false, 3000);
                    state = State.WAIT_TO_ARRIVE_AT_CONTAINER;
                } else {
                    if (client.world != null) {
                        // If we fail on this half of the chest - flip to the other half and try again
                        BlockPos containerPos = container.getPos();
                        BlockPos containerOtherHalfPos = SortingUtility.getOneBlockPosFromDoubleChests(client, containerPos, DoubleBlockProperties.Type.SECOND);
                        if (containerPos != containerOtherHalfPos) {
                            DebugUtility.print(client, "State: §cFailed to find open space adjacent to container. Trying to navigate to other half of double chest. " + containersToVisit.get(containerIndex).getPos());
                            containersToVisit.set(containerIndex, (LootableContainerBlockEntity) client.world.getBlockEntity(containerOtherHalfPos));
                            return;
                        }
                    }

                    state = State.NEXT_CONTAINER;
                }
            }
        }
    }

    private void waitForNavigationToContainer(MinecraftClient client) {
        if (!CurrInvClient.navigator.isNavigating() && !CurrInvClient.navigator.isSearchingForPath()) {
            if (client.player != null && client.player.getBlockPos().equals(placeToStand)) {
                placesToStand = null;
                lookRadius = 0;
                state = State.LOOK_AT_CONTAINER;
            } else if (!placesToStand.isEmpty()) {
                // If we're not at the place to stand, but there are still places to stand, go to the next place to stand.
                state = State.BEGIN_NAVIGATE_TO_CONTAINER;
            } else {
                placesToStand = null;
                if (client.world != null) {

                    BlockPos containerPos = containersToVisit.get(containerIndex).getPos();
                    BlockPos containerOtherHalfPos = SortingUtility.getOneBlockPosFromDoubleChests(client, containerPos, DoubleBlockProperties.Type.SECOND);
                    if (containerPos != containerOtherHalfPos) {
                        DebugUtility.print(client, "State: §cFailed to find path to container. Trying to navigate to other half of double chest. " + containersToVisit.get(containerIndex).getPos());
                        containersToVisit.set(containerIndex, (LootableContainerBlockEntity) client.world.getBlockEntity(containerOtherHalfPos));
                        state = State.BEGIN_NAVIGATE_TO_CONTAINER;
                        return;
                    }

                }

                state = State.NEXT_CONTAINER;
            }
        }
    }

    private void lookAtContainer(MinecraftClient client) {
        LootableContainerBlockEntity container = containersToVisit.get(containerIndex);

        final long millisPerRotation = 2000;
        float dt = (System.currentTimeMillis() % (millisPerRotation) / ((float) millisPerRotation));
        double angle = 2 * Math.PI * dt;
        lookRadius += dt * 0.25f;

        if (client.player != null && container != null) {
            float lerpFactor = 0.8f;

            Vector2d pitchAndYaw = NavigationUtility.getPitchAndYawToLookTowardsBlockFace(client.world, client.player, container.getPos());
            float yaw = (float) pitchAndYaw.y;
            yaw += (float) (Math.sin(angle) * lookRadius);
            client.player.setYaw(NavigationUtility.angleLerp(client.player.getYaw(), yaw, lerpFactor));

            float pitch = (float) pitchAndYaw.x;
            pitch += (float) (Math.cos(angle) * lookRadius);
            client.player.setPitch(NavigationUtility.angleLerp(client.player.getPitch(), pitch, lerpFactor));

            if (client.crosshairTarget instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof ItemFrameEntity itemFrameEntity) {
                // copied from AbstractDecorationEntity#canStayAttached
                BlockPos attachedPos = itemFrameEntity.getDecorationBlockPos().offset(itemFrameEntity.getHorizontalFacing().getOpposite());
                client.crosshairTarget = new BlockHitResult(client.crosshairTarget.getPos(), itemFrameEntity.getHorizontalFacing(), attachedPos, false);
            }
            if (client.crosshairTarget instanceof BlockHitResult blockHitResult && blockHitResult.getBlockPos().equals(container.getPos())) {
                // Unlike other states, we don't wait for the next tick to open the container. We do it immediately. This is because we want to open the container as soon as we look at it.
                // TODO: Merge these two states.
                state = State.OPEN_CONTAINER;
                openContainer(client);
            }
        } else {
            state = State.NEXT_CONTAINER;
        }
    }


    private void openContainer(MinecraftClient client) {
        assert client.interactionManager != null;

        LootableContainerBlockEntity container = containersToVisit.get(containerIndex);

        if (container != null) {
            // All credit for this bit of logic goes to @Giselbaer (https://www.curseforge.com/minecraft/mc-mods/clickthrough) <3
            if (client.crosshairTarget instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof ItemFrameEntity itemFrameEntity) {
                // copied from AbstractDecorationEntity#canStayAttached
                BlockPos attachedPos = itemFrameEntity.getDecorationBlockPos().offset(itemFrameEntity.getHorizontalFacing().getOpposite());
                client.crosshairTarget = new BlockHitResult(client.crosshairTarget.getPos(), itemFrameEntity.getHorizontalFacing(), attachedPos, false);
            }

            if (client.crosshairTarget instanceof BlockHitResult blockHitResult) {
                ActionResult actionResult = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHitResult);
                if (blockHitResult.getBlockPos().equals(container.getPos())) {
                    if (actionResult == ActionResult.SUCCESS) {
                        // TODO Fail to open blocked chests gets us stuck in the netxt state. Need to handle this.
                        state = State.WAIT_FOR_CONTAINER_SCREEN;
                        return;
                    } else {
                        state = State.NEXT_CONTAINER;
                        return;
                    }
                } else {
                    DebugUtility.print(client, "§cFailed to open container at " + container.getPos() + " because the block hit result was " + blockHitResult.getBlockPos() + " instead.");
                    state = State.NEXT_CONTAINER;
                    return;
                }
            }
        }
        state = State.NEXT_CONTAINER;
    }

    private void waitForContainerScreenToRender(MinecraftClient client) {
        assert client.player != null;

        if (client.player.currentScreenHandler != null) {
            state = State.DO_CONTAINER_SCREEN_INTERACTION;
        }
    }

    private void doContainerInteraction(MinecraftClient client) {
        if (mode.doContainerInteractionTick(client))
            state = State.CLOSE_CONTAINER;
    }

    private void doContainerScreenInteraction(MinecraftClient client, Screen screen) {
        if (mode.doContainerScreenInteractionTick(client, screen, containersToVisit, containerIndex))
            state = State.CLOSE_CONTAINER;
    }

    private void closeOpenContainer(MinecraftClient client) {
        assert client.player != null;
        client.player.closeScreen();
        if (client.currentScreen == null) {
            state = State.NEXT_CONTAINER;
        }
    }

    private void nextContainer(MinecraftClient client) {
        closeOpenContainer(client);
        if (client.currentScreen == null) {
            containerIndex++;
            placesToStand = null;
            if (containerIndex >= containersToVisit.size()) {
                state = State.FINISH;
            } else {
                state = State.BEGIN_NAVIGATE_TO_CONTAINER;
            }
        }
    }

    private void finish() {
        CurrInvClient.sorter.isSortingEnabled = false;

        state = State.IDLE;

        DebugUtility.print(MinecraftClient.getInstance(), "Full-suite sorter operation finished.");
    }

    private void onStateChanged(MinecraftClient client) {
        if (isDebugModeEnabled) {
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
            if (isDebugVerbose) {
                DebugUtility.print(client, "State: " + color + state);
            } else {
                if (state == State.NEXT_CONTAINER && lastState == State.BEGIN_NAVIGATE_TO_CONTAINER) {
                    DebugUtility.print(client, "State: §cFailed to find open space adjacent to container. Moving on to next container. " + containersToVisit.get(containerIndex).getPos());
                } else if (state == State.NEXT_CONTAINER && lastState == State.WAIT_TO_ARRIVE_AT_CONTAINER) {
                    DebugUtility.print(client, "State: §cFailed to find path to container. Moving on to next container. " + containersToVisit.get(containerIndex).getPos());
                } else if (state == State.NEXT_CONTAINER && lastState != State.CLOSE_CONTAINER) {
                    DebugUtility.print(client, "State: §cFailed in state " + lastState.name() + ". Moving on to next container. " + containersToVisit.get(containerIndex).getPos());
                } else if (state == State.NEXT_CONTAINER) {
                    DebugUtility.print(client, "State: §aSuccessfully closed container. Moving on to next container.");
                } else if (state == State.FINISH) {
                    DebugUtility.print(client, "State: §aFinished sorting.");
                }
            }
        }
    }

    private static Queue<BlockPos> getPlacesToStandByContainer(ClientPlayerInteractionManager interactionManager, ClientWorld world, ClientPlayerEntity player, BlockPos containerPos) {
        // TODO: Massively duplicated and redundant code from nav to marker - but it works and I'm too lazy to make it better. Could make a function that takes a functional interface that takes in a blockPos.

        Queue<BlockPos> eligible = new LinkedList<>();

        final int MAX_DIAMETER = (int) Math.ceil(interactionManager.getReachDistance() * 2);
        for (int cubeSideLength = 3; cubeSideLength <= MAX_DIAMETER; cubeSideLength += 2) {
            // Each iteration of this outerloop is O(n^2) where n is cubeSideLength. It's better than the other solutions.

            // Shift cube to center it
            int offset = -Math.floorDiv(cubeSideLength, 2);

            // Iterate through the faces of the cube
            for (int x = offset; x < cubeSideLength + offset; x += cubeSideLength - 1) {
                for (int y = offset; y < cubeSideLength + offset; y++) {
                    for (int z = offset; z < cubeSideLength + offset; z++) {
                        BlockPos blockPos1 = containerPos.add(x, y, z);
                        if (canAccessContainerFromBlockPos(interactionManager, world, player, containerPos, blockPos1)) {
                            eligible.add(blockPos1);
                        }

                        BlockPos blockPos2 = containerPos.add(y, x, z);
                        if (canAccessContainerFromBlockPos(interactionManager, world, player, containerPos, blockPos2)) {
                            eligible.add(blockPos2);
                        }

                        BlockPos blockPos3 = containerPos.add(y, z, x);
                        if (canAccessContainerFromBlockPos(interactionManager, world, player, containerPos, blockPos3)) {
                            eligible.add(blockPos3);
                        }
                    }
                }
            }
        }
        return eligible;
    }

    private static boolean canAccessContainerFromBlockPos(ClientPlayerInteractionManager interactionManager, ClientWorld world, ClientPlayerEntity player, BlockPos containerPos, BlockPos blockPos) {
        // Returns true if there is a clear line of sight between player and container, and the player can stand at blockPos.
        boolean canSee = NavigationUtility.canPlayerSeeBlockPosFromBlockPos(interactionManager, world, player, blockPos, containerPos);
        boolean canStand = NavigationUtility.hasSpaceForPlayerToStandAtBlockPos(world, player, blockPos);
        boolean canStandOnBlockBelow = NavigationUtility.canPlayerStandOnBlockBelow(world, player, blockPos);
        return canStandOnBlockBelow && canStand && canSee;
    }

    // ---- Debug ----

    @ClientTick
    public void updateDebugParticles() {
        if (isDebugParticlesEnabled && state != State.IDLE) {
            if (containerIndex < containersToVisit.size()) {
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

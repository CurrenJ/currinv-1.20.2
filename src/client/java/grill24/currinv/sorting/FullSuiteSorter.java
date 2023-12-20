package grill24.currinv.sorting;

import com.mojang.brigadier.context.CommandContext;
import grill24.currinv.CurrInvClient;
import grill24.currinv.debug.DebugParticles;
import grill24.currinv.debug.DebugUtility;
import grill24.currinv.navigation.NavigationUtility;
import grill24.sizzlib.component.*;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoubleBlockProperties;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector2d;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Command
public class FullSuiteSorter {
    public Supplier<LootableContainerBlockEntity> containersToVisit;
    LootableContainerBlockEntity currentContainer;

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

    @CommandOption("state")
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

    public enum DebugRays {OFF, SUCCESS, FAIL, ALL}

    @CommandOption(value = "debugRays")
    public DebugRays debugRays = DebugRays.OFF;

    int operationsDone;


    public FullSuiteSorter() {
        state = State.IDLE;
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
        mode = new ScanNearbyChestsMode(MinecraftClient.getInstance());

        tryStart(commandContext);
    }

    @CommandAction("collect")
    public void takeItemsFromNearbyContainers(CommandContext<FabricClientCommandSource> commandContext, ItemStackArgument itemStackArgument) {
        Item item = itemStackArgument.getItem();
        mode = new CollectItemsMode(MinecraftClient.getInstance(), Collections.singletonList(item));

        tryStart(commandContext);
    }

    @CommandAction("consolidate")
    public void consolidateAndSort(CommandContext<FabricClientCommandSource> commandContext) {
        mode = new ConsolidateAndSortMode(MinecraftClient.getInstance());

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
                closeOpenContainer(client, screen);
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

        state = State.BEGIN_NAVIGATE_TO_CONTAINER;
        placesToStand = null;
        operationsDone = 0;

        // Update our stock reference from the sorter data.
        setAllContainersStockData(CurrInvClient.sorter);
        containersToVisit = mode.getContainersToVisitSupplier(client);

        currentContainer = containersToVisit.get();
    }

    public void setAllContainersStockData(Sorter sorter) {
        ArrayList<ContainerStockData> data = new ArrayList<>();
        CurrInvClient.sorter.stockData.forEach((key, value) -> data.add(value));
        allContainersStockData = new ContainerStockData(data);
    }

    private void startNavigationToContainer(MinecraftClient client) {
        if (currentContainer != null) {
            if (!CurrInvClient.navigator.isNavigating() && !CurrInvClient.navigator.isSearchingForPath()) {
                if (placesToStand == null)
                    placesToStand = getPlacesToStandByContainer(client.interactionManager, client.world, client.player, currentContainer.getPos());

                if (!placesToStand.isEmpty()) {
                    placeToStand = placesToStand.poll();
                    CurrInvClient.navigator.startNavigationToPosition(client.player.getBlockPos(), placeToStand, false, 1000);
                    state = State.WAIT_TO_ARRIVE_AT_CONTAINER;
                } else {
                    if (client.world != null) {
                        // If we fail on this half of the chest - flip to the other half and try again
                        BlockPos containerPos = currentContainer.getPos();
                        BlockPos containerOtherHalfPos = SortingUtility.getOneBlockPosFromDoubleChests(client, containerPos, DoubleBlockProperties.Type.SECOND);
                        if (containerPos != containerOtherHalfPos) {
                            DebugUtility.print(client, "State: §cFailed to find open space adjacent to container. Trying to navigate to other half of double chest. " + currentContainer.getPos());
                            currentContainer = (LootableContainerBlockEntity) client.world.getBlockEntity(containerOtherHalfPos);
                            return;
                        }
                    }

                    mode.onContainerAccessFail(client);
                    state = State.NEXT_CONTAINER;
                }
            }
        } else {
            state = State.FINISH;
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

                    BlockPos containerPos = currentContainer.getPos();
                    BlockPos containerOtherHalfPos = SortingUtility.getOneBlockPosFromDoubleChests(client, containerPos, DoubleBlockProperties.Type.SECOND);
                    if (containerPos != containerOtherHalfPos) {
                        DebugUtility.print(client, "State: §cFailed to find path to container. Trying to navigate to other half of double chest. " + currentContainer.getPos());
                        currentContainer = (LootableContainerBlockEntity) client.world.getBlockEntity(containerOtherHalfPos);
                        state = State.BEGIN_NAVIGATE_TO_CONTAINER;
                        return;
                    }

                }

                mode.onContainerAccessFail(client);
                state = State.NEXT_CONTAINER;
            }
        }
    }

    private void lookAtContainer(MinecraftClient client) {
        final long millisPerRotation = 2000;
        float dt = (System.currentTimeMillis() % (millisPerRotation) / ((float) millisPerRotation));
        double angle = 2 * Math.PI * dt;
        lookRadius += dt * 0.25f;

        if (client.player != null && currentContainer != null && lookRadius < 20) {
            float lerpFactor = 0.8f;

            Vector2d pitchAndYaw = NavigationUtility.getPitchAndYawToLookTowardsBlockFace(client.world, client.player, currentContainer.getPos());
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
            if (client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos blockPos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
                BlockState blockState = client.world.getBlockState(blockPos);
                Block block = blockState.getBlock();
                if (blockPos.equals(currentContainer.getPos())) {
                    state = State.OPEN_CONTAINER;
                    openContainer(client);
                    return;
                }

                // TODO: Duplicated code from openContainer - refactor
                if (block instanceof WallSignBlock) {
                    WallSignBlock sign = (WallSignBlock) block;
                    BlockPos attachedPos = blockPos.offset(blockState.get(sign.FACING).getOpposite());
                    if (!isClickableBlockAt(client, attachedPos)) {
                        return;
                    }
                    BlockEntity entity = client.world.getBlockEntity(blockPos);
                    if (!(entity instanceof SignBlockEntity)) {
                        return;
                    }

                    // Click through sign
                    if (attachedPos.equals(currentContainer.getPos())) {
                        client.crosshairTarget = new BlockHitResult(client.crosshairTarget.getPos(), blockState.get(WallSignBlock.FACING), attachedPos, false);
                        state = State.OPEN_CONTAINER;
                        openContainer(client);
                    }
                }
            }
        } else {
            state = State.NEXT_CONTAINER;
        }
    }

    private boolean isClickableBlockAt(MinecraftClient client, BlockPos pos) {
        BlockEntity entity = client.world.getBlockEntity(pos);
        return (entity != null && entity instanceof LockableContainerBlockEntity);
    }


    private void openContainer(MinecraftClient client) {
        assert client.interactionManager != null;

        if (currentContainer != null) {
            // All credit for this bit of logic goes to @Giselbaer (https://www.curseforge.com/minecraft/mc-mods/clickthrough) <3
            if (client.crosshairTarget instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof ItemFrameEntity itemFrameEntity) {
                // copied from AbstractDecorationEntity#canStayAttached
                BlockPos attachedPos = itemFrameEntity.getDecorationBlockPos().offset(itemFrameEntity.getHorizontalFacing().getOpposite());
                client.crosshairTarget = new BlockHitResult(client.crosshairTarget.getPos(), itemFrameEntity.getHorizontalFacing(), attachedPos, false);
            } else if (client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockPos blockPos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
                BlockState state = client.world.getBlockState(blockPos);
                Block block = state.getBlock();
                if (block instanceof WallSignBlock) {
                    WallSignBlock sign = (WallSignBlock) block;
                    BlockPos attachedPos = blockPos.offset(state.get(sign.FACING).getOpposite());
                    if (!isClickableBlockAt(client, attachedPos)) {
                        return;
                    }
                    BlockEntity entity = client.world.getBlockEntity(blockPos);
                    if (!(entity instanceof SignBlockEntity)) {
                        return;
                    }

                    client.crosshairTarget = new BlockHitResult(client.crosshairTarget.getPos(), state.get(WallSignBlock.FACING), attachedPos, false);
                }
            }

            if (client.crosshairTarget instanceof BlockHitResult blockHitResult) {
                ActionResult actionResult = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, blockHitResult);
                if (blockHitResult.getBlockPos().equals(currentContainer.getPos())) {
                    if (actionResult == ActionResult.SUCCESS) {
                        // TODO Fail to open blocked chests gets us stuck in the netxt blockState. Need to handle this.
                        state = State.WAIT_FOR_CONTAINER_SCREEN;
                        return;
                    } else {
                        state = State.NEXT_CONTAINER;
                        return;
                    }
                } else {
                    DebugUtility.print(client, "§cFailed to open container at " + currentContainer.getPos() + " because the block hit result was " + blockHitResult.getBlockPos() + " instead.");
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
        if (mode.doContainerScreenInteractionTick(client, screen))
            state = State.CLOSE_CONTAINER;
    }

    private void closeOpenContainer(MinecraftClient client, Screen screen) {
        if (client.player != null) {
            if (screen instanceof HandledScreen<?> handledScreen) {
                client.player.closeHandledScreen();
                if (currentContainer != null) {
                    currentContainer.onClose(client.player);
                }
                if (client.currentScreen == null) {
                    state = State.NEXT_CONTAINER;
                }
            }
        }
    }

    private void nextContainer(MinecraftClient client) {
        if (client.currentScreen == null) {
            placesToStand = null;
            operationsDone++;

            currentContainer = containersToVisit.get();
            if (currentContainer == null) {
                state = State.FINISH;
            } else if (CurrInvClient.config.isContainerExemptFromSorting(currentContainer.getPos())) {
                DebugUtility.print(client, "Container is exempt from sorting.");
            } else {
                state = State.BEGIN_NAVIGATE_TO_CONTAINER;
            }
        }
    }

    private void finish() {
        CurrInvClient.sorter.isSortingEnabled = false;

        state = State.IDLE;

        DebugUtility.print(MinecraftClient.getInstance(), "Full-suite sorter operation finished. " + operationsDone + " operations.");
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
                    DebugUtility.print(client, "State: §cFailed to find open space adjacent to container. Moving on to next container. " + currentContainer.getPos());
                } else if (state == State.NEXT_CONTAINER && lastState == State.WAIT_TO_ARRIVE_AT_CONTAINER) {
                    DebugUtility.print(client, "State: §cFailed to find path to container. Moving on to next container. " + currentContainer.getPos());
                } else if (state == State.NEXT_CONTAINER && lastState != State.CLOSE_CONTAINER) {
                    DebugUtility.print(client, "State: §cFailed in blockState " + lastState.name() + ". Moving on to next container. " + currentContainer.getPos());
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

            TreeMap<Integer, BlockPos> orderedPos = new TreeMap<>();
            HashSet<BlockPos> seen = new HashSet<>();
            // Iterate through the faces of the cube
            for (int x = offset; x < cubeSideLength + offset; x += cubeSideLength - 1) {
                for (int y = offset; y < cubeSideLength + offset; y++) {
                    for (int z = offset; z < cubeSideLength + offset; z++) {
                        BlockPos blockPos1 = containerPos.add(x, y, z);
                        int manhattanDistance = Math.abs(x) + Math.abs(y) + Math.abs(z);
                        if (!seen.contains(blockPos1) && canAccessContainerFromBlockPos(interactionManager, world, player, containerPos, blockPos1)) {
                            orderedPos.put(manhattanDistance, blockPos1);
                        }
                        seen.add(blockPos1);

                        BlockPos blockPos2 = containerPos.add(y, x, z);
                        if (!seen.contains(blockPos2) && canAccessContainerFromBlockPos(interactionManager, world, player, containerPos, blockPos2)) {
                            orderedPos.put(manhattanDistance, blockPos2);
                        }
                        seen.add(blockPos2);

                        BlockPos blockPos3 = containerPos.add(y, z, x);
                        if (!seen.contains(blockPos3) && canAccessContainerFromBlockPos(interactionManager, world, player, containerPos, blockPos3)) {
                            orderedPos.put(manhattanDistance, blockPos3);
                        }
                        seen.add(blockPos3);
                    }
                }
            }
            eligible.addAll(orderedPos.values());
        }
        return eligible;
    }

    private static boolean canAccessContainerFromBlockPos(ClientPlayerInteractionManager interactionManager, ClientWorld world, ClientPlayerEntity player, BlockPos containerPos, BlockPos blockPos) {
        // Returns true if there is a clear line of sight between player and container, and the player can stand at blockPos.
        BooleanSupplier canSee = () -> NavigationUtility.canPlayerSeeBlockPosFromBlockPos(interactionManager, world, player, blockPos, containerPos);
        BooleanSupplier canStand = () -> NavigationUtility.hasSpaceForPlayerToStandAtBlockPos(world, player, blockPos);
        BooleanSupplier canStandOnBlockBelow = () -> NavigationUtility.canPlayerStandOnBlockBelow(world, player, blockPos);
        return canStandOnBlockBelow.getAsBoolean() && canStand.getAsBoolean() && canSee.getAsBoolean();
    }

    // ---- Debug ----

    @ClientTick
    public void updateDebugParticles() {
        if (isDebugParticlesEnabled && state != State.IDLE) {
            if (currentContainer != null) {
                BlockPos activeContainer = currentContainer.getPos();

                // TODO: Use fullsuitesortermode
//                DebugParticles.setDebugParticles(DebugParticles.SORTING_PARTICLE_KEY,
//                        IntStream.range(0, containersToVisit.size())
//                                .filter(i -> i > containerIndex)
//                                .mapToObj(containersToVisit::get)
//                                .map(BlockEntity::getPos).toList(),
//                        ParticleTypes.SOUL);
//                DebugParticles.setDebugParticles(DebugParticles.SORTING_ACTIVE_CONTAINER_PARTICLE_KEY, Collections.singletonList(activeContainer), ParticleTypes.FLAME);
            }
        } else {
            DebugParticles.clearDebugParticles(DebugParticles.SORTING_PARTICLE_KEY);
            DebugParticles.clearDebugParticles(DebugParticles.SORTING_ACTIVE_CONTAINER_PARTICLE_KEY);
        }
    }
}

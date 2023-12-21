package grill24.currinv.persistence;

import grill24.currinv.CurrInvClient;
import grill24.currinv.debug.CurrInvDebugRenderer;
import grill24.currinv.sorting.ContainerSortingConfiguration;
import grill24.currinv.sorting.Sorter;
import grill24.sizzlib.component.ClientTick;
import grill24.sizzlib.component.Command;
import grill24.sizzlib.component.CommandAction;
import grill24.sizzlib.component.CommandOption;
import grill24.sizzlib.persistence.PersistenceManager;
import grill24.sizzlib.persistence.Persists;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.util.HashMap;
import java.util.Optional;

@Command("config")
public class Config extends grill24.sizzlib.persistence.Persistable {
    private final static String DATA_FILE_NAME = "config.dat";
    private final static MinecraftClient client = MinecraftClient.getInstance();

    @Persists
    @CommandOption(readOnly = true, debug = true)
    protected long biomeAccessSeed = -1;

    @Persists
    @CommandOption(readOnly = true, debug = true)
    protected HashMap<BlockPos, ContainerSortingConfiguration> sortingContainerConfigurations;

    @Persists
    @CommandOption
    public Sorter.SortingStyle currentSortingStyle = Sorter.SortingStyle.QUANTITY;

    @CommandOption("drawExemptContainers")
    public boolean shouldDrawSortingExemptContainers;

    public Config(long biomeAccessSeed) {
        this.biomeAccessSeed = biomeAccessSeed;
        sortingContainerConfigurations = new HashMap<>();

        PersistenceManager.load(this);
    }

    @ClientTick(20)
    public void updateMarkers() {
        if (shouldDrawSortingExemptContainers)
            for (BlockPos pos : sortingContainerConfigurations.keySet())
                if (isContainerExemptFromSorting(pos))
                    CurrInvClient.currInvDebugRenderer.addCube(pos, 0, 1000, CurrInvDebugRenderer.RED);
    }

    @CommandAction("markSortingExempt")
    public void markCrosshairContainerExempt() {
        if (client.crosshairTarget instanceof BlockHitResult blockHitResult) {
            BlockPos pos = blockHitResult.getBlockPos().toImmutable();
            if (client.world != null && client.world.getBlockEntity(pos) instanceof LootableContainerBlockEntity) {
                ContainerSortingConfiguration containerSortingConfiguration = sortingContainerConfigurations.getOrDefault(pos, new ContainerSortingConfiguration());
                containerSortingConfiguration.isExempt = true;
                setSortingContainerConfiguration(pos, containerSortingConfiguration);

                CurrInvClient.sorter.removeData(pos);

                PersistenceManager.save(this);
            }
        }
    }

    public void setSortingContainerConfiguration(BlockPos containerPos, ContainerSortingConfiguration containerSortingConfiguration) {
        sortingContainerConfigurations.put(containerPos, containerSortingConfiguration);
    }

    public Optional<ContainerSortingConfiguration> getContainerSortingConfiguration(BlockPos containerPos) {
        if (sortingContainerConfigurations.containsKey(containerPos))
            return Optional.of(sortingContainerConfigurations.get(containerPos));
        else
            return Optional.empty();
    }

    public boolean isContainerExemptFromSorting(BlockPos containerPos) {
        Optional<ContainerSortingConfiguration> containerSortingConfiguration = getContainerSortingConfiguration(containerPos);
        return containerSortingConfiguration.isPresent() && containerSortingConfiguration.get().isExempt;
    }

    @Override
    public File getFile() {
        String[] tokens = DATA_FILE_NAME.split("\\.");
        String fileName = String.format("%s_%s.%s", tokens[0], this.biomeAccessSeed, tokens[1]);
        return CurrInvClient.getFileInDataDir(fileName);
    }

    public long getBiomeAccessSeed() {
        return biomeAccessSeed;
    }
}


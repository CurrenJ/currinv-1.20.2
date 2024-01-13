package grill24.currinv.persistence;

import grill24.currinv.CurrInvClient;
import grill24.currinv.debug.CurrInvDebugRenderer;
import grill24.currinv.sorting.ContainerSortingConfiguration;
import grill24.currinv.sorting.Sorter;
import grill24.sizzlib.component.ClientTick;
import grill24.sizzlib.component.Command;
import grill24.sizzlib.component.CommandAction;
import grill24.sizzlib.component.CommandOption;
import grill24.sizzlib.persistence.IFileProvider;
import grill24.sizzlib.persistence.PersistenceManager;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.util.HashMap;
import java.util.Optional;

@Command("config")
public class Config implements IFileProvider {
    private final static String DATA_FILE_NAME = "config.json";
    private final static MinecraftClient client = MinecraftClient.getInstance();

    @CommandOption(readOnly = true, debug = true)
    protected transient long biomeAccessSeed = -1;

    @CommandOption(readOnly = true, debug = true)
    protected HashMap<BlockPos, ContainerSortingConfiguration> sortingContainerConfigurations;

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
                containerSortingConfiguration.isExempt = !containerSortingConfiguration.isExempt;
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
        return getWorldAssociatedFile(DATA_FILE_NAME);
    }

    public File getWorldAssociatedFile(String fileName) {
        String[] tokens = fileName.split("\\.");
        String fullFileName = String.format("%s_%s.%s", tokens[0], this.biomeAccessSeed, tokens[1]);
        return CurrInvClient.getFileInDataDir(fullFileName);
    }

    public long getBiomeAccessSeed() {
        return biomeAccessSeed;
    }
}


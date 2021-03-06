package net.minecraft.server;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.commands.CommandDispatcher;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.server.packs.EnumResourcePackType;
import net.minecraft.server.packs.IResourcePack;
import net.minecraft.server.packs.resources.IReloadableResourceManager;
import net.minecraft.server.packs.resources.IResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.ITagRegistry;
import net.minecraft.tags.TagRegistry;
import net.minecraft.util.Unit;
import net.minecraft.world.item.crafting.CraftingManager;
import net.minecraft.world.level.storage.loot.ItemModifierManager;
import net.minecraft.world.level.storage.loot.LootPredicateManager;
import net.minecraft.world.level.storage.loot.LootTableRegistry;

public class DataPackResources implements AutoCloseable {

    private static final CompletableFuture<Unit> DATA_RELOAD_INITIAL_TASK = CompletableFuture.completedFuture(Unit.INSTANCE);
    private final IReloadableResourceManager resources;
    public CommandDispatcher commands;
    private final CraftingManager recipes;
    private final TagRegistry tagManager;
    private final LootPredicateManager predicateManager;
    private final LootTableRegistry lootTables;
    private final ItemModifierManager itemModifierManager;
    private final AdvancementDataWorld advancements;
    private final CustomFunctionManager functionLibrary;

    public DataPackResources(IRegistryCustom iregistrycustom, CommandDispatcher.ServerType commanddispatcher_servertype, int i) {
        this.resources = new ResourceManager(EnumResourcePackType.SERVER_DATA);
        this.recipes = new CraftingManager();
        this.predicateManager = new LootPredicateManager();
        this.lootTables = new LootTableRegistry(this.predicateManager);
        this.itemModifierManager = new ItemModifierManager(this.predicateManager, this.lootTables);
        this.advancements = new AdvancementDataWorld(this.predicateManager);
        this.tagManager = new TagRegistry(iregistrycustom);
        this.commands = new CommandDispatcher(commanddispatcher_servertype);
        this.functionLibrary = new CustomFunctionManager(i, this.commands.getDispatcher());
        this.resources.registerReloadListener(this.tagManager);
        this.resources.registerReloadListener(this.predicateManager);
        this.resources.registerReloadListener(this.recipes);
        this.resources.registerReloadListener(this.lootTables);
        this.resources.registerReloadListener(this.itemModifierManager);
        this.resources.registerReloadListener(this.functionLibrary);
        this.resources.registerReloadListener(this.advancements);
    }

    public CustomFunctionManager getFunctionLibrary() {
        return this.functionLibrary;
    }

    public LootPredicateManager getPredicateManager() {
        return this.predicateManager;
    }

    public LootTableRegistry getLootTables() {
        return this.lootTables;
    }

    public ItemModifierManager getItemModifierManager() {
        return this.itemModifierManager;
    }

    public ITagRegistry getTags() {
        return this.tagManager.getTags();
    }

    public CraftingManager getRecipeManager() {
        return this.recipes;
    }

    public CommandDispatcher getCommands() {
        return this.commands;
    }

    public AdvancementDataWorld getAdvancements() {
        return this.advancements;
    }

    public IResourceManager getResourceManager() {
        return this.resources;
    }

    public static CompletableFuture<DataPackResources> loadResources(List<IResourcePack> list, IRegistryCustom iregistrycustom, CommandDispatcher.ServerType commanddispatcher_servertype, int i, Executor executor, Executor executor1) {
        DataPackResources datapackresources = new DataPackResources(iregistrycustom, commanddispatcher_servertype, i);
        CompletableFuture<Unit> completablefuture = datapackresources.resources.reload(executor, executor1, list, DataPackResources.DATA_RELOAD_INITIAL_TASK);

        return completablefuture.whenComplete((unit, throwable) -> {
            if (throwable != null) {
                datapackresources.close();
            }

        }).thenApply((unit) -> {
            return datapackresources;
        });
    }

    public void updateGlobals() {
        this.tagManager.getTags().bindToGlobal();
    }

    public void close() {
        this.resources.close();
    }
}

package net.minecraft.stats;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.ResourceLocationException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookRemovePacket;
import net.minecraft.network.protocol.game.ClientboundRecipeBookSettingsPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.slf4j.Logger;

public class ServerRecipeBook extends RecipeBook {
    public static final String RECIPE_BOOK_TAG = "recipeBook";
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ServerRecipeBook.DisplayResolver displayResolver;
    @VisibleForTesting
    public final Set<ResourceKey<Recipe<?>>> known = Sets.newIdentityHashSet();
    @VisibleForTesting
    protected final Set<ResourceKey<Recipe<?>>> highlight = Sets.newIdentityHashSet();

    public ServerRecipeBook(ServerRecipeBook.DisplayResolver displayResolver) {
        this.displayResolver = displayResolver;
    }

    public void add(ResourceKey<Recipe<?>> recipe) {
        this.known.add(recipe);
    }

    public boolean contains(ResourceKey<Recipe<?>> recipe) {
        return this.known.contains(recipe);
    }

    public void remove(ResourceKey<Recipe<?>> recipe) {
        this.known.remove(recipe);
        this.highlight.remove(recipe);
    }

    public void removeHighlight(ResourceKey<Recipe<?>> recipe) {
        this.highlight.remove(recipe);
    }

    private void addHighlight(ResourceKey<Recipe<?>> recipe) {
        this.highlight.add(recipe);
    }

    public int addRecipes(Collection<RecipeHolder<?>> recipes, ServerPlayer player) {
        List<ClientboundRecipeBookAddPacket.Entry> list = new ArrayList<>();

        for (RecipeHolder<?> recipeHolder : recipes) {
            ResourceKey<Recipe<?>> resourceKey = recipeHolder.id();
            if (!this.known.contains(resourceKey) && !recipeHolder.value().isSpecial() && org.bukkit.craftbukkit.event.CraftEventFactory.handlePlayerRecipeListUpdateEvent(player, resourceKey.location())) { // CraftBukkit
                this.add(resourceKey);
                this.addHighlight(resourceKey);
                this.displayResolver
                    .displaysForRecipe(
                        resourceKey, entry -> list.add(new ClientboundRecipeBookAddPacket.Entry(entry, recipeHolder.value().showNotification(), true))
                    );
                CriteriaTriggers.RECIPE_UNLOCKED.trigger(player, recipeHolder);
            }
        }

        if (!list.isEmpty() && player.connection != null) { // SPIGOT-4478 during PlayerLoginEvent
            player.connection.send(new ClientboundRecipeBookAddPacket(list, false));
        }

        return list.size();
    }

    public int removeRecipes(Collection<RecipeHolder<?>> recipes, ServerPlayer player) {
        List<RecipeDisplayId> list = Lists.newArrayList();

        for (RecipeHolder<?> recipeHolder : recipes) {
            ResourceKey<Recipe<?>> resourceKey = recipeHolder.id();
            if (this.known.contains(resourceKey)) {
                this.remove(resourceKey);
                this.displayResolver.displaysForRecipe(resourceKey, entry -> list.add(entry.id()));
            }
        }

        if (!list.isEmpty() && player.connection != null) { // SPIGOT-4478 during PlayerLoginEvent
            player.connection.send(new ClientboundRecipeBookRemovePacket(list));
        }

        return list.size();
    }

    public CompoundTag toNbt() {
        CompoundTag compoundTag = new CompoundTag();
        this.getBookSettings().write(compoundTag);
        ListTag listTag = new ListTag();

        for (ResourceKey<Recipe<?>> resourceKey : this.known) {
            listTag.add(StringTag.valueOf(resourceKey.location().toString()));
        }

        compoundTag.put("recipes", listTag);
        ListTag listTag1 = new ListTag();

        for (ResourceKey<Recipe<?>> resourceKey1 : this.highlight) {
            listTag1.add(StringTag.valueOf(resourceKey1.location().toString()));
        }

        compoundTag.put("toBeDisplayed", listTag1);
        return compoundTag;
    }

    public void fromNbt(CompoundTag tag, Predicate<ResourceKey<Recipe<?>>> isRecognized) {
        this.setBookSettings(RecipeBookSettings.read(tag));
        ListTag list = tag.getList("recipes", 8);
        this.loadRecipes(list, this::add, isRecognized);
        ListTag list1 = tag.getList("toBeDisplayed", 8);
        this.loadRecipes(list1, this::addHighlight, isRecognized);
    }

    private void loadRecipes(ListTag tag, Consumer<ResourceKey<Recipe<?>>> output, Predicate<ResourceKey<Recipe<?>>> isRecognized) {
        for (int i = 0; i < tag.size(); i++) {
            String string = tag.getString(i);

            try {
                ResourceKey<Recipe<?>> resourceKey = ResourceKey.create(Registries.RECIPE, ResourceLocation.parse(string));
                if (!isRecognized.test(resourceKey)) {
                    if (!org.purpurmc.purpur.PurpurConfig.loggerSuppressUnrecognizedRecipeErrors) // Purpur - Logger settings (suppressing pointless logs)
                    LOGGER.error("Tried to load unrecognized recipe: {} removed now.", resourceKey);
                } else {
                    output.accept(resourceKey);
                }
            } catch (ResourceLocationException var7) {
                LOGGER.error("Tried to load improperly formatted recipe: {} removed now.", string);
            }
        }
    }

    public void sendInitialRecipeBook(ServerPlayer player) {
        player.connection.send(new ClientboundRecipeBookSettingsPacket(this.getBookSettings()));
        List<ClientboundRecipeBookAddPacket.Entry> list = new ArrayList<>(this.known.size());

        for (ResourceKey<Recipe<?>> resourceKey : this.known) {
            this.displayResolver
                .displaysForRecipe(resourceKey, entry -> list.add(new ClientboundRecipeBookAddPacket.Entry(entry, false, this.highlight.contains(resourceKey))));
        }

        player.connection.send(new ClientboundRecipeBookAddPacket(list, true));
    }

    public void copyOverData(ServerRecipeBook other) {
        this.known.clear();
        this.highlight.clear();
        this.bookSettings.replaceFrom(other.bookSettings);
        this.known.addAll(other.known);
        this.highlight.addAll(other.highlight);
    }

    @FunctionalInterface
    public interface DisplayResolver {
        void displaysForRecipe(ResourceKey<Recipe<?>> recipe, Consumer<RecipeDisplayEntry> output);
    }
}

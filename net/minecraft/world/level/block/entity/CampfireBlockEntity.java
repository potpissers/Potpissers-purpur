package net.minecraft.world.level.block.entity;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class CampfireBlockEntity extends BlockEntity implements Clearable {
    private static final int BURN_COOL_SPEED = 2;
    private static final int NUM_SLOTS = 4;
    private final NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    public final int[] cookingProgress = new int[4];
    public final int[] cookingTime = new int[4];
    public final boolean[] stopCooking = new boolean[4]; // Paper - Add more Campfire API

    public CampfireBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.CAMPFIRE, pos, blockState);
    }

    public static void cookTick(
        ServerLevel level,
        BlockPos pos,
        BlockState state,
        CampfireBlockEntity campfire,
        RecipeManager.CachedCheck<SingleRecipeInput, CampfireCookingRecipe> check
    ) {
        boolean flag = false;

        for (int i = 0; i < campfire.items.size(); i++) {
            ItemStack itemStack = campfire.items.get(i);
            if (!itemStack.isEmpty()) {
                flag = true;
                if (!campfire.stopCooking[i]) { // Paper - Add more Campfire API
                campfire.cookingProgress[i]++;
                } // Paper - Add more Campfire API
                if (campfire.cookingProgress[i] >= campfire.cookingTime[i]) {
                    SingleRecipeInput singleRecipeInput = new SingleRecipeInput(itemStack);
                    // Paper start - add recipe to cook events
                    final var optionalCookingRecipe = check.getRecipeFor(singleRecipeInput, level);
                    ItemStack itemStack1 = optionalCookingRecipe
                        .map(recipe -> recipe.value().assemble(singleRecipeInput, level.registryAccess()))
                        .orElse(itemStack);
                    // Paper end - add recipe to cook events
                    if (itemStack1.isItemEnabled(level.enabledFeatures())) {
                        // CraftBukkit start - fire BlockCookEvent
                        org.bukkit.craftbukkit.inventory.CraftItemStack source = org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(itemStack);
                        org.bukkit.inventory.ItemStack result = org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(itemStack1);

                        org.bukkit.event.block.BlockCookEvent blockCookEvent = new org.bukkit.event.block.BlockCookEvent(
                            org.bukkit.craftbukkit.block.CraftBlock.at(level, pos),
                            source,
                            result,
                            (org.bukkit.inventory.CookingRecipe<?>) optionalCookingRecipe.map(RecipeHolder::toBukkitRecipe).orElse(null) // Paper -Add recipe to cook events
                        );

                        if (!blockCookEvent.callEvent()) {
                            return;
                        }

                        result = blockCookEvent.getResult();
                        itemStack1 = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(result);
                        // CraftBukkit end
                        // Paper start - Fix item locations dropped from campfires
                        double deviation = 0.05F * RandomSource.GAUSSIAN_SPREAD_FACTOR;
                        while (!itemStack1.isEmpty()) {
                            net.minecraft.world.entity.item.ItemEntity droppedItem = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, itemStack1.split(level.random.nextInt(21) + 10));
                            droppedItem.setDeltaMovement(level.random.triangle(0.0D, deviation), level.random.triangle(0.2D, deviation), level.random.triangle(0.0D, deviation));
                            level.addFreshEntity(droppedItem);
                        }
                        // Paper end - Fix item locations dropped from campfires
                        campfire.items.set(i, ItemStack.EMPTY);
                        level.sendBlockUpdated(pos, state, state, 3);
                        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
                    }
                }
            }
        }

        if (flag) {
            setChanged(level, pos, state);
        }
    }

    public static void cooldownTick(Level level, BlockPos pos, BlockState state, CampfireBlockEntity blockEntity) {
        boolean flag = false;

        for (int i = 0; i < blockEntity.items.size(); i++) {
            if (blockEntity.cookingProgress[i] > 0) {
                flag = true;
                blockEntity.cookingProgress[i] = Mth.clamp(blockEntity.cookingProgress[i] - 2, 0, blockEntity.cookingTime[i]);
            }
        }

        if (flag) {
            setChanged(level, pos, state);
        }
    }

    public static void particleTick(Level level, BlockPos pos, BlockState state, CampfireBlockEntity blockEntity) {
        RandomSource randomSource = level.random;
        if (randomSource.nextFloat() < 0.11F) {
            for (int i = 0; i < randomSource.nextInt(2) + 2; i++) {
                CampfireBlock.makeParticles(level, pos, state.getValue(CampfireBlock.SIGNAL_FIRE), false);
            }
        }

        int i = state.getValue(CampfireBlock.FACING).get2DDataValue();

        for (int i1 = 0; i1 < blockEntity.items.size(); i1++) {
            if (!blockEntity.items.get(i1).isEmpty() && randomSource.nextFloat() < 0.2F) {
                Direction direction = Direction.from2DDataValue(Math.floorMod(i1 + i, 4));
                float f = 0.3125F;
                double d = pos.getX() + 0.5 - direction.getStepX() * 0.3125F + direction.getClockWise().getStepX() * 0.3125F;
                double d1 = pos.getY() + 0.5;
                double d2 = pos.getZ() + 0.5 - direction.getStepZ() * 0.3125F + direction.getClockWise().getStepZ() * 0.3125F;

                for (int i2 = 0; i2 < 4; i2++) {
                    level.addParticle(ParticleTypes.SMOKE, d, d1, d2, 0.0, 5.0E-4, 0.0);
                }
            }
        }
    }

    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items.clear();
        ContainerHelper.loadAllItems(tag, this.items, registries);
        if (tag.contains("CookingTimes", 11)) {
            int[] intArray = tag.getIntArray("CookingTimes");
            System.arraycopy(intArray, 0, this.cookingProgress, 0, Math.min(this.cookingTime.length, intArray.length));
        }

        if (tag.contains("CookingTotalTimes", 11)) {
            int[] intArray = tag.getIntArray("CookingTotalTimes");
            System.arraycopy(intArray, 0, this.cookingTime, 0, Math.min(this.cookingTime.length, intArray.length));
        }

        // Paper start - Add more Campfire API
        if (tag.contains("Paper.StopCooking", org.bukkit.craftbukkit.util.CraftMagicNumbers.NBT.TAG_BYTE_ARRAY)) {
            byte[] abyte = tag.getByteArray("Paper.StopCooking");
            boolean[] cookingState = new boolean[4];
            for (int index = 0; index < abyte.length; index++) {
                cookingState[index] = abyte[index] == 1;
            }
            System.arraycopy(cookingState, 0, this.stopCooking, 0, Math.min(this.stopCooking.length, abyte.length));
        }
        // Paper end - Add more Campfire API
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, true, registries);
        tag.putIntArray("CookingTimes", this.cookingProgress);
        tag.putIntArray("CookingTotalTimes", this.cookingTime);
        // Paper start - Add more Campfire API
        byte[] cookingState = new byte[4];
        for (int index = 0; index < cookingState.length; index++) {
            cookingState[index] = (byte) (this.stopCooking[index] ? 1 : 0);
        }
        tag.putByteArray("Paper.StopCooking", cookingState);
        // Paper end - Add more Campfire API
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag compoundTag = new CompoundTag();
        ContainerHelper.saveAllItems(compoundTag, this.items, true, registries);
        return compoundTag;
    }

    public boolean placeFood(ServerLevel level, @Nullable LivingEntity entity, ItemStack stack) {
        for (int i = 0; i < this.items.size(); i++) {
            ItemStack itemStack = this.items.get(i);
            if (itemStack.isEmpty()) {
                Optional<RecipeHolder<CampfireCookingRecipe>> recipeFor = level.recipeAccess()
                    .getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SingleRecipeInput(stack), level);
                if (recipeFor.isEmpty()) {
                    return false;
                }

                // CraftBukkit start
                org.bukkit.event.block.CampfireStartEvent event = new org.bukkit.event.block.CampfireStartEvent(
                    org.bukkit.craftbukkit.block.CraftBlock.at(this.level,this.worldPosition),
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack),
                    (org.bukkit.inventory.CampfireRecipe) recipeFor.get().toBukkitRecipe()
                );
                this.level.getCraftServer().getPluginManager().callEvent(event);
                this.cookingTime[i] = event.getTotalCookTime(); // i -> event.getTotalCookTime()
                // CraftBukkit end
                this.cookingProgress[i] = 0;
                this.items.set(i, stack.consumeAndReturn(1, entity));
                level.gameEvent(GameEvent.BLOCK_CHANGE, this.getBlockPos(), GameEvent.Context.of(entity, this.getBlockState()));
                this.markUpdated();
                return true;
            }
        }

        return false;
    }

    private void markUpdated() {
        this.setChanged();
        this.getLevel().sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    public void dowse() {
        if (this.level != null) {
            this.markUpdated();
        }
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        componentInput.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(this.getItems());
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Items");
    }
}

package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Spawner;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class SpawnerBlock extends BaseEntityBlock {
    public static final MapCodec<SpawnerBlock> CODEC = simpleCodec(SpawnerBlock::new);

    @Override
    public MapCodec<SpawnerBlock> codec() {
        return CODEC;
    }

    protected SpawnerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(
            blockEntityType, BlockEntityType.MOB_SPAWNER, level.isClientSide ? SpawnerBlockEntity::clientTick : SpawnerBlockEntity::serverTick
        );
    }

    // Purpur start - Silk touch spawners
    @Override
    public void playerDestroy(Level level, net.minecraft.world.entity.player.Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack stack, boolean includeDrops, boolean dropExp) {
        if (level.purpurConfig.silkTouchEnabled && player.getBukkitEntity().hasPermission("purpur.drop.spawners") && isSilkTouch(level, stack)) {
            ItemStack item = new ItemStack(Blocks.SPAWNER.asItem());

            net.minecraft.world.level.SpawnData nextSpawnData = blockEntity instanceof SpawnerBlockEntity spawnerBlock ? spawnerBlock.getSpawner().nextSpawnData : null;
            java.util.Optional<net.minecraft.world.entity.EntityType<?>> type = java.util.Optional.empty();
            if (nextSpawnData != null) {
                type = net.minecraft.world.entity.EntityType.by(nextSpawnData.getEntityToSpawn());
                net.minecraft.world.level.SpawnData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, nextSpawnData).result().ifPresent(tag -> item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY.update(compoundTag -> compoundTag.put("Purpur.SpawnData", tag))));
            }

            if (type.isPresent()) {
                final net.kyori.adventure.text.Component mobName = io.papermc.paper.adventure.PaperAdventure.asAdventure(type.get().getDescription());

                String name = level.purpurConfig.silkTouchSpawnerName;
                if (name != null && !name.isEmpty() && !name.equals("Monster Spawner")) {
                    net.kyori.adventure.text.Component displayName = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(name, net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("mob", mobName));
                    if (name.startsWith("<reset>")) {
                        displayName = displayName.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
                    }
                    item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, io.papermc.paper.adventure.PaperAdventure.asVanilla(displayName));
                }

                List<String> lore = level.purpurConfig.silkTouchSpawnerLore;
                if (lore != null && !lore.isEmpty()) {

                    List<Component> loreComponentList = new java.util.ArrayList<>();
                    for (String line : lore) {
                        net.kyori.adventure.text.Component lineComponent = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(line, net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("mob", mobName));
                        if (line.startsWith("<reset>")) {
                            lineComponent = lineComponent.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
                        }
                        loreComponentList.add(io.papermc.paper.adventure.PaperAdventure.asVanilla(lineComponent));
                    }

                    item.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(loreComponentList, loreComponentList));
                }
                item.set(net.minecraft.core.component.DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
            }
            popResource(level, pos, item);
        }
        super.playerDestroy(level, player, pos, state, blockEntity, stack, includeDrops, dropExp);
    }

    private boolean isSilkTouch(Level level, ItemStack stack) {
        return stack != null && level.purpurConfig.silkTouchTools.contains(stack.getItem()) && net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH, stack) >= level.purpurConfig.minimumSilkTouchSpawnerRequire;
    }
    // Purpur end - Silk touch spawners

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, stack, dropExperience);
        // CraftBukkit start - Delegate to getExpDrop
    }

    @Override
    public int getExpDrop(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
        if (level.purpurConfig.silkTouchEnabled && isSilkTouch(level, stack)) return 0; // Purpur - Silk touch spawners
        if (dropExperience) {
            int i = 15 + level.random.nextInt(15) + level.random.nextInt(15);
            // this.popExperience(level, pos, i);
            return  i;
        }
        return 0;
        // CraftBukkit end
     }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        Spawner.appendHoverText(stack, tooltipComponents, "SpawnData");
    }
}

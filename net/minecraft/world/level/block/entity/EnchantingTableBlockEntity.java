package net.minecraft.world.level.block.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class EnchantingTableBlockEntity extends BlockEntity implements Nameable {
    public int time;
    public float flip;
    public float oFlip;
    public float flipT;
    public float flipA;
    public float open;
    public float oOpen;
    public float rot;
    public float oRot;
    public float tRot;
    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable
    private Component name;
    private int lapis = 0; // Purpur - Enchantment Table Persists Lapis

    public EnchantingTableBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.ENCHANTING_TABLE, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.hasCustomName()) {
            tag.putString("CustomName", Component.Serializer.toJson(this.name, registries));
        }
        tag.putInt("Purpur.Lapis", this.lapis); // Purpur - Enchantment Table Persists Lapis
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("CustomName", 8)) {
            this.name = parseCustomNameSafe(tag.getString("CustomName"), registries);
        }
        this.lapis = tag.getInt("Purpur.Lapis"); // Purpur - Enchantment Table Persists Lapis
    }

    public static void bookAnimationTick(Level level, BlockPos pos, BlockState state, EnchantingTableBlockEntity enchantingTable) {
        enchantingTable.oOpen = enchantingTable.open;
        enchantingTable.oRot = enchantingTable.rot;
        Player nearestPlayer = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3.0, false);
        if (nearestPlayer != null) {
            double d = nearestPlayer.getX() - (pos.getX() + 0.5);
            double d1 = nearestPlayer.getZ() - (pos.getZ() + 0.5);
            enchantingTable.tRot = (float)Mth.atan2(d1, d);
            enchantingTable.open += 0.1F;
            if (enchantingTable.open < 0.5F || RANDOM.nextInt(40) == 0) {
                float f = enchantingTable.flipT;

                do {
                    enchantingTable.flipT = enchantingTable.flipT + (RANDOM.nextInt(4) - RANDOM.nextInt(4));
                } while (f == enchantingTable.flipT);
            }
        } else {
            enchantingTable.tRot += 0.02F;
            enchantingTable.open -= 0.1F;
        }

        while (enchantingTable.rot >= (float) Math.PI) {
            enchantingTable.rot -= (float) (Math.PI * 2);
        }

        while (enchantingTable.rot < (float) -Math.PI) {
            enchantingTable.rot += (float) (Math.PI * 2);
        }

        while (enchantingTable.tRot >= (float) Math.PI) {
            enchantingTable.tRot -= (float) (Math.PI * 2);
        }

        while (enchantingTable.tRot < (float) -Math.PI) {
            enchantingTable.tRot += (float) (Math.PI * 2);
        }

        float f1 = enchantingTable.tRot - enchantingTable.rot;

        while (f1 >= (float) Math.PI) {
            f1 -= (float) (Math.PI * 2);
        }

        while (f1 < (float) -Math.PI) {
            f1 += (float) (Math.PI * 2);
        }

        enchantingTable.rot += f1 * 0.4F;
        enchantingTable.open = Mth.clamp(enchantingTable.open, 0.0F, 1.0F);
        enchantingTable.time++;
        enchantingTable.oFlip = enchantingTable.flip;
        float f2 = (enchantingTable.flipT - enchantingTable.flip) * 0.4F;
        float f3 = 0.2F;
        f2 = Mth.clamp(f2, -0.2F, 0.2F);
        enchantingTable.flipA = enchantingTable.flipA + (f2 - enchantingTable.flipA) * 0.9F;
        enchantingTable.flip = enchantingTable.flip + enchantingTable.flipA;
    }

    @Override
    public Component getName() {
        return (Component)(this.name != null ? this.name : Component.translatable("container.enchant"));
    }

    public void setCustomName(@Nullable Component customName) {
        this.name = customName;
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.name;
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        this.name = componentInput.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, this.name);
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("CustomName");
    }

    // Purpur start - Enchantment Table Persists Lapis
    public int getLapis() {
        return this.lapis;
    }

    public void setLapis(int lapis) {
        this.lapis = lapis;
    }
    // Purpur end - Enchantment Table Persists Lapis
}

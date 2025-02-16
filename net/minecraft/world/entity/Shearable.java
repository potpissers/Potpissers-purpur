package net.minecraft.world.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

public interface Shearable {
    default void shear(ServerLevel level, SoundSource soundSource, ItemStack shears, java.util.List<net.minecraft.world.item.ItemStack> drops) { this.shear(level, soundSource, shears); } // Paper - Add drops to shear events
    void shear(ServerLevel level, SoundSource soundSource, ItemStack shears);

    boolean readyForShearing();

    net.minecraft.world.level.Level level(); // Shearable API - expose default level needed for shearing.

    // Paper start - custom shear drops; ensure all implementing entities override this
    default java.util.List<net.minecraft.world.item.ItemStack> generateDefaultDrops(final ServerLevel serverLevel, final ItemStack shears) {
        return java.util.Collections.emptyList();
    }
    // Paper end - custom shear drops
}

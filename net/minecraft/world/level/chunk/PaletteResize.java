package net.minecraft.world.level.chunk;

interface PaletteResize<T> {
    int onResize(int bits, T objectAdded);
}

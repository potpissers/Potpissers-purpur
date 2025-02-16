package net.minecraft.world.level.material;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class FluidState extends StateHolder<Fluid, FluidState> {
    public static final Codec<FluidState> CODEC = codec(BuiltInRegistries.FLUID.byNameCodec(), Fluid::defaultFluidState).stable();
    public static final int AMOUNT_MAX = 9;
    public static final int AMOUNT_FULL = 8;
    protected final boolean isEmpty; // Paper - Perf: moved from isEmpty()

    public FluidState(Fluid owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<FluidState> propertiesCodec) {
        super(owner, values, propertiesCodec);
        this.isEmpty = owner.isEmpty(); // Paper - Perf: moved from isEmpty()
    }

    public Fluid getType() {
        return this.owner;
    }

    public boolean isSource() {
        return this.getType().isSource(this);
    }

    public boolean isSourceOfType(Fluid fluid) {
        return this.owner == fluid && this.owner.isSource(this);
    }

    public boolean isEmpty() {
        return this.isEmpty; // Paper - Perf: moved into constructor
    }

    public float getHeight(BlockGetter level, BlockPos pos) {
        return this.getType().getHeight(this, level, pos);
    }

    public float getOwnHeight() {
        return this.getType().getOwnHeight(this);
    }

    public int getAmount() {
        return this.getType().getAmount(this);
    }

    public boolean shouldRenderBackwardUpFace(BlockGetter level, BlockPos pos) {
        for (int i = -1; i <= 1; i++) {
            for (int i1 = -1; i1 <= 1; i1++) {
                BlockPos blockPos = pos.offset(i, 0, i1);
                FluidState fluidState = level.getFluidState(blockPos);
                if (!fluidState.getType().isSame(this.getType()) && !level.getBlockState(blockPos).isSolidRender()) {
                    return true;
                }
            }
        }

        return false;
    }

    public void tick(ServerLevel level, BlockPos pos, BlockState state) {
        this.getType().tick(level, pos, state, this);
    }

    public void animateTick(Level level, BlockPos pos, RandomSource random) {
        this.getType().animateTick(level, pos, this, random);
    }

    public boolean isRandomlyTicking() {
        return this.getType().isRandomlyTicking();
    }

    public void randomTick(ServerLevel level, BlockPos pos, RandomSource random) {
        this.getType().randomTick(level, pos, this, random);
    }

    public Vec3 getFlow(BlockGetter level, BlockPos pos) {
        return this.getType().getFlow(level, pos, this);
    }

    public BlockState createLegacyBlock() {
        return this.getType().createLegacyBlock(this);
    }

    @Nullable
    public ParticleOptions getDripParticle() {
        return this.getType().getDripParticle();
    }

    public boolean is(TagKey<Fluid> tag) {
        return this.getType().builtInRegistryHolder().is(tag);
    }

    public boolean is(HolderSet<Fluid> fluids) {
        return fluids.contains(this.getType().builtInRegistryHolder());
    }

    public boolean is(Fluid fluid) {
        return this.getType() == fluid;
    }

    public float getExplosionResistance() {
        return this.getType().getExplosionResistance();
    }

    public boolean canBeReplacedWith(BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
        return this.getType().canBeReplacedWith(this, level, pos, fluid, direction);
    }

    public VoxelShape getShape(BlockGetter level, BlockPos pos) {
        return this.getType().getShape(this, level, pos);
    }

    public Holder<Fluid> holder() {
        return this.owner.builtInRegistryHolder();
    }

    public Stream<TagKey<Fluid>> getTags() {
        return this.owner.builtInRegistryHolder().tags();
    }
}

package net.minecraft.world.level.block.grower;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

public final class TreeGrower {
    private static final Map<String, TreeGrower> GROWERS = new Object2ObjectArrayMap<>();
    public static final Codec<TreeGrower> CODEC = Codec.stringResolver(treeGrower -> treeGrower.name, GROWERS::get);
    public static final TreeGrower OAK = new TreeGrower(
        "oak",
        0.1F,
        Optional.empty(),
        Optional.empty(),
        Optional.of(TreeFeatures.OAK),
        Optional.of(TreeFeatures.FANCY_OAK),
        Optional.of(TreeFeatures.OAK_BEES_005),
        Optional.of(TreeFeatures.FANCY_OAK_BEES_005)
    );
    public static final TreeGrower SPRUCE = new TreeGrower(
        "spruce",
        0.5F,
        Optional.of(TreeFeatures.MEGA_SPRUCE),
        Optional.of(TreeFeatures.MEGA_PINE),
        Optional.of(TreeFeatures.SPRUCE),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    );
    public static final TreeGrower MANGROVE = new TreeGrower(
        "mangrove",
        0.85F,
        Optional.empty(),
        Optional.empty(),
        Optional.of(TreeFeatures.MANGROVE),
        Optional.of(TreeFeatures.TALL_MANGROVE),
        Optional.empty(),
        Optional.empty()
    );
    public static final TreeGrower AZALEA = new TreeGrower("azalea", Optional.empty(), Optional.of(TreeFeatures.AZALEA_TREE), Optional.empty());
    public static final TreeGrower BIRCH = new TreeGrower("birch", Optional.empty(), Optional.of(TreeFeatures.BIRCH), Optional.of(TreeFeatures.BIRCH_BEES_005));
    public static final TreeGrower JUNGLE = new TreeGrower(
        "jungle", Optional.of(TreeFeatures.MEGA_JUNGLE_TREE), Optional.of(TreeFeatures.JUNGLE_TREE_NO_VINE), Optional.empty()
    );
    public static final TreeGrower ACACIA = new TreeGrower("acacia", Optional.empty(), Optional.of(TreeFeatures.ACACIA), Optional.empty());
    public static final TreeGrower CHERRY = new TreeGrower(
        "cherry", Optional.empty(), Optional.of(TreeFeatures.CHERRY), Optional.of(TreeFeatures.CHERRY_BEES_005)
    );
    public static final TreeGrower DARK_OAK = new TreeGrower("dark_oak", Optional.of(TreeFeatures.DARK_OAK), Optional.empty(), Optional.empty());
    public static final TreeGrower PALE_OAK = new TreeGrower("pale_oak", Optional.of(TreeFeatures.PALE_OAK_BONEMEAL), Optional.empty(), Optional.empty());
    private final String name;
    private final float secondaryChance;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryMegaTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> flowers;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryFlowers;

    public TreeGrower(
        String name,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaTree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> flowers
    ) {
        this(name, 0.0F, megaTree, Optional.empty(), tree, Optional.empty(), flowers, Optional.empty());
    }

    public TreeGrower(
        String name,
        float secondaryChance,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaTree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryMegaTree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryTree,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> flowers,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryFlowers
    ) {
        this.name = name;
        this.secondaryChance = secondaryChance;
        this.megaTree = megaTree;
        this.secondaryMegaTree = secondaryMegaTree;
        this.tree = tree;
        this.secondaryTree = secondaryTree;
        this.flowers = flowers;
        this.secondaryFlowers = secondaryFlowers;
        GROWERS.put(name, this);
    }

    @Nullable
    private ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean flowers) {
        if (random.nextFloat() < this.secondaryChance) {
            if (flowers && this.secondaryFlowers.isPresent()) {
                return this.secondaryFlowers.get();
            }

            if (this.secondaryTree.isPresent()) {
                return this.secondaryTree.get();
            }
        }

        return flowers && this.flowers.isPresent() ? this.flowers.get() : this.tree.orElse(null);
    }

    @Nullable
    private ResourceKey<ConfiguredFeature<?, ?>> getConfiguredMegaFeature(RandomSource random) {
        return this.secondaryMegaTree.isPresent() && random.nextFloat() < this.secondaryChance ? this.secondaryMegaTree.get() : this.megaTree.orElse(null);
    }

    public boolean growTree(ServerLevel level, ChunkGenerator chunkGenerator, BlockPos pos, BlockState state, RandomSource random) {
        ResourceKey<ConfiguredFeature<?, ?>> configuredMegaFeature = this.getConfiguredMegaFeature(random);
        if (configuredMegaFeature != null) {
            Holder<ConfiguredFeature<?, ?>> holder = level.registryAccess()
                .lookupOrThrow(Registries.CONFIGURED_FEATURE)
                .get(configuredMegaFeature)
                .orElse(null);
            if (holder != null) {
                this.setTreeType(holder); // CraftBukkit
                for (int i = 0; i >= -1; i--) {
                    for (int i1 = 0; i1 >= -1; i1--) {
                        if (isTwoByTwoSapling(state, level, pos, i, i1)) {
                            ConfiguredFeature<?, ?> configuredFeature = holder.value();
                            BlockState blockState = Blocks.AIR.defaultBlockState();
                            level.setBlock(pos.offset(i, 0, i1), blockState, 4);
                            level.setBlock(pos.offset(i + 1, 0, i1), blockState, 4);
                            level.setBlock(pos.offset(i, 0, i1 + 1), blockState, 4);
                            level.setBlock(pos.offset(i + 1, 0, i1 + 1), blockState, 4);
                            if (configuredFeature.place(level, chunkGenerator, random, pos.offset(i, 0, i1))) {
                                return true;
                            }

                            level.setBlock(pos.offset(i, 0, i1), state, 4);
                            level.setBlock(pos.offset(i + 1, 0, i1), state, 4);
                            level.setBlock(pos.offset(i, 0, i1 + 1), state, 4);
                            level.setBlock(pos.offset(i + 1, 0, i1 + 1), state, 4);
                            return false;
                        }
                    }
                }
            }
        }

        ResourceKey<ConfiguredFeature<?, ?>> configuredFeature1 = this.getConfiguredFeature(random, this.hasFlowers(level, pos));
        if (configuredFeature1 == null) {
            return false;
        } else {
            Holder<ConfiguredFeature<?, ?>> holder1 = level.registryAccess().lookupOrThrow(Registries.CONFIGURED_FEATURE).get(configuredFeature1).orElse(null);
            if (holder1 == null) {
                return false;
            } else {
                this.setTreeType(holder1); // CraftBukkit
                ConfiguredFeature<?, ?> configuredFeature2 = holder1.value();
                BlockState blockState1 = level.getFluidState(pos).createLegacyBlock();
                level.setBlock(pos, blockState1, 4);
                if (configuredFeature2.place(level, chunkGenerator, random, pos)) {
                    if (level.getBlockState(pos) == blockState1) {
                        level.sendBlockUpdated(pos, state, blockState1, 2);
                    }

                    return true;
                } else {
                    level.setBlock(pos, state, 4);
                    return false;
                }
            }
        }
    }

    private static boolean isTwoByTwoSapling(BlockState state, BlockGetter level, BlockPos pos, int xOffset, int yOffset) {
        Block block = state.getBlock();
        return level.getBlockState(pos.offset(xOffset, 0, yOffset)).is(block)
            && level.getBlockState(pos.offset(xOffset + 1, 0, yOffset)).is(block)
            && level.getBlockState(pos.offset(xOffset, 0, yOffset + 1)).is(block)
            && level.getBlockState(pos.offset(xOffset + 1, 0, yOffset + 1)).is(block);
    }

    private boolean hasFlowers(LevelAccessor level, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.MutableBlockPos.betweenClosed(pos.below().north(2).west(2), pos.above().south(2).east(2))) {
            if (level.getBlockState(blockPos).is(BlockTags.FLOWERS)) {
                return true;
            }
        }

        return false;
    }

    // CraftBukkit start
    private void setTreeType(Holder<ConfiguredFeature<?, ?>> holder) {
        ResourceKey<ConfiguredFeature<?, ?>> treeFeature = holder.unwrapKey().get();
        if (treeFeature == TreeFeatures.OAK || treeFeature == TreeFeatures.OAK_BEES_005) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.TREE;
        } else if (treeFeature == TreeFeatures.HUGE_RED_MUSHROOM) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.RED_MUSHROOM;
        } else if (treeFeature == TreeFeatures.HUGE_BROWN_MUSHROOM) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.BROWN_MUSHROOM;
        } else if (treeFeature == TreeFeatures.JUNGLE_TREE) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.COCOA_TREE;
        } else if (treeFeature == TreeFeatures.JUNGLE_TREE_NO_VINE) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.SMALL_JUNGLE;
        } else if (treeFeature == TreeFeatures.PINE) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.TALL_REDWOOD;
        } else if (treeFeature == TreeFeatures.SPRUCE) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.REDWOOD;
        } else if (treeFeature == TreeFeatures.ACACIA) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.ACACIA;
        } else if (treeFeature == TreeFeatures.BIRCH || treeFeature == TreeFeatures.BIRCH_BEES_005) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.BIRCH;
        } else if (treeFeature == TreeFeatures.SUPER_BIRCH_BEES_0002) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.TALL_BIRCH;
        } else if (treeFeature == TreeFeatures.SWAMP_OAK) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.SWAMP;
        } else if (treeFeature == TreeFeatures.FANCY_OAK || treeFeature == TreeFeatures.FANCY_OAK_BEES_005) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.BIG_TREE;
        } else if (treeFeature == TreeFeatures.JUNGLE_BUSH) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.JUNGLE_BUSH;
        } else if (treeFeature == TreeFeatures.DARK_OAK) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.DARK_OAK;
        } else if (treeFeature == TreeFeatures.MEGA_SPRUCE) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.MEGA_REDWOOD;
        } else if (treeFeature == TreeFeatures.MEGA_PINE) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.MEGA_PINE;
        } else if (treeFeature == TreeFeatures.MEGA_JUNGLE_TREE) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.JUNGLE;
        } else if (treeFeature == TreeFeatures.AZALEA_TREE) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.AZALEA;
        } else if (treeFeature == TreeFeatures.MANGROVE) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.MANGROVE;
        } else if (treeFeature == TreeFeatures.TALL_MANGROVE) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.TALL_MANGROVE;
        } else if (treeFeature == TreeFeatures.CHERRY || treeFeature == TreeFeatures.CHERRY_BEES_005) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.CHERRY;
        } else if (treeFeature == TreeFeatures.PALE_OAK || treeFeature == TreeFeatures.PALE_OAK_BONEMEAL) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.PALE_OAK;
        } else if (treeFeature == TreeFeatures.PALE_OAK_CREAKING) {
            net.minecraft.world.level.block.SaplingBlock.treeType = org.bukkit.TreeType.PALE_OAK_CREAKING;
        } else {
            throw new IllegalArgumentException("Unknown tree generator " + treeFeature);
        }
    }
    // CraftBukkit end
}

package net.minecraft.world.level.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public class JigsawBlockEntity extends BlockEntity {
    public static final String TARGET = "target";
    public static final String POOL = "pool";
    public static final String JOINT = "joint";
    public static final String PLACEMENT_PRIORITY = "placement_priority";
    public static final String SELECTION_PRIORITY = "selection_priority";
    public static final String NAME = "name";
    public static final String FINAL_STATE = "final_state";
    private ResourceLocation name = ResourceLocation.withDefaultNamespace("empty");
    private ResourceLocation target = ResourceLocation.withDefaultNamespace("empty");
    private ResourceKey<StructureTemplatePool> pool = ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.withDefaultNamespace("empty"));
    private JigsawBlockEntity.JointType joint = JigsawBlockEntity.JointType.ROLLABLE;
    private String finalState = "minecraft:air";
    private int placementPriority;
    private int selectionPriority;

    public JigsawBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityType.JIGSAW, pos, blockState);
    }

    public ResourceLocation getName() {
        return this.name;
    }

    public ResourceLocation getTarget() {
        return this.target;
    }

    public ResourceKey<StructureTemplatePool> getPool() {
        return this.pool;
    }

    public String getFinalState() {
        return this.finalState;
    }

    public JigsawBlockEntity.JointType getJoint() {
        return this.joint;
    }

    public int getPlacementPriority() {
        return this.placementPriority;
    }

    public int getSelectionPriority() {
        return this.selectionPriority;
    }

    public void setName(ResourceLocation name) {
        this.name = name;
    }

    public void setTarget(ResourceLocation target) {
        this.target = target;
    }

    public void setPool(ResourceKey<StructureTemplatePool> pool) {
        this.pool = pool;
    }

    public void setFinalState(String finalState) {
        this.finalState = finalState;
    }

    public void setJoint(JigsawBlockEntity.JointType joint) {
        this.joint = joint;
    }

    public void setPlacementPriority(int placementPriority) {
        this.placementPriority = placementPriority;
    }

    public void setSelectionPriority(int selectionPriority) {
        this.selectionPriority = selectionPriority;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("name", this.name.toString());
        tag.putString("target", this.target.toString());
        tag.putString("pool", this.pool.location().toString());
        tag.putString("final_state", this.finalState);
        tag.putString("joint", this.joint.getSerializedName());
        tag.putInt("placement_priority", this.placementPriority);
        tag.putInt("selection_priority", this.selectionPriority);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.name = ResourceLocation.parse(tag.getString("name"));
        this.target = ResourceLocation.parse(tag.getString("target"));
        this.pool = ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.parse(tag.getString("pool")));
        this.finalState = tag.getString("final_state");
        this.joint = StructureTemplate.getJointType(tag, this.getBlockState());
        this.placementPriority = tag.getInt("placement_priority");
        this.selectionPriority = tag.getInt("selection_priority");
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public void generate(ServerLevel level, int maxDepth, boolean keepJigsaws) {
        BlockPos blockPos = this.getBlockPos().relative(this.getBlockState().getValue(JigsawBlock.ORIENTATION).front());
        Registry<StructureTemplatePool> registry = level.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
        // Paper start - Replace getHolderOrThrow with a null check
        Holder<StructureTemplatePool> orThrow = registry.get(this.pool).orElse(null);
        if (orThrow == null) {
            return;
        }
        // Paper end - Replace getHolderOrThrow with a null check
        JigsawPlacement.generateJigsaw(level, orThrow, this.target, maxDepth, blockPos, keepJigsaws);
    }

    public static enum JointType implements StringRepresentable {
        ROLLABLE("rollable"),
        ALIGNED("aligned");

        public static final StringRepresentable.EnumCodec<JigsawBlockEntity.JointType> CODEC = StringRepresentable.fromEnum(JigsawBlockEntity.JointType::values);
        private final String name;

        private JointType(final String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public Component getTranslatedName() {
            return Component.translatable("jigsaw_block.joint." + this.name);
        }
    }
}

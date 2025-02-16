package net.minecraft.server.packs.repository;

import com.google.common.annotations.VisibleForTesting;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.BuiltInMetadata;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.validation.DirectoryValidator;

public class ServerPacksSource extends BuiltInPackSource {
    private static final PackMetadataSection VERSION_METADATA_SECTION = new PackMetadataSection(
        Component.translatable("dataPack.vanilla.description"), SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA), Optional.empty()
    );
    private static final FeatureFlagsMetadataSection FEATURE_FLAGS_METADATA_SECTION = new FeatureFlagsMetadataSection(FeatureFlags.DEFAULT_FLAGS);
    private static final BuiltInMetadata BUILT_IN_METADATA = BuiltInMetadata.of(
        PackMetadataSection.TYPE, VERSION_METADATA_SECTION, FeatureFlagsMetadataSection.TYPE, FEATURE_FLAGS_METADATA_SECTION
    );
    private static final PackLocationInfo VANILLA_PACK_INFO = new PackLocationInfo(
        "vanilla", Component.translatable("dataPack.vanilla.name"), PackSource.BUILT_IN, Optional.of(CORE_PACK_INFO)
    );
    private static final PackSelectionConfig VANILLA_SELECTION_CONFIG = new PackSelectionConfig(false, Pack.Position.BOTTOM, false);
    private static final PackSelectionConfig FEATURE_SELECTION_CONFIG = new PackSelectionConfig(false, Pack.Position.TOP, false);
    private static final ResourceLocation PACKS_DIR = ResourceLocation.withDefaultNamespace("datapacks");

    public ServerPacksSource(DirectoryValidator validator) {
        super(PackType.SERVER_DATA, createVanillaPackSource(), PACKS_DIR, validator);
    }

    private static PackLocationInfo createBuiltInPackLocation(String id, Component title) {
        return new PackLocationInfo(id, title, PackSource.FEATURE, Optional.of(KnownPack.vanilla(id)));
    }

    @VisibleForTesting
    public static VanillaPackResources createVanillaPackSource() {
        return new VanillaPackResourcesBuilder()
            .setMetadata(BUILT_IN_METADATA)
            .exposeNamespace("minecraft", ResourceLocation.PAPER_NAMESPACE) // Paper
            .applyDevelopmentConfig()
            .pushJarResources()
            .build(VANILLA_PACK_INFO);
    }

    @Override
    protected Component getPackTitle(String id) {
        return Component.literal(id);
    }

    @Nullable
    @Override
    protected Pack createVanillaPack(PackResources resources) {
        return Pack.readMetaAndCreate(VANILLA_PACK_INFO, fixedResources(resources), PackType.SERVER_DATA, VANILLA_SELECTION_CONFIG);
    }

    @Nullable
    @Override
    protected Pack createBuiltinPack(String id, Pack.ResourcesSupplier resources, Component title) {
        // Paper start - custom built-in pack
        final PackLocationInfo info;
        final PackSelectionConfig packConfig;
        if ("paper".equals(id)) {
            info = new PackLocationInfo(id, title, PackSource.BUILT_IN, Optional.empty());
            packConfig = new PackSelectionConfig(true, Pack.Position.TOP, true);
        } else {
            info = createBuiltInPackLocation(id, title);
            packConfig = FEATURE_SELECTION_CONFIG;
        }
        return Pack.readMetaAndCreate(info, resources, PackType.SERVER_DATA, packConfig);
        // Paper end - custom built-in pack
    }

    public static PackRepository createPackRepository(Path folder, DirectoryValidator validator) {
        return new PackRepository(validator, new ServerPacksSource(validator), new FolderRepositorySource(folder, PackType.SERVER_DATA, PackSource.WORLD, validator)); // Paper - add validator
    }

    public static PackRepository createVanillaTrustedRepository() {
        return new PackRepository(new DirectoryValidator(path -> true), new ServerPacksSource(new DirectoryValidator(path -> true))); // Paper - add validator
    }

    public static PackRepository createPackRepository(LevelStorageSource.LevelStorageAccess level) {
        return createPackRepository(level.getLevelPath(LevelResource.DATAPACK_DIR), level.parent().getWorldDirValidator());
    }
}

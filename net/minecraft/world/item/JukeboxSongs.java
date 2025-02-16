package net.minecraft.world.item;

import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public interface JukeboxSongs {
    ResourceKey<JukeboxSong> THIRTEEN = create("13");
    ResourceKey<JukeboxSong> CAT = create("cat");
    ResourceKey<JukeboxSong> BLOCKS = create("blocks");
    ResourceKey<JukeboxSong> CHIRP = create("chirp");
    ResourceKey<JukeboxSong> FAR = create("far");
    ResourceKey<JukeboxSong> MALL = create("mall");
    ResourceKey<JukeboxSong> MELLOHI = create("mellohi");
    ResourceKey<JukeboxSong> STAL = create("stal");
    ResourceKey<JukeboxSong> STRAD = create("strad");
    ResourceKey<JukeboxSong> WARD = create("ward");
    ResourceKey<JukeboxSong> ELEVEN = create("11");
    ResourceKey<JukeboxSong> WAIT = create("wait");
    ResourceKey<JukeboxSong> PIGSTEP = create("pigstep");
    ResourceKey<JukeboxSong> OTHERSIDE = create("otherside");
    ResourceKey<JukeboxSong> FIVE = create("5");
    ResourceKey<JukeboxSong> RELIC = create("relic");
    ResourceKey<JukeboxSong> PRECIPICE = create("precipice");
    ResourceKey<JukeboxSong> CREATOR = create("creator");
    ResourceKey<JukeboxSong> CREATOR_MUSIC_BOX = create("creator_music_box");

    private static ResourceKey<JukeboxSong> create(String name) {
        return ResourceKey.create(Registries.JUKEBOX_SONG, ResourceLocation.withDefaultNamespace(name));
    }

    private static void register(
        BootstrapContext<JukeboxSong> context, ResourceKey<JukeboxSong> key, Holder.Reference<SoundEvent> soundEvent, int lengthInSeconds, int comparatorOutput
    ) {
        context.register(
            key, new JukeboxSong(soundEvent, Component.translatable(Util.makeDescriptionId("jukebox_song", key.location())), lengthInSeconds, comparatorOutput)
        );
    }

    static void bootstrap(BootstrapContext<JukeboxSong> context) {
        register(context, THIRTEEN, SoundEvents.MUSIC_DISC_13, 178, 1);
        register(context, CAT, SoundEvents.MUSIC_DISC_CAT, 185, 2);
        register(context, BLOCKS, SoundEvents.MUSIC_DISC_BLOCKS, 345, 3);
        register(context, CHIRP, SoundEvents.MUSIC_DISC_CHIRP, 185, 4);
        register(context, FAR, SoundEvents.MUSIC_DISC_FAR, 174, 5);
        register(context, MALL, SoundEvents.MUSIC_DISC_MALL, 197, 6);
        register(context, MELLOHI, SoundEvents.MUSIC_DISC_MELLOHI, 96, 7);
        register(context, STAL, SoundEvents.MUSIC_DISC_STAL, 150, 8);
        register(context, STRAD, SoundEvents.MUSIC_DISC_STRAD, 188, 9);
        register(context, WARD, SoundEvents.MUSIC_DISC_WARD, 251, 10);
        register(context, ELEVEN, SoundEvents.MUSIC_DISC_11, 71, 11);
        register(context, WAIT, SoundEvents.MUSIC_DISC_WAIT, 238, 12);
        register(context, PIGSTEP, SoundEvents.MUSIC_DISC_PIGSTEP, 149, 13);
        register(context, OTHERSIDE, SoundEvents.MUSIC_DISC_OTHERSIDE, 195, 14);
        register(context, FIVE, SoundEvents.MUSIC_DISC_5, 178, 15);
        register(context, RELIC, SoundEvents.MUSIC_DISC_RELIC, 218, 14);
        register(context, PRECIPICE, SoundEvents.MUSIC_DISC_PRECIPICE, 299, 13);
        register(context, CREATOR, SoundEvents.MUSIC_DISC_CREATOR, 176, 12);
        register(context, CREATOR_MUSIC_BOX, SoundEvents.MUSIC_DISC_CREATOR_MUSIC_BOX, 73, 11);
    }
}

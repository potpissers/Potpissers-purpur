package net.minecraft.server.commands.data;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

public interface DataAccessor {
    void setData(CompoundTag other) throws CommandSyntaxException;

    CompoundTag getData() throws CommandSyntaxException;

    Component getModifiedSuccess();

    Component getPrintSuccess(Tag nbt);

    Component getPrintSuccess(NbtPathArgument.NbtPath path, double scale, int i);
}

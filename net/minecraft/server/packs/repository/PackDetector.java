package net.minecraft.server.packs.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;

public abstract class PackDetector<T> {
    private final DirectoryValidator validator;

    protected PackDetector(DirectoryValidator validator) {
        this.validator = validator;
    }

    @Nullable
    public T detectPackResources(Path path, List<ForbiddenSymlinkInfo> forbiddenSymlinkInfos) throws IOException {
        Path path1 = path;

        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException var6) {
            return null;
        }

        if (attributes.isSymbolicLink()) {
            this.validator.validateSymlink(path, forbiddenSymlinkInfos);
            if (!forbiddenSymlinkInfos.isEmpty()) {
                return null;
            }

            path1 = Files.readSymbolicLink(path);
            attributes = Files.readAttributes(path1, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        }

        if (attributes.isDirectory()) {
            this.validator.validateKnownDirectory(path1, forbiddenSymlinkInfos);
            if (!forbiddenSymlinkInfos.isEmpty()) {
                return null;
            } else {
                return !Files.isRegularFile(path1.resolve("pack.mcmeta")) ? null : this.createDirectoryPack(path1);
            }
        } else {
            return attributes.isRegularFile() && path1.getFileName().toString().endsWith(".zip") ? this.createZipPack(path1) : null;
        }
    }

    @Nullable
    protected abstract T createZipPack(Path path) throws IOException;

    @Nullable
    protected abstract T createDirectoryPack(Path path) throws IOException;
}

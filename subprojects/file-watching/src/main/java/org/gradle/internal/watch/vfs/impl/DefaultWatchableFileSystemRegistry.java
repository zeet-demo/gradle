/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.watch.vfs.impl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.file.FileSystemInfo;
import net.rubygrapefruit.platform.file.FileSystems;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.ChildMap;
import org.gradle.internal.snapshot.EmptyChildMap;
import org.gradle.internal.snapshot.SingletonChildMap;
import org.gradle.internal.snapshot.VfsRelativePath;
import org.gradle.internal.watch.WatchingNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

public class DefaultWatchableFileSystemRegistry implements WatchableFileSystemRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWatchableFileSystemRegistry.class);

    private static final ImmutableSet<String> SUPPORTED_FILE_SYSTEM_TYPES = ImmutableSet.of(
        // APFS on macOS
        "apfs",
        // HFS and HFS+ on macOS
        "hfs",
        "ext3",
        "ext4",
        "btrfs",
        // NTFS on macOS
        "ntfs",
        // NTFS on Windows
        "NTFS",
        // FAT32 on macOS
        "msdos",
        // FAT32 on Windows
        "FAT32",
        // exFAT on macOS
        "exfat",
        // exFAT on Windows
        "exFAT",
        // VirtualBox FS
        "vboxsf"
    );

    private final FileSystemRoots fileSystemRoots;

    public static WatchableFileSystemRegistry create() {
        return new DefaultWatchableFileSystemRegistry(Native.get(FileSystems.class).getFileSystems());
    }

    @VisibleForTesting
    DefaultWatchableFileSystemRegistry(List<FileSystemInfo> fileSystems) {
        FileSystemRoots fileSystemRoots = new FileSystemRoots(null, true, CaseSensitivity.CASE_SENSITIVE, EmptyChildMap.getInstance());
        for (FileSystemInfo fileSystem : fileSystems) {
            String absolutePath = fileSystem.getMountPoint().getAbsolutePath();
            LOGGER.info("Detected {} {}: {} from {} (remote: {}, case-sensitive: {}, case-preserving: {})",
                isSupported(fileSystem) ? "supported" : "unsupported",
                fileSystem.getFileSystemType(),
                absolutePath,
                fileSystem.getDeviceName(),
                fileSystem.isRemote(),
                fileSystem.isCaseSensitive(),
                fileSystem.isCasePreserving());
            fileSystemRoots = fileSystemRoots.recordFileSystemInfo(VfsRelativePath.of(absolutePath), fileSystem);
        }
        this.fileSystemRoots = fileSystemRoots;
    }

    @Override
    public void ensureWatchingSupported(File path) {
        boolean supportsWatching = fileSystemRoots.supportsWatching(VfsRelativePath.of(path.getAbsolutePath()));
        if (!supportsWatching) {
            throw new WatchingNotSupportedException(String.format("Cannot watch file hierarchy at '%s' because file system is unknown", path.getAbsolutePath()));
        }
    }

    private static class FileSystemRoots {
        private final ChildMap<FileSystemRoots> children;
        private final FileSystemInfo fileSystemInfo;
        private final boolean supportsWatching;
        private final CaseSensitivity caseSensitivity;

        public FileSystemRoots(@Nullable FileSystemInfo fileSystemInfo, boolean supportsWatching, CaseSensitivity caseSensitivity, ChildMap<FileSystemRoots> children) {
            this.fileSystemInfo = fileSystemInfo;
            this.children = children;
            this.supportsWatching = supportsWatching;
            this.caseSensitivity = caseSensitivity;
        }

        public FileSystemRoots recordFileSystemInfo(VfsRelativePath relativePath, FileSystemInfo fileSystemInfo) {
            if (relativePath.length() == 0) {
                return new FileSystemRoots(fileSystemInfo, isSupported(fileSystemInfo), getCaseSensitivity(fileSystemInfo), children);
            }
            ChildMap<FileSystemRoots> newChildren = children.store(relativePath, caseSensitivity, new ChildMap.StoreHandler<FileSystemRoots>() {
                @Override
                public FileSystemRoots handleAsDescendantOfChild(VfsRelativePath pathInChild, FileSystemRoots child) {
                    return child.recordFileSystemInfo(pathInChild, fileSystemInfo);
                }

                @Override
                public FileSystemRoots handleAsAncestorOfChild(String childPath, FileSystemRoots child) {
                    CaseSensitivity caseSensitivity = getCaseSensitivity(fileSystemInfo);
                    return new FileSystemRoots(fileSystemInfo, isSupported(fileSystemInfo), caseSensitivity, new SingletonChildMap<>(VfsRelativePath.of(childPath).suffixStartingFrom(relativePath.length() + 1).getAsString(), child));
                }

                @Override
                public FileSystemRoots mergeWithExisting(FileSystemRoots child) {
                    boolean supportsWatching = isSupported(fileSystemInfo) && child.supportsWatching;
                    return new FileSystemRoots(fileSystemInfo, supportsWatching, getCaseSensitivity(fileSystemInfo), child.children);
                }

                @Override
                public FileSystemRoots createChild() {
                    return new FileSystemRoots(fileSystemInfo, isSupported(fileSystemInfo), getCaseSensitivity(fileSystemInfo), EmptyChildMap.getInstance());
                }

                @Override
                public FileSystemRoots createNodeFromChildren(ChildMap<FileSystemRoots> children) {
                    boolean supportsWatching = children.values().stream().anyMatch(it -> it.supportsWatching);
                    return new FileSystemRoots(null, supportsWatching, caseSensitivity, children);
                }
            });
            boolean supportsWatching = this.supportsWatching && newChildren.values().stream().allMatch(it -> it.supportsWatching);
            return new FileSystemRoots(this.fileSystemInfo, supportsWatching, caseSensitivity, newChildren);
        }

        public boolean supportsWatching(VfsRelativePath targetPath) {
            if (targetPath.length() == 0) {
                return supportsWatching;
            }
            return children.withNode(targetPath, caseSensitivity, new ChildMap.NodeHandler<FileSystemRoots, Boolean>() {
                @Override
                public Boolean handleAsDescendantOfChild(VfsRelativePath pathInChild, FileSystemRoots child) {
                    return child.supportsWatching(pathInChild);
                }

                @Override
                public Boolean handleAsAncestorOfChild(String childPath, FileSystemRoots child) {
                    return child.supportsWatching;
                }

                @Override
                public Boolean handleExactMatchWithChild(FileSystemRoots child) {
                    return child.supportsWatching;
                }

                @Override
                public Boolean handleUnrelatedToAnyChild() {
                    return supportsWatching;
                }
            });
        }

        private static CaseSensitivity getCaseSensitivity(FileSystemInfo fileSystemInfo) {
            return fileSystemInfo.isCaseSensitive() ? CaseSensitivity.CASE_SENSITIVE : CaseSensitivity.CASE_INSENSITIVE;
        }
    }

    private static boolean isSupported(FileSystemInfo fileSystem) {
        // We don't support network file systems
        if (fileSystem.isRemote()) {
            return false;
        }
        return SUPPORTED_FILE_SYSTEM_TYPES.contains(fileSystem.getFileSystemType());
    }

}

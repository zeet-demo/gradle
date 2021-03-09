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
import org.gradle.internal.watch.WatchingNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Comparator;
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

    private final ImmutableList<FileSystemSupport> fileSystemRoots;

    public static WatchableFileSystemRegistry create() {
        return new DefaultWatchableFileSystemRegistry(Native.get(FileSystems.class).getFileSystems());
    }

    @VisibleForTesting
    DefaultWatchableFileSystemRegistry(List<FileSystemInfo> fileSystems) {
        ImmutableList.Builder<FileSystemSupport> builder = ImmutableList.<FileSystemSupport>builder();
        fileSystems.stream()
            // Sort by longest path first so we always match most the specific location in case locations are nested
            .sorted(Comparator.comparingInt(fileSystem -> -fileSystem.getMountPoint().getAbsolutePath().length()))
            .map(FileSystemSupport::new)
            .forEach(support -> {
                LOGGER.info("Detected {} {}: {} from {} (remote: {}, case-sensitive: {}, case-preserving: {})",
                    support.isSupported() ? "supported" : "unsupported",
                    support.getFileSystem().getFileSystemType(),
                    support.getPrefix(),
                    support.getFileSystem().getDeviceName(),
                    support.getFileSystem().isRemote(),
                    support.getFileSystem().isCaseSensitive(),
                    support.getFileSystem().isCasePreserving());
                builder.add(support);
            });
        this.fileSystemRoots = builder.build();
    }

    @Override
    public void ensureWatchingSupported(File path) {
        String prefix = toAbsolutePathPrefix(path);
        for (FileSystemSupport support : fileSystemRoots) {
            if (support.isSupported(prefix)) {
                return;
            }
        }
        throw new WatchingNotSupportedException(String.format("Cannot watch file hierarchy at '%s' because file system is unknown", prefix));
    }

    private static String toAbsolutePathPrefix(File path) {
        String absolutePath = path.getAbsolutePath();
        return absolutePath.equals(File.separator)
            ? absolutePath
            : absolutePath + File.separatorChar;
    }

    private static class FileSystemSupport {
        private final FileSystemInfo fileSystem;
        private final String prefix;
        private final boolean supported;

        public FileSystemSupport(FileSystemInfo fileSystem) {
            this.fileSystem = fileSystem;
            this.prefix = toAbsolutePathPrefix(fileSystem.getMountPoint());
            this.supported = isSupported(fileSystem);
        }

        private static boolean isSupported(FileSystemInfo fileSystem) {
            // We don't support network file systems
            if (fileSystem.isRemote()) {
                return false;
            }
            return SUPPORTED_FILE_SYSTEM_TYPES.contains(fileSystem.getFileSystemType());
        }

        public FileSystemInfo getFileSystem() {
            return fileSystem;
        }

        public String getPrefix() {
            return prefix;
        }

        public boolean isSupported() {
            return supported;
        }

        public boolean isSupported(String prefix) {
            if (!prefix.startsWith(this.prefix)) {
                return false;
            }

            if (!supported) {
                throw new WatchingNotSupportedException(String.format("Cannot watch file hierarchy at '%s' because %s%s file system mounted on '%s' is not supported",
                    prefix,
                    fileSystem.isRemote() ? "remote " : "",
                    fileSystem.getFileSystemType(),
                    fileSystem.getMountPoint().getAbsolutePath()));
            }
            return true;
        }
    }
}

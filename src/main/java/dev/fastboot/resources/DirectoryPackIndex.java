package dev.fastboot.resources;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * Lazy per-namespace index of a directory-backed Minecraft resource pack.
 *
 * Vanilla walks the requested resource directory for every listing call. This
 * index walks a namespace tree once on its first listing, then answers later
 * directory queries with binary searches. It is deliberately lazy: cheap
 * namespace discovery remains vanilla, and unrelated namespaces are never
 * scanned just because a pack is opened.
 *
 * Symbolic links are not followed during file enumeration, matching the
 * default non-following behavior of Files.find / Files.walkFileTree.
 */
public final class DirectoryPackIndex {
    private final Path root;
    private final ConcurrentMap<NamespaceKey, IndexResult> namespaceIndexes = new ConcurrentHashMap<>();

    public DirectoryPackIndex(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /**
     * Lists resources for one namespace. Returns false if indexing failed, in
     * which case the caller must let vanilla perform the listing.
     */
    public boolean forEachResource(
            String packTypeDirectory,
            String namespace,
            String directory,
            BiConsumer<String, Path> consumer
    ) {
        NamespaceKey key = new NamespaceKey(packTypeDirectory, namespace);
        IndexResult result = namespaceIndexes.computeIfAbsent(key, this::buildNamespaceIndex);
        if (!result.available()) {
            return false;
        }

        String pathPrefix = normalizeDirectoryPrefix(directory);
        List<String> files = result.files();
        int start = lowerBound(files, pathPrefix);
        for (int i = start; i < files.size(); i++) {
            String resourcePath = files.get(i);
            if (!resourcePath.startsWith(pathPrefix)) {
                break;
            }
            Path file = root.resolve(packTypeDirectory).resolve(namespace).resolve(resourcePath);
            consumer.accept(resourcePath, file);
        }
        return true;
    }

    private IndexResult buildNamespaceIndex(NamespaceKey key) {
        Path namespaceRoot = root.resolve(key.packTypeDirectory()).resolve(key.namespace());
        List<String> files = new ArrayList<>();
        try {
            Files.walkFileTree(namespaceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile()) {
                        files.add(namespaceRoot.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/"));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            files.sort(Comparator.naturalOrder());
            return new IndexResult(true, List.copyOf(files));
        } catch (IOException | RuntimeException exception) {
            // Store the failure so repeated calls go straight to vanilla rather
            // than repeatedly attempting to scan an inaccessible namespace.
            return new IndexResult(false, List.of());
        }
    }

    private static String normalizeDirectoryPrefix(String directory) {
        if (directory == null || directory.isEmpty()) {
            return "";
        }
        return directory.endsWith("/") ? directory : directory + "/";
    }

    private static int lowerBound(List<String> files, String pathPrefix) {
        int low = 0;
        int high = files.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (files.get(middle).compareTo(pathPrefix) < 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private record NamespaceKey(String packTypeDirectory, String namespace) {
    }

    private record IndexResult(boolean available, List<String> files) {
    }
}

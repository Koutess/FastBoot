package dev.fastboot.resources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * One-pass index of ZIP-backed Minecraft resource packs.
 *
 * Vanilla's FilePackResources.listResources scans the ZIP entry table for each
 * namespace/path query. This index pays for one enumeration per pack instance,
 * then answers subsequent queries with a binary search over sorted paths.
 *
 * This class deliberately contains no Minecraft types so the same tested index
 * can be used on both sides of the 1.21.11 Mojang-mapping rename.
 */
public final class ZipPackIndex {
    private final Map<String, Map<String, List<IndexedEntry>>> entriesByTypeAndNamespace;
    private final Map<String, Set<String>> namespacesByType;

    public ZipPackIndex(ZipFile zipFile, String overlayPrefix, Collection<String> packTypeDirectories) {
        String normalizedOverlay = normalizeOverlayPrefix(overlayPrefix);
        String prefix = normalizedOverlay.isEmpty() ? "" : normalizedOverlay + "/";

        Set<String> packTypes = new HashSet<>();
        for (String directory : packTypeDirectories) {
            if (directory != null && !directory.isEmpty()) {
                packTypes.add(directory);
            }
        }

        Map<String, Map<String, List<IndexedEntry>>> mutableEntries = new HashMap<>();
        Map<String, Set<String>> mutableNamespaces = new HashMap<>();
        for (String directory : packTypes) {
            mutableEntries.put(directory, new HashMap<>());
            mutableNamespaces.put(directory, new HashSet<>());
        }

        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        int ordinal = 0;
        while (zipEntries.hasMoreElements()) {
            ZipEntry entry = zipEntries.nextElement();
            int entryOrdinal = ordinal++;
            String archivePath = entry.getName();
            if (!archivePath.startsWith(prefix)) {
                continue;
            }

            String remainder = archivePath.substring(prefix.length());
            int typeEnd = remainder.indexOf('/');
            if (typeEnd <= 0) {
                continue;
            }

            String packType = remainder.substring(0, typeEnd);
            if (!packTypes.contains(packType)) {
                continue;
            }

            String typeRemainder = remainder.substring(typeEnd + 1);
            int namespaceEnd = typeRemainder.indexOf('/');
            String namespace = namespaceEnd == -1
                    ? typeRemainder
                    : typeRemainder.substring(0, namespaceEnd);
            if (!isValidNamespace(namespace)) {
                continue;
            }

            // Vanilla extracts a namespace even when an archive entry is
            // exactly "assets/<namespace>" without a following slash.
            mutableNamespaces.get(packType).add(namespace);
            String resourcePath = namespaceEnd == -1
                    ? ""
                    : typeRemainder.substring(namespaceEnd + 1);
            if (!entry.isDirectory() && !resourcePath.isEmpty()) {
                mutableEntries.get(packType)
                        .computeIfAbsent(namespace, ignored -> new ArrayList<>())
                        .add(new IndexedEntry(resourcePath, entry, entryOrdinal));
            }
        }

        Map<String, Map<String, List<IndexedEntry>>> frozenEntries = new HashMap<>();
        for (Map.Entry<String, Map<String, List<IndexedEntry>>> typeEntry : mutableEntries.entrySet()) {
            Map<String, List<IndexedEntry>> namespaces = new HashMap<>();
            for (Map.Entry<String, List<IndexedEntry>> namespaceEntry : typeEntry.getValue().entrySet()) {
                List<IndexedEntry> sorted = namespaceEntry.getValue();
                sorted.sort(Comparator.comparing(IndexedEntry::resourcePath));
                namespaces.put(namespaceEntry.getKey(), List.copyOf(sorted));
            }
            frozenEntries.put(typeEntry.getKey(), Collections.unmodifiableMap(namespaces));
        }

        Map<String, Set<String>> frozenNamespaces = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : mutableNamespaces.entrySet()) {
            frozenNamespaces.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }

        this.entriesByTypeAndNamespace = Collections.unmodifiableMap(frozenEntries);
        this.namespacesByType = Collections.unmodifiableMap(frozenNamespaces);
    }

    public Set<String> getNamespaces(String packTypeDirectory) {
        return namespacesByType.getOrDefault(packTypeDirectory, Set.of());
    }

    /**
     * Emits all files beneath {@code directory} for the requested namespace.
     * A path-sorted index narrows the query to matching entries. Results are
     * then emitted in ZIP central-directory order to preserve vanilla behavior
     * for packs containing unusual or duplicate resource names.
     */
    public void forEachResource(
            String packTypeDirectory,
            String namespace,
            String directory,
            BiConsumer<String, ZipEntry> consumer
    ) {
        Map<String, List<IndexedEntry>> namespaces = entriesByTypeAndNamespace.get(packTypeDirectory);
        if (namespaces == null) {
            return;
        }
        List<IndexedEntry> entries = namespaces.get(namespace);
        if (entries == null || entries.isEmpty()) {
            return;
        }

        // FilePackResources appends exactly one slash to the caller's
        // directory argument. Preserve that behavior, including an empty
        // argument or a caller-supplied trailing slash.
        String pathPrefix = String.valueOf(directory) + "/";

        int start = lowerBound(entries, pathPrefix);
        List<IndexedEntry> matches = new ArrayList<>();
        for (int i = start; i < entries.size(); i++) {
            IndexedEntry indexed = entries.get(i);
            if (!indexed.resourcePath().startsWith(pathPrefix)) {
                break;
            }
            matches.add(indexed);
        }

        // FilePackResources iterates ZIP entries in central-directory order.
        // Keep that order so duplicate paths retain vanilla's overwrite order.
        matches.sort(Comparator.comparingInt(IndexedEntry::ordinal));
        for (IndexedEntry indexed : matches) {
            consumer.accept(indexed.resourcePath(), indexed.zipEntry());
        }
    }

    private static String normalizeOverlayPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }

        int start = 0;
        int end = prefix.length();
        while (start < end && prefix.charAt(start) == '/') {
            start++;
        }
        while (end > start && prefix.charAt(end - 1) == '/') {
            end--;
        }
        return prefix.substring(start, end);
    }

    private static int lowerBound(List<IndexedEntry> entries, String pathPrefix) {
        int low = 0;
        int high = entries.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (entries.get(middle).resourcePath().compareTo(pathPrefix) < 0) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private static boolean isValidNamespace(String namespace) {
        if (namespace.isEmpty()) {
            return false;
        }
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private record IndexedEntry(String resourcePath, ZipEntry zipEntry, int ordinal) {
    }
}

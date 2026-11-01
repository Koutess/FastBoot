import dev.fastboot.resources.ZipPackIndex;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Standalone microbenchmark for repeated ZIP resource-listing scans. */
public final class ZipPackIndexBenchmark {
    private ZipPackIndexBenchmark() {
    }

    public static void main(String[] args) throws Exception {
        final int namespaces = 60;
        final int filesPerNamespace = 1_000;
        Path archive = Files.createTempFile("fastboot-60k", ".zip");

        try {
            createArchive(archive, namespaces, filesPerNamespace);
            // Warm the OS file cache, then collect paired timings and report medians.
            benchmarkVanillaStyleScans(archive, namespaces);
            benchmarkIndexedQueries(archive, namespaces);
            long[] vanillaTrials = new long[7];
            long[] indexedTrials = new long[7];
            for (int trial = 0; trial < vanillaTrials.length; trial++) {
                if ((trial & 1) == 0) {
                    vanillaTrials[trial] = benchmarkVanillaStyleScans(archive, namespaces);
                    indexedTrials[trial] = benchmarkIndexedQueries(archive, namespaces);
                } else {
                    indexedTrials[trial] = benchmarkIndexedQueries(archive, namespaces);
                    vanillaTrials[trial] = benchmarkVanillaStyleScans(archive, namespaces);
                }
            }

            long vanillaMedian = median(vanillaTrials);
            long indexedMedian = median(indexedTrials);
            System.out.printf("Synthetic ZIP: %,d entries; %d namespace queries; 7 measured trials%n",
                    namespaces * filesPerNamespace, namespaces);
            System.out.printf("Repeated full ZIP scans (median): %.1f ms%n", vanillaMedian / 1_000_000.0);
            System.out.printf("Index construction + indexed queries (median): %.1f ms%n", indexedMedian / 1_000_000.0);
            System.out.printf("Synthetic listing speedup: %.2fx%n", (double) vanillaMedian / indexedMedian);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    private static void createArchive(Path archive, int namespaces, int filesPerNamespace) throws Exception {
        byte[] content = {1, 2, 3, 4};
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (int namespaceIndex = 0; namespaceIndex < namespaces; namespaceIndex++) {
                String namespace = namespaceName(namespaceIndex);
                for (int fileIndex = 0; fileIndex < filesPerNamespace; fileIndex++) {
                    String path = "assets/" + namespace + "/models/block/model" + fileIndex + ".json";
                    output.putNextEntry(new ZipEntry(path));
                    output.write(content);
                    output.closeEntry();
                }
            }
        }
    }

    private static long benchmarkVanillaStyleScans(Path archive, int namespaces) throws Exception {
        long count = 0;
        long start = System.nanoTime();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (int namespaceIndex = 0; namespaceIndex < namespaces; namespaceIndex++) {
                String prefix = "assets/" + namespaceName(namespaceIndex) + "/models/block/";
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    if (entries.nextElement().getName().startsWith(prefix)) {
                        count++;
                    }
                }
            }
        }
        long elapsed = System.nanoTime() - start;
        if (count != 60_000) {
            throw new AssertionError("Unexpected vanilla-style result count: " + count);
        }
        return elapsed;
    }

    private static long benchmarkIndexedQueries(Path archive, int namespaces) throws Exception {
        long count = 0;
        long start = System.nanoTime();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipPackIndex index = new ZipPackIndex(zip, "", List.of("assets", "data"));
            for (int namespaceIndex = 0; namespaceIndex < namespaces; namespaceIndex++) {
                final long[] namespaceCount = {0};
                index.forEachResource("assets", namespaceName(namespaceIndex), "models/block",
                        (path, entry) -> namespaceCount[0]++);
                count += namespaceCount[0];
            }
        }
        long elapsed = System.nanoTime() - start;
        if (count != 60_000) {
            throw new AssertionError("Unexpected indexed result count: " + count);
        }
        return elapsed;
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static String namespaceName(int index) {
        return "mod" + (index < 10 ? "0" : "") + index;
    }
}

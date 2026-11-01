package dev.fastboot.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipPackIndexTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void indexesNamespacesAndListsOnlyResourcesUnderRequestedDirectory() throws Exception {
        Path archive = createArchive("resources.zip",
                "assets/",
                "assets/minecraft/",
                "assets/minecraft/models/block/cube.json",
                "assets/minecraft/models/item/test.json",
                "assets/minecraft/models2/should-not-match.json",
                "assets/demo/",
                "assets/demo/textures/gui/icon.png",
                "assets/UpperCase/ignored.json",
                "assets/bare_namespace",
                "data/minecraft/recipes/test.json",
                "not-a-resource/file.txt");

        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            ZipPackIndex index = new ZipPackIndex(zipFile, "", List.of("assets", "data"));

            assertEquals(Set.of("minecraft", "demo", "bare_namespace"), index.getNamespaces("assets"));
            assertEquals(Set.of("minecraft"), index.getNamespaces("data"));
            assertTrue(index.getNamespaces("unknown").isEmpty());

            List<String> modelPaths = new ArrayList<>();
            index.forEachResource("assets", "minecraft", "models", (path, entry) -> modelPaths.add(path));
            assertEquals(List.of("models/block/cube.json", "models/item/test.json"), modelPaths);

            List<String> itemPaths = new ArrayList<>();
            index.forEachResource("assets", "minecraft", "models/item", (path, entry) -> itemPaths.add(path));
            assertEquals(List.of("models/item/test.json"), itemPaths);

            List<String> rootPaths = new ArrayList<>();
            index.forEachResource("assets", "minecraft", "", (path, entry) -> rootPaths.add(path));
            // FilePackResources appends directory + "/"; for an empty
            // directory this is a double slash after the namespace root.
            assertTrue(rootPaths.isEmpty());

            List<String> dataPaths = new ArrayList<>();
            index.forEachResource("data", "minecraft", "recipes", (path, entry) -> dataPaths.add(path));
            assertEquals(List.of("recipes/test.json"), dataPaths);
        }
    }

    @Test
    void respectsOverlayPrefixEvenWhenItHasLeadingOrTrailingSlashes() throws Exception {
        Path archive = createArchive("overlay.zip",
                "overlays/test/assets/example/textures/gui/icon.png",
                "assets/example/textures/gui/wrong-location.png",
                "overlays/test/data/example/loot_tables/test.json");

        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            ZipPackIndex index = new ZipPackIndex(zipFile, "/overlays/test/", List.of("assets", "data"));

            assertEquals(Set.of("example"), index.getNamespaces("assets"));
            assertEquals(Set.of("example"), index.getNamespaces("data"));

            List<String> paths = new ArrayList<>();
            index.forEachResource("assets", "example", "textures/gui", (path, entry) -> paths.add(path));
            assertEquals(List.of("textures/gui/icon.png"), paths);
        }
    }

    @Test
    void preservesZipEntryOrderForMatchingResources() throws Exception {
        Path archive = createArchive("entry-order.zip",
                "assets/example/textures/z-last.png",
                "assets/example/textures/a-first.png",
                "assets/example/textures/m-middle.png");

        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            ZipPackIndex index = new ZipPackIndex(zipFile, "", List.of("assets", "data"));
            List<String> paths = new ArrayList<>();
            index.forEachResource("assets", "example", "textures", (path, entry) -> paths.add(path));

            assertEquals(List.of(
                    "textures/z-last.png",
                    "textures/a-first.png",
                    "textures/m-middle.png"
            ), paths);
        }
    }

    @Test
    void returnsEmptyResultsForMissingNamespacesAndDirectories() throws Exception {
        Path archive = createArchive("empty-queries.zip",
                "assets/example/textures/gui/icon.png");

        try (ZipFile zipFile = new ZipFile(archive.toFile())) {
            ZipPackIndex index = new ZipPackIndex(zipFile, "", List.of("assets", "data"));
            List<String> paths = new ArrayList<>();
            index.forEachResource("assets", "missing", "", (path, entry) -> paths.add(path));
            index.forEachResource("assets", "example", "models", (path, entry) -> paths.add(path));
            assertTrue(paths.isEmpty());
        }
    }

    private Path createArchive(String fileName, String... entries) throws IOException {
        Path archive = temporaryDirectory.resolve(fileName);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String entryName : entries) {
                output.putNextEntry(new ZipEntry(entryName));
                if (!entryName.endsWith("/")) {
                    output.write(("content:" + entryName).getBytes(StandardCharsets.UTF_8));
                }
                output.closeEntry();
            }
        }
        return archive;
    }
}

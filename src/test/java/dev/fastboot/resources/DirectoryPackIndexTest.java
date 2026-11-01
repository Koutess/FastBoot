package dev.fastboot.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectoryPackIndexTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void lazilyIndexesOnlyRequestedNamespaceAndFiltersDirectoryBoundary() throws Exception {
        write("assets/demo/models/block/a.json");
        write("assets/demo/models/item/b.json");
        write("assets/demo/models2/not-a-match.json");
        write("assets/other/textures/unused.png");
        write("data/demo/recipes/test.json");

        DirectoryPackIndex index = new DirectoryPackIndex(temporaryDirectory);

        List<String> modelPaths = new ArrayList<>();
        assertTrue(index.forEachResource("assets", "demo", "models", (path, file) -> modelPaths.add(path)));
        assertEquals(List.of("models/block/a.json", "models/item/b.json"), modelPaths);

        List<String> itemPaths = new ArrayList<>();
        assertTrue(index.forEachResource("assets", "demo", "models/item", (path, file) -> itemPaths.add(path)));
        assertEquals(List.of("models/item/b.json"), itemPaths);

        List<String> dataPaths = new ArrayList<>();
        assertTrue(index.forEachResource("data", "demo", "recipes", (path, file) -> dataPaths.add(path)));
        assertEquals(List.of("recipes/test.json"), dataPaths);
    }

    @Test
    void reportsMissingNamespaceToAllowVanillaFallback() {
        DirectoryPackIndex index = new DirectoryPackIndex(temporaryDirectory);
        assertFalse(index.forEachResource("assets", "missing", "textures", (path, file) -> {
            throw new AssertionError("No resources should be emitted for a missing namespace");
        }));
    }

    @Test
    void emptyDirectoryQueryListsEveryFileInNamespace() throws Exception {
        write("assets/demo/textures/icon.png");
        write("assets/demo/models/test.json");
        DirectoryPackIndex index = new DirectoryPackIndex(temporaryDirectory);

        List<String> paths = new ArrayList<>();
        assertTrue(index.forEachResource("assets", "demo", "", (path, file) -> paths.add(path)));
        assertEquals(List.of("models/test.json", "textures/icon.png"), paths);
    }

    private void write(String relativePath) throws Exception {
        Path file = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "content", StandardCharsets.UTF_8);
    }
}

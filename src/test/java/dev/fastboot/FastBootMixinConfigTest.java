package dev.fastboot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastBootMixinConfigTest {
    @Test
    void onlyFunctionalMixinsAreEnabledAndMixinConfigurationFailsOpen() throws Exception {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("fastboot.mixins.json")) {
            assertNotNull(stream, "Mixin configuration must be packaged");
            JsonObject config = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            assertFalse(config.get("required").getAsBoolean(),
                    "A missing optional hook must not prevent Minecraft from starting");
            assertEquals(0, config.getAsJsonObject("injectors").get("defaultRequire").getAsInt());
            assertEquals("fastboot.refmap.json", config.get("refmap").getAsString());

            JsonArray clientMixins = config.getAsJsonArray("client");
            Set<String> mixins = new HashSet<>();
            clientMixins.forEach(entry -> mixins.add(entry.getAsString()));

            assertEquals(Set.of("FilePackResourcesMixin", "PathPackResourcesMixin", "TitleScreenFadeMixin"), mixins);
            assertFalse(mixins.contains("ReloadTimerMixin"));
            assertFalse(mixins.contains("DataFixersMixin"));
            assertFalse(mixins.contains("TitleScreenMixin"));
            assertEquals(3, mixins.size(), "Only the two resource-pack indexes and title-screen fade mixins should be enabled");
        }

        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("fastboot.accesswidener")) {
            assertNotNull(stream, "Access widener must be packaged");
            String accessWidener = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(accessWidener.startsWith("accessWidener v2 named"));
            assertTrue(accessWidener.contains("FilePackResources$SharedZipFileAccess"));
            assertTrue(accessWidener.contains("getOrCreateZipFile"));
        }
    }
}

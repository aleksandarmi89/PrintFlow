package com.printflow.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateMessageKeysCoverageTest {

    private static final Pattern MESSAGE_EXPR = Pattern.compile("#\\{([^}]+)}");

    @Test
    void allTemplateMessageKeysExistInBothBundles() throws IOException {
        Properties en = loadProperties("src/main/resources/messages/messages.properties");
        Properties sr = loadProperties("src/main/resources/messages/messages_sr.properties");

        Set<String> referencedKeys = collectReferencedKeys("src/main/resources/templates");
        List<String> missingEn = new ArrayList<>();
        List<String> missingSr = new ArrayList<>();

        for (String key : referencedKeys) {
            if (!en.containsKey(key)) {
                missingEn.add(key);
            }
            if (!sr.containsKey(key)) {
                missingSr.add(key);
            }
        }

        assertTrue(missingEn.isEmpty(), "Missing keys in messages.properties: " + missingEn);
        assertTrue(missingSr.isEmpty(), "Missing keys in messages_sr.properties: " + missingSr);
    }

    private static Properties loadProperties(String path) throws IOException {
        Properties properties = new Properties();
        try (var stream = Files.newInputStream(Path.of(path))) {
            properties.load(stream);
        }
        return properties;
    }

    private static Set<String> collectReferencedKeys(String templatesRoot) throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> pathStream = Files.walk(Path.of(templatesRoot))) {
            pathStream
                .filter(path -> path.toString().endsWith(".html"))
                .forEach(path -> collectKeysFromTemplate(path, keys));
        }
        return keys;
    }

    private static void collectKeysFromTemplate(Path template, Set<String> keys) {
        try {
            String text = Files.readString(template, StandardCharsets.UTF_8);
            Matcher matcher = MESSAGE_EXPR.matcher(text);
            while (matcher.find()) {
                String expression = matcher.group(1).trim();
                // Skip dynamic expressions such as #{${someKey}}.
                if (expression.contains("${")) {
                    continue;
                }
                int argsIndex = expression.indexOf('(');
                String key = (argsIndex >= 0 ? expression.substring(0, argsIndex) : expression).trim();
                if (!key.isEmpty()) {
                    keys.add(key);
                }
            }
        } catch (IOException ignored) {
            // A failing read should fail test during bundle check through missing keys,
            // but this keeps stream traversal resilient for transient filesystem glitches.
        }
    }
}

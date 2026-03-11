package com.printflow.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PublicMessagesConsistencyTest {

    private static final List<String> PUBLIC_KEYS = List.of(
        "track.error.too_many_requests",
        "public.error.access_denied",
        "public.error.too_many_requests",
        "public.error.too_many_requests_for_order",
        "public.error.request_unavailable",
        "public.error.unable_to_process",
        "public.upload.error.select_file",
        "public.upload.error.access_denied",
        "public.upload.error.too_many_requests",
        "public.upload.error.too_many_uploads",
        "public.upload.error.limit_reached",
        "public.upload.error.metadata_mismatch",
        "public.upload.error.unavailable",
        "public.upload.error.generic",
        "public.order.error.company_not_found",
        "public.order.error.request_not_found",
        "public.order.error.file_not_found",
        "public.order.error.too_many_requests",
        "public.order.error.max_files",
        "public.order.error.file_too_large",
        "public.order.error.file_type_not_allowed",
        "public.order.error.upload_failed",
        "public.order.error.generic",
        "order_not_found.title",
        "order_not_found.heading",
        "order_not_found.message",
        "order_not_found.track_another",
        "order_not_found.public_home"
    );

    @Test
    void publicErrorKeysExistInEnglishAndSerbianBundles() throws Exception {
        Properties en = loadProperties("messages/messages.properties");
        Properties sr = loadProperties("messages/messages_sr.properties");

        for (String key : PUBLIC_KEYS) {
            String enValue = en.getProperty(key);
            String srValue = sr.getProperty(key);
            assertNotNull(enValue, "Missing EN key: " + key);
            assertNotNull(srValue, "Missing SR key: " + key);
            assertFalse(enValue.isBlank(), "Empty EN key: " + key);
            assertFalse(srValue.isBlank(), "Empty SR key: " + key);
        }
    }

    private Properties loadProperties(String classpathLocation) throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathLocation)) {
            assertNotNull(input, "Cannot load resource: " + classpathLocation);
            Properties props = new Properties();
            props.load(input);
            return props;
        }
    }
}

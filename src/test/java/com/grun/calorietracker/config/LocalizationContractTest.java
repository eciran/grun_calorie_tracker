package com.grun.calorietracker.config;

import com.grun.calorietracker.enums.ApiErrorCode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationContractTest {

    @Test
    void englishAndTurkishBundlesHaveIdenticalKeys() throws IOException {
        Properties english = load("messages.properties");
        Properties turkish = load("messages_tr.properties");

        assertEquals(english.stringPropertyNames(), turkish.stringPropertyNames());
    }

    @Test
    void everyApiErrorCodeHasEnglishAndTurkishCopy() throws IOException {
        Properties english = load("messages.properties");
        Properties turkish = load("messages_tr.properties");

        for (ApiErrorCode errorCode : ApiErrorCode.values()) {
            assertTrue(english.containsKey(errorCode.messageKey()), errorCode + " is missing English copy");
            assertTrue(turkish.containsKey(errorCode.messageKey()), errorCode + " is missing Turkish copy");
        }
    }

    private Properties load(String resourceName) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertTrue(input != null, resourceName + " was not found");
            properties.load(input);
        }
        return properties;
    }
}

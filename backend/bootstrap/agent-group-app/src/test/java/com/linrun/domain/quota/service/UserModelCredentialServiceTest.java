package com.linrun.domain.quota.service;

import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserModelCredentialServiceTest {

    @Test
    void shouldRejectPrivateNetworkBaseUrl() {
        UserModelCredentialService service = new UserModelCredentialService();

        AppException exception = assertThrows(AppException.class,
                () -> service.normalizeModelBaseUrl("https://127.0.0.1/v1"));

        assertEquals("MODEL_CONFIG_0002", exception.getCode());
    }

    @Test
    void shouldNormalizeHttpsBaseUrl() {
        UserModelCredentialService service = new UserModelCredentialService();

        assertEquals("https://api.example.com/v1",
                service.normalizeModelBaseUrl("api.example.com/v1"));
    }

    @Test
    void shouldEncryptAndDecryptApiKey() {
        UserModelCredentialService service = new UserModelCredentialService();
        ReflectionTestUtils.setField(service, "modelConfigCryptoSecret", "unit-test-credential");

        String encrypted = service.encryptApiKey("credential-test-value");
        String decrypted = service.decryptApiKey(encrypted);

        assertTrue(encrypted.startsWith("v1:"));
        assertEquals("credential-test-value", decrypted);
        assertEquals("cred****alue", service.maskApiKey("credential-test-value"));
    }
}

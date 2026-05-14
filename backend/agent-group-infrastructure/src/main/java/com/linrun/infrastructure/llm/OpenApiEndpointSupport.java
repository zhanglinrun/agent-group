package com.linrun.infrastructure.llm;

import java.net.URI;

public final class OpenApiEndpointSupport {

    private OpenApiEndpointSupport() {
    }

    public static URI uri(String baseUrl, String pathUnderV1) {
        String normalizedBaseUrl = trimTrailingSlash(baseUrl == null ? "" : baseUrl.trim());
        String normalizedPath = trimLeadingSlash(pathUnderV1 == null ? "" : pathUnderV1.trim());
        if (normalizedBaseUrl.endsWith("/v1")) {
            return URI.create(normalizedBaseUrl + "/" + normalizedPath);
        }
        return URI.create(normalizedBaseUrl + "/v1/" + normalizedPath);
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String trimLeadingSlash(String value) {
        String result = value;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }
}

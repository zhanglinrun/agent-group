package com.linrun.trigger.http.agent;

import org.springframework.util.StringUtils;

/**
 * API 密钥验证工具�?
 * 统一处理 API 密钥的有效性校�?
 */
public class ApiKeyValidator {

    /**
     * 验证 API 密钥是否有效
     * 
     * @param apiKey API 密钥
     * @return 如果密钥非空且不包含占位符则返回 true
     */
    public static boolean isValidApiKey(String apiKey) {
        return StringUtils.hasText(apiKey) && !apiKey.contains("XXXXX");
    }

    /**
     * 验证多个 API 密钥是否都有�?
     * 
     * @param apiKeys API 密钥数组
     * @return 如果所有密钥都有效则返�?true
     */
    public static boolean areAllValidApiKeys(String... apiKeys) {
        if (apiKeys == null || apiKeys.length == 0) {
            return false;
        }
        for (String apiKey : apiKeys) {
            if (!isValidApiKey(apiKey)) {
                return false;
            }
        }
        return true;
    }
}
















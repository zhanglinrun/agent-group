package com.linrun.domain.agent.runtime.security;

import com.linrun.types.exception.AppException;

import java.util.regex.Pattern;

/**
 * 用户输入的提示词注入防护。
 *
 * 在用户问题进入提示词拼装之前做三件事：
 * 1. 清理零宽字符和控制字符（常见的注入混淆手段）；
 * 2. 限制输入长度，避免超长输入挤占上下文；
 * 3. 拦截明显的指令覆盖、系统提示词套取、密钥套取类输入。
 *
 * 规则刻意保持收紧，只拦截"指令式"的注入语句，
 * 正常讨论注入攻防的学术问题不会命中。
 */
public final class PromptInjectionGuard {

    public static final String ERROR_CODE = "AGENT_SEC_0001";
    public static final String ERROR_MESSAGE = "检测到疑似提示词注入内容，请调整提问后重试";

    static final int MAX_INPUT_LENGTH = 8000;

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u200B-\\u200F\\u202A-\\u202E\\uFEFF]");

    private static final Pattern[] BLOCKED_PATTERNS = {
            // 覆盖既有指令
            Pattern.compile("(?i)(ignore|disregard|forget)\\s+(all\\s+)?(your\\s+)?(previous|prior|above|earlier)\\s+(instructions?|prompts?|rules?)"),
            Pattern.compile("忽略(掉)?(之前|以上|前面|上面|先前)的?(所有|全部)?(指令|提示词?|设定|规则|要求)"),
            // 套取系统提示词
            Pattern.compile("(?i)(reveal|print|show|output|repeat|leak)\\s+(your\\s+|the\\s+)?system\\s+prompt"),
            Pattern.compile("(输出|打印|显示|泄露|复述|告诉我)你?的?(系统提示词?|system\\s*prompt|初始指令)"),
            // 越狱模式
            Pattern.compile("(?i)you\\s+are\\s+now\\s+(in\\s+)?(developer|dan|jailbreak)\\s+mode"),
            Pattern.compile("(进入|开启|切换到)(开发者模式|DAN ?模式|越狱模式)"),
            // 套取密钥（覆盖动词在前和动词在后两种语序）
            Pattern.compile("(?i)(输出|打印|泄露|告诉我|show|reveal|print|leak)[^\\n]{0,16}(api[\\s_-]?key|access[\\s_-]?token|密钥|secret\\s+key)"),
            Pattern.compile("(?i)(api[\\s_-]?key|access[\\s_-]?token|密钥|secret\\s+key)[^\\n]{0,16}(输出|打印|泄露|告诉|发给|发我)")
    };

    private PromptInjectionGuard() {
    }

    /**
     * 清理并校验用户输入：命中注入规则时抛出 {@link AppException}，
     * 否则返回去掉混淆字符、截断到安全长度的文本。
     */
    public static String sanitize(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return userInput == null ? "" : userInput;
        }
        String cleaned = CONTROL_CHARS.matcher(userInput).replaceAll("");
        if (cleaned.length() > MAX_INPUT_LENGTH) {
            cleaned = cleaned.substring(0, MAX_INPUT_LENGTH);
        }
        if (isSuspicious(cleaned)) {
            throw new AppException(ERROR_CODE, ERROR_MESSAGE);
        }
        return cleaned;
    }

    /**
     * 仅判断是否命中注入规则，不修改输入。
     */
    public static boolean isSuspicious(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return false;
        }
        for (Pattern pattern : BLOCKED_PATTERNS) {
            if (pattern.matcher(userInput).find()) {
                return true;
            }
        }
        return false;
    }
}

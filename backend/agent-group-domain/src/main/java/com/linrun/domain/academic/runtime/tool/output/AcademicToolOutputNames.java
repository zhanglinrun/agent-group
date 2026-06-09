package com.linrun.domain.academic.runtime.tool.output;

import java.util.List;
import java.util.Set;

public final class AcademicToolOutputNames {

    public static final String DEEP_SEARCH = "deep_search";
    public static final String FILE_TOOL = "file_tool";
    public static final String CODE_INTERPRETER = "code_interpreter";
    public static final String REPORT_TOOL = "report_tool";
    public static final String WEB_FETCH = "web_fetch";
    public static final String DATA_ANALYSIS = "data_analysis";
    public static final String MULTIMODAL_AGENT = "multimodal_agent";
    public static final String IMAGE_GENERATION = "image_generation";
    public static final String SCRIPT_RUNNER = "script_runner";
    public static final String PLANNING = "planning";
    public static final String TABLE_RAG = "table_rag";
    public static final String NL2SQL = "nl2sql";
    public static final String QUOTA_USAGE = "quota_usage";

    public static final Set<String> RICH_TOOL_NAMES = Set.of(
            DEEP_SEARCH,
            FILE_TOOL,
            CODE_INTERPRETER,
            REPORT_TOOL,
            WEB_FETCH,
            DATA_ANALYSIS,
            MULTIMODAL_AGENT,
            IMAGE_GENERATION,
            SCRIPT_RUNNER,
            PLANNING,
            TABLE_RAG,
            NL2SQL
    );

    private AcademicToolOutputNames() {
    }

    public static boolean isRichTool(String toolName) {
        return RICH_TOOL_NAMES.contains(toolName);
    }

    public static List<String> orderedRichToolNames() {
        return List.of(
                WEB_FETCH,
                DATA_ANALYSIS,
                REPORT_TOOL,
                PLANNING,
                CODE_INTERPRETER,
                IMAGE_GENERATION,
                MULTIMODAL_AGENT,
                DEEP_SEARCH,
                FILE_TOOL,
                SCRIPT_RUNNER,
                TABLE_RAG,
                NL2SQL
        );
    }
}
















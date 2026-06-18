package com.linrun.trigger.http.agent.support;

import com.linrun.domain.account.model.UserAccount;
import com.linrun.trigger.agent.entity.record.FileInfo;
import com.linrun.trigger.agent.service.FileInfoService;
import com.linrun.trigger.agent.service.FileManageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 学术 Agent 的文件管理服务，从 AcademicAgentNativeService 抽出。
 * 负责文件上传、查询、内容读取、删除、列表和存在性校验，统一走文件归属校验。
 */
@Service
public class AcademicAgentFileService {

    private final FileManageService fileManageService;
    private final FileInfoService fileInfoService;
    private final AgentContextResolver agentContextResolver;

    public AcademicAgentFileService(FileManageService fileManageService,
                                     FileInfoService fileInfoService,
                                     AgentContextResolver agentContextResolver) {
        this.fileManageService = fileManageService;
        this.fileInfoService = fileInfoService;
        this.agentContextResolver = agentContextResolver;
    }

    public FileInfo upload(String token, MultipartFile file, String conversationId) {
        UserAccount user = agentContextResolver.user(token);
        FileInfo fileInfo = fileManageService.uploadFile(file);
        String ownerConversationId = StringUtils.hasText(conversationId) ? conversationId : "__global";
        fileInfo.setConversationId(agentContextResolver.internalConversationId(user.getUserId(), ownerConversationId));
        fileInfoService.updateFileInfo(fileInfo);
        return fileInfo;
    }

    public FileInfo getFileInfo(String token, String fileId) {
        UserAccount user = agentContextResolver.user(token);
        FileInfo fileInfo = fileManageService.getFileInfo(fileId);
        agentContextResolver.assertOwnedFile(user.getUserId(), fileInfo);
        return fileInfo;
    }

    public String getFileContent(String token, String fileId) {
        getFileInfo(token, fileId);
        return fileManageService.getFileContent(fileId);
    }

    public void deleteFile(String token, String fileId) {
        getFileInfo(token, fileId);
        fileManageService.deleteFile(fileId);
    }

    public List<FileInfo> listFiles(String token) {
        UserAccount user = agentContextResolver.user(token);
        String prefix = user.getUserId() + ":";
        return fileInfoService.getAllFiles().stream()
                .filter(file -> StringUtils.hasText(file.getConversationId()) && file.getConversationId().startsWith(prefix))
                .toList();
    }

    public boolean fileExists(String token, String fileId) {
        try {
            getFileInfo(token, fileId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

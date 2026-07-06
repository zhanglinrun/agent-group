package com.linrun.reactor.api;

import com.linrun.reactor.api.dto.AiAgentResponseDTO;
import com.linrun.reactor.api.dto.ArmoryAgentRequestDTO;
import com.linrun.reactor.api.dto.ArmoryApiRequestDTO;
import com.linrun.reactor.api.dto.AutoAgentRequestDTO;
import com.linrun.reactor.api.response.Response;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;


public interface IAiAgentService {

//    ResponseBodyEmitter autoAgent(AutoAgentRequestDTO request, HttpServletResponse response);

    /**
     * 装配智能体
     */
    Response<Boolean> armoryAgent(ArmoryAgentRequestDTO request);

    /**
     * 查询可用的智能体列表
     */
    Response<List<AiAgentResponseDTO>> queryAvailableAgents();

    /**
     * 装配API
     */
    Response<Boolean> armoryApi(ArmoryApiRequestDTO request);

}

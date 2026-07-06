package com.linrun.reactor.api;

import com.linrun.reactor.api.dto.DataStatisticsResponseDTO;
import com.linrun.reactor.api.response.Response;


public interface IAiAgentDataStatisticsAdminService {

    /**
     * 获取系统数据统计
     * @return 统计数据响应
     */
    Response<DataStatisticsResponseDTO> getDataStatistics();
}

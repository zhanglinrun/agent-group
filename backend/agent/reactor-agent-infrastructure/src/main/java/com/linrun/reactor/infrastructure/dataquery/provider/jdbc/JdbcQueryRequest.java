package com.linrun.reactor.infrastructure.dataquery.provider.jdbc;


import lombok.Data;
import com.linrun.reactor.infrastructure.dataquery.jdbc.JdbcConnectionConfig;
import com.linrun.reactor.domain.agent.reactor.data.provider.DataQueryRequest;

@Data
public class JdbcQueryRequest implements DataQueryRequest {

    private JdbcConnectionConfig jdbcConnectionConfig;
    private String sql;
    private int limit;

    private int pageIndex;
    private int pageSize;
}


package com.linrun.reactor.infrastructure.dataquery.jdbc.connection;



import lombok.Data;
import com.linrun.reactor.infrastructure.dataquery.jdbc.catalog.JdbcCatalog;
import com.linrun.reactor.infrastructure.dataquery.jdbc.dialect.JdbcDialect;

import javax.sql.DataSource;

@Data
public class DatasourceWrapper {

    private DataSource dataSource;

    private JdbcDialect jdbcDialect;

    private JdbcCatalog catalog;

    private Long freshTime;
}


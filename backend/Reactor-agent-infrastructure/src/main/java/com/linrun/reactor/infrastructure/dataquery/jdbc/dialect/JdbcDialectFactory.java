package com.linrun.reactor.infrastructure.dataquery.jdbc.dialect;

public interface JdbcDialectFactory {

    boolean acceptsURL(String url);

    JdbcDialect create();
}


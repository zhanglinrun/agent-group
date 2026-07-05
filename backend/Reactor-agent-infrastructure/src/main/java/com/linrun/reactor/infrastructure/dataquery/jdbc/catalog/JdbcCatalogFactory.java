package com.linrun.reactor.infrastructure.dataquery.jdbc.catalog;


import com.linrun.reactor.infrastructure.dataquery.jdbc.dialect.DialectEnum;

public interface JdbcCatalogFactory {

    DialectEnum jdbcDialect();

    /**
     * Creates a {@link JdbcCatalog} using the options.
     */
    JdbcCatalog createCatalog();
}


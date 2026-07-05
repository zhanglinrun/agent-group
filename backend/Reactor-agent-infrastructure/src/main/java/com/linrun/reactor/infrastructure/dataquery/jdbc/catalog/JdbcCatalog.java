
package com.linrun.reactor.infrastructure.dataquery.jdbc.catalog;




import com.linrun.reactor.domain.agent.reactor.data.SimpleTable;
import com.linrun.reactor.domain.agent.reactor.data.TableColumn;
import com.linrun.reactor.domain.agent.reactor.data.exception.CatalogException;

import java.sql.Connection;
import java.util.List;

public interface JdbcCatalog {

    List<SimpleTable> listTables(Connection connection, String schema) throws CatalogException;

    List<TableColumn> getTableColumns(Connection connection, String tablePath, String schema) throws CatalogException;
}


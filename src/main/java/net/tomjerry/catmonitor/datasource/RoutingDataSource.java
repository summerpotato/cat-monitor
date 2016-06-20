package net.tomjerry.catmonitor.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 根据线程中数据源名称，返回数据源
 * @author joonk
 *
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

	@Override
	protected Object determineCurrentLookupKey() {
		return DataSourceHolder.getDBName();
	}

	
}

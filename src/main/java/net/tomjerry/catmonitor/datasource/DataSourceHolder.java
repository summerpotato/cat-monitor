package net.tomjerry.catmonitor.datasource;

/**
 * 在当前线程中保存数据源信息
 * 
 * @author potato
 */
public class DataSourceHolder {

	private static final ThreadLocal<String> contextHolder = new ThreadLocal<String>();
	
	public static void setDBName(String dbName) {
		contextHolder.set(dbName);
	}
	
	public static String getDBName() {
		return contextHolder.get();
	}
	
	public static void clearDBName() {
		contextHolder.remove();
	}
	
}

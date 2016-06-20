package net.tomjerry.catmonitor.mybatisext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.mybatis.spring.transaction.SpringManagedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;

import com.alibaba.druid.pool.DruidDataSource;
import com.dianping.cat.Cat;
import com.dianping.cat.message.Transaction;

/**
 * 对MyBatis进行拦截，添加Cat监控
 * 目前仅支持RoutingDataSource和Druid组合配置的数据源
 * 
 * @author joonk
 */

@Intercepts({
    @Signature(method = "query", type = Executor.class, args = {  
            MappedStatement.class, Object.class, RowBounds.class,  
            ResultHandler.class }),
    @Signature(method = "update", type = Executor.class, args = { MappedStatement.class, Object.class }) 
})
public class CatMybatisPlugin implements Interceptor {

	private static Logger logger = LoggerFactory.getLogger(CatMybatisPlugin.class);
	
	//缓存，提高性能
    private static final Map<String, String> sqlURLCache = new ConcurrentReferenceHashMap<String, String>(256);

    private static final String EMPTY_CONNECTION = "jdbc:mysql://unknown:3306/%s?useUnicode=true";

    private Executor target;
	
	@Override
	public Object intercept(Invocation invocation) throws Throwable {
		MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        //得到类名，方法
        String[] strArr = mappedStatement.getId().split("\\.");
        String methodName = strArr[strArr.length - 2] + "." + strArr[strArr.length - 1];


        Transaction t = Cat.newTransaction("SQL", methodName);

        //获取SQL类型
        SqlCommandType sqlCommandType = mappedStatement.getSqlCommandType();
        Cat.logEvent("SQL.Method", sqlCommandType.name().toLowerCase());

        String s = this.getSQLDatabase();
        Cat.logEvent("SQL.Database", s);

        Object returnObj = null;
        try {
            returnObj = invocation.proceed();
            t.setStatus(Transaction.SUCCESS);
        } catch (Exception e) {
            Cat.logError(e);
        } finally {
            t.complete();
        }

        return returnObj;
	}

	private javax.sql.DataSource getDataSource() {
        org.apache.ibatis.transaction.Transaction transaction = this.target.getTransaction();
        if (transaction == null) {
            logger.error(String.format("Could not find transaction on target [%s]", this.target));
            return null;
        }
        if (transaction instanceof SpringManagedTransaction) {
            String fieldName = "dataSource";
            Field field = ReflectionUtils.findField(transaction.getClass(), fieldName, javax.sql.DataSource.class);

            if (field == null) {
                logger.error(String.format("Could not find field [%s] of type [%s] on target [%s]",
                        fieldName, javax.sql.DataSource.class, this.target));
                return null;
            }

            ReflectionUtils.makeAccessible(field);
            javax.sql.DataSource dataSource = (javax.sql.DataSource) ReflectionUtils.getField(field, transaction);
            return dataSource;
        }

        logger.error(String.format("---the transaction is not SpringManagedTransaction:%s", transaction.getClass().toString()));

        return null;
    }

    private String getSqlURL() {
        javax.sql.DataSource dataSource = this.getDataSource();

        if (dataSource == null) {
            return null;
        }

        if (dataSource instanceof AbstractRoutingDataSource) {
            String methodName = "determineTargetDataSource";
            Method method = ReflectionUtils.findMethod(AbstractRoutingDataSource.class, methodName);

            if (method == null) {
                logger.error(String.format("---Could not find method [%s] on target [%s]",
                        methodName,  dataSource));
                return null;
            }

            ReflectionUtils.makeAccessible(method);
            javax.sql.DataSource dataSource1 = (javax.sql.DataSource) ReflectionUtils.invokeMethod(method, dataSource);
            if (dataSource1 instanceof DruidDataSource) {
                DruidDataSource druidDataSource = (DruidDataSource) dataSource1;
                return druidDataSource.getUrl();
            } else {
                logger.error("---only surpport DruidDataSource:" + dataSource1.getClass().toString());
            }
        }
        return null;
    }

    private String getSQLDatabase() {
        String dbName = DataSourceHolder.getDBName();
        if (dbName == null) {
            dbName = "DEFAULT";
        }
        String url = CatMybatisPlugin.sqlURLCache.get(dbName);
        if (url != null) {
            return url;
        }

        url = this.getSqlURL();
        if (url == null) {
            url = String.format(EMPTY_CONNECTION, dbName);
        }
        CatMybatisPlugin.sqlURLCache.put(dbName, url);
        return url;
    }
	
	
	@Override
    public Object plugin(Object target) {
        if (target instanceof Executor) {
            this.target = (Executor) target;
            return Plugin.wrap(target, this);
        }
        return target;
    }

	@Override
	public void setProperties(Properties properties) {
	}

}

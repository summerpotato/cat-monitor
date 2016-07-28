package net.tomjerry.catmonitor.datasource;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * 动态切换数据源,切面可调用本方法
 * @author potato
 */
public class DataSourceAspectUtil {

	//注解缓存，提高性能
	private static final Map<Method, DataSource> findAnnotationCache =
			new ConcurrentReferenceHashMap<Method, DataSource>(256);


	public static Object selectDataSource(ProceedingJoinPoint point) throws Throwable {
		String currentDB = DataSourceHolder.getDBName();
		if (currentDB == null) {
			/** 
			 * 只有在上下文中没有指定数据库时才选择数据库
			 * 确保在一个嵌套Session中使用同一个DB
			 * 即：如果在Controller和DAO(或Mapper)中指定同时指定了DB，Controller中的值将被使用
			 */
			DataSource a = getDataSourceAnnotation(point);
			if (a != null && a != NULL_ANNOTATION) {
				String dbName = a.value();
				DataSourceHolder.setDBName(dbName);
			}
		}

		try {
			Object result = point.proceed();
			return result;
		} finally {
			//恢复当前线程中的设置
			DataSourceHolder.setDBName(currentDB);
		}
	}

	/**
	 * 从方法或类或实现的接口中获取DataSource注解
	 */
	private static DataSource getDataSourceAnnotation(JoinPoint point) throws NoSuchMethodException, SecurityException {
		Object target = point.getTarget();
		Class<?> classz = target.getClass();
		String methodName = point.getSignature().getName();
		Class<?>[] parameterTypes = ((MethodSignature) point.getSignature())
				.getMethod().getParameterTypes();
		Method method = target.getClass().getMethod(methodName, parameterTypes);

		DataSource result = findAnnotationCache.get(method);
		if (result == NULL_ANNOTATION) {
			return null;
		}
		if (result != null) {
			return result;
		}

		result = AnnotationUtils.findAnnotation(method, DataSource.class);
		if (result == null) {
			result = AnnotationUtils.findAnnotation(classz, DataSource.class);
		}
		if (result == null) {
			result = NULL_ANNOTATION;
		}

		findAnnotationCache.put(method, result);//保存到缓存

		return result;
	}


	//定义个NULL常量，方便缓存
	private static final DataSource NULL_ANNOTATION = new DataSource() {
		@Override
		public Class<? extends Annotation> annotationType() {
			return null;
		}

		@Override
		public String value() {
			return null;
		}
	};
}

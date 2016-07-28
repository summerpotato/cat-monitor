package net.tomjerry.catmonitor.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;

/**
 * 将数据库名(DataSource注解的value值)保存到当前线程
 * @author potato
 *
 */
@Aspect
public class AnnotationDataSourceAspect implements Ordered {

	@Around("@annotation(net.tomjerry.catmonitor.datasource.DataSource)")
	public Object selectDataSource(ProceedingJoinPoint point) throws Throwable {
		return DataSourceAspectUtil.selectDataSource(point);
	}
	
	@Override
	public int getOrder() {
		//确保在@Transactional之前调用
		return 0;
	}

}

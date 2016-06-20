package net.tomjerry.catmonitor.dubbospi;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;

/**
 * 获取application-name
 * @author kulijia
 */
@Activate(group = {Constants.CONSUMER})
public class AppNameAppendFilter implements Filter {

	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
		RpcContext.getContext().setAttachment(Constants.APPLICATION_KEY, invoker.getUrl().getParameter(Constants.APPLICATION_KEY));
        return invoker.invoke(invocation);
	}
	
}

package net.tomjerry.catmonitor.dubbospi;

import java.util.Map;

import net.tomjerry.catmonitor.common.CatConstants;
import net.tomjerry.catmonitor.common.CatContext;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.Activate;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.TimeoutException;
import com.alibaba.dubbo.rpc.Filter;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.dianping.cat.Cat;
import com.dianping.cat.message.Event;
import com.dianping.cat.message.Transaction;
import com.dianping.cat.message.internal.AbstractMessage;
import com.dianping.cat.message.internal.NullMessage;

/**
 * 消息树串联：dubbo-rpc服务调用
 * @author potato
 *
 */
@Activate(group = {Constants.PROVIDER, Constants.CONSUMER}, order = -9000)
public class DubboCatCrossFilter implements Filter {

	private static final ThreadLocal<Cat.Context> CAT_CONTEXT_LOCAL = new ThreadLocal<Cat.Context>();
	
	@Override
	public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {

		URL url = invoker.getUrl();
        String sideKey = url.getParameter(Constants.SIDE_KEY);
        String loggerName = invoker.getInterface().getSimpleName() + "." + invocation.getMethodName();
        String type = CatConstants.CROSS_CONSUMER;
        if(Constants.PROVIDER_SIDE.equals(sideKey)){
            type= CatConstants.CROSS_SERVER;
        }
        Transaction t = Cat.newTransaction(type, loggerName);
        Result result = null;
        try{
            Cat.Context context = this.getCatContext();
            if(Constants.CONSUMER_SIDE.equals(sideKey)){
                this.createConsumerCross(url, t);
                Cat.logRemoteCallClient(context);
            }else{
                this.createProviderCross(url, t);
                Cat.logRemoteCallServer(context);
            }
            this.setAttachment(context);
            result = invoker.invoke(invocation);
            
            if(result.hasException()){
                //给调用接口出现异常进行打点
                Throwable throwable = result.getException();
                Event event = null;
                if(RpcException.class == throwable.getClass()){
                    Throwable caseBy = throwable.getCause();
                    if(caseBy != null && caseBy.getClass() == TimeoutException.class){
                        event = Cat.newEvent("DUBBO_TIMEOUT_ERROR", loggerName);
                    }else{
                        event = Cat.newEvent("DUBBO_REMOTING_ERROR", loggerName);
                    }
                }else if(RemotingException.class.isAssignableFrom(throwable.getClass())){
                    event = Cat.newEvent("DUBBO_REMOTING_ERROR", loggerName);
                }else{
                    event = Cat.newEvent("DUBBO_BIZ_ERROR", loggerName);
                }
                event.setStatus(result.getException());
                this.completeEvent(event);
                t.addChild(event);
                t.setStatus(result.getException().getClass().getSimpleName());
            }else{
                t.setStatus(Transaction.SUCCESS);
            }
            return result;
        }catch (RuntimeException e){
            Event event = null;
            if(RpcException.class == e.getClass()){
                Throwable caseBy = e.getCause();
                if(caseBy != null && caseBy.getClass() == TimeoutException.class){
                    event = Cat.newEvent("DUBBO_TIMEOUT_ERROR", loggerName);
                }else{
                    event = Cat.newEvent("DUBBO_REMOTING_ERROR", loggerName);
                }
            }else{
                event = Cat.newEvent("DUBBO_BIZ_ERROR", loggerName);
            }
            event.setStatus(e);
            this.completeEvent(event);
            t.addChild(event);
            t.setStatus(e.getClass().getSimpleName());
            if(result == null){
                throw e;
            }else{
                return result;
            }
        } finally {
            t.complete();
            CAT_CONTEXT_LOCAL.remove();
        }
	}
	
	
	private Cat.Context getCatContext(){
        Cat.Context context = CAT_CONTEXT_LOCAL.get();
        if(context==null){
            context = initCatContext();
            CAT_CONTEXT_LOCAL.set(context);
        }
        return context;
    }

    private Cat.Context initCatContext(){
        Cat.Context context = new CatContext();
        Map<String,String> attachments = RpcContext.getContext().getAttachments();
        if(attachments != null && attachments.size() > 0){
            for(Map.Entry<String,String> entry : attachments.entrySet()){
                if(Cat.Context.CHILD.equals(entry.getKey()) || Cat.Context.ROOT.equals(entry.getKey()) || Cat.Context.PARENT.equals(entry.getKey())){
                    context.addProperty(entry.getKey(), entry.getValue());
                }
            }
        }
        return context;
    }
    
    private void setAttachment(Cat.Context context){
        RpcContext.getContext().setAttachment(Cat.Context.ROOT, context.getProperty(Cat.Context.ROOT));
        RpcContext.getContext().setAttachment(Cat.Context.CHILD, context.getProperty(Cat.Context.CHILD));
        RpcContext.getContext().setAttachment(Cat.Context.PARENT, context.getProperty(Cat.Context.PARENT));
    }
    
    private void createConsumerCross(URL url, Transaction t){
        Event crossAppEvent = Cat.newEvent(CatConstants.CONSUMER_CALL_APP, this.getProviderAppName(url));
        Event crossServerEvent = Cat.newEvent(CatConstants.CONSUMER_CALL_SERVER, url.getHost());
        Event crossPortEvent = Cat.newEvent(CatConstants.CONSUMER_CALL_PORT, url.getPort()+"");
        crossAppEvent.setStatus(Event.SUCCESS);
        crossServerEvent.setStatus(Event.SUCCESS);
        crossPortEvent.setStatus(Event.SUCCESS);
        completeEvent(crossAppEvent);
        completeEvent(crossPortEvent);
        completeEvent(crossServerEvent);
        t.addChild(crossAppEvent);
        t.addChild(crossPortEvent);
        t.addChild(crossServerEvent);
    }

    private void createProviderCross(URL url, Transaction t){
        String consumerAppName = RpcContext.getContext().getAttachment(Constants.APPLICATION_KEY);
        if(StringUtils.isEmpty(consumerAppName)){
            consumerAppName = RpcContext.getContext().getRemoteHost() + ":" + RpcContext.getContext().getRemotePort();
        }
        Event crossAppEvent = Cat.newEvent(CatConstants.PROVIDER_SERVICE_APP, consumerAppName);
        Event crossServerEvent = Cat.newEvent(CatConstants.PROVIDER_SERVICE_CLIENT, url.getHost());
        crossAppEvent.setStatus(Event.SUCCESS);
        crossServerEvent.setStatus(Event.SUCCESS);
        completeEvent(crossAppEvent);
        completeEvent(crossServerEvent);
        t.addChild(crossAppEvent);
        t.addChild(crossServerEvent);
    }
    
    private String getProviderAppName(URL url){
        String appName = url.getParameter(CatConstants.DUBBO_PROVIDER_APPLICATION_NAME);
        if(StringUtils.isEmpty(appName)){
            String interfaceName  = url.getParameter(Constants.INTERFACE_KEY);
            appName = interfaceName.substring(0,interfaceName.lastIndexOf('.'));
        }
        return appName;
    }
    
    private void completeEvent(Event event){
    	if (event != NullMessage.EVENT) {
	        AbstractMessage message = (AbstractMessage) event;
	        message.setCompleted(true);
    	}
    }

}

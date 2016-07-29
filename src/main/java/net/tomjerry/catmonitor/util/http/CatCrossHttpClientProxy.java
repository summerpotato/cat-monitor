package net.tomjerry.catmonitor.util.http;

import java.io.IOException;

import net.tomjerry.catmonitor.common.CatConstants;
import net.tomjerry.catmonitor.common.CatContext;

import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.methods.HttpRequestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dianping.cat.Cat;
import com.dianping.cat.message.Event;
import com.dianping.cat.message.Transaction;
import com.dianping.cat.message.internal.AbstractMessage;
import com.dianping.cat.message.internal.NullMessage;

/**
 * 支持CatCross监控的HttpClient类
 * 
 * @author potato
 * @date 2016-6-8
 */
@ThreadSafe
public class CatCrossHttpClientProxy extends HttpClientProxy {

	private Logger logger = LoggerFactory.getLogger(CatCrossHttpClientProxy.class);
    
    //please inject this attr in the spring-applicationContext
    private String applicationName;
    
    public String getApplicationName() {
		return applicationName;
	}

	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	/**
     * 发起Http请求
     * 较复杂的请求如设置HttpHeader等，可调用本方法执行
     * @param request
     * @param socketTimeout
     * @param connectTimeout
     * @return
     * @throws IOException
     */
    public String execute(HttpRequestBase request, int socketTimeout, int connectTimeout) throws IOException {
        Transaction t = Cat.newTransaction(CatConstants.CROSS_CONSUMER, request.getURI().getPath());
        
        //串联message-tree埋点
        this.createConsumerCross(request, t);
        Cat.Context context = new CatContext();
        Cat.logRemoteCallClient(context);
        request.setHeader(Cat.Context.ROOT, context.getProperty(Cat.Context.ROOT));
        request.setHeader(Cat.Context.PARENT, context.getProperty(Cat.Context.PARENT));
        request.setHeader(Cat.Context.CHILD, context.getProperty(Cat.Context.CHILD));
        
        //设置服务消费方的clientName
        request.setHeader(CatConstants.CLIENT_APPLICATION_NAME, applicationName);

        try {
            String ret = super.execute(request, socketTimeout, connectTimeout);
            t.setStatus(Transaction.SUCCESS);
            return ret;
        } catch (IOException e) {
        	
        	Event event = null;
			event = Cat.newEvent("HTTP_REST_CAT_ERROR", request.getURI().getPath());
			event.setStatus(e);
			completeEvent(event);
			t.addChild(event);
            t.setStatus(e.getClass().getSimpleName());
            throw e;
        } finally {
            t.complete();
        }
    }
    
    private void createConsumerCross(HttpRequestBase request, Transaction t) {
    	
    	String uriPath = request.getURI().getPath();
        Event crossAppEvent = Cat.newEvent(CatConstants.CONSUMER_CALL_APP, uriPath.substring(0, uriPath.lastIndexOf("/")));	//serverName
        Event crossServerEvent = Cat.newEvent(CatConstants.CONSUMER_CALL_SERVER, request.getURI().getHost());				//serverIp
        Event crossPortEvent = Cat.newEvent(CatConstants.CONSUMER_CALL_PORT, String.valueOf(request.getURI().getPort()));	
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
	
	private void completeEvent(Event event){
		if (event != NullMessage.EVENT) {
			AbstractMessage message = (AbstractMessage) event;
			message.setCompleted(true);
		}
    }
	
}

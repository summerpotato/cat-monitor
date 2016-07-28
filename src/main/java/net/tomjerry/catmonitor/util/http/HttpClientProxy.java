package net.tomjerry.catmonitor.util.http;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.annotation.ThreadSafe;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Http Client的包装类,基于4.4实现
 * 应根据具体应用个性化maxPerRoute, maxTotal, timeToLive参数
 * 
 * @author potato
 */
@ThreadSafe
public class HttpClientProxy implements InitializingBean {

	private Logger logger = LoggerFactory.getLogger(HttpClientProxy.class);
	
	private static final int DEFAULT_MAX_PER_ROUTE = 15;
    private static final int DEFAULT_MAX_TOTAL = 100;
    private static final int DEFAULT_TIME_TO_LIVE = 3000; //毫秒

	private CloseableHttpClient httpClient;

    private Integer maxPerRoute;

    private Integer maxTotal;

    private Integer timeToLive;

    private Integer defaultSocketTimeout = 1000;

    private Integer defaultConnectTimeout = 1000;

    public String get(String url) throws IOException {
        return this.get(url, -1, -1);
    }
	
	public String get(String url, int socketTimeout, int connectTimeout) throws IOException {
        HttpGet httpGet = new HttpGet(url);
	    return this.execute(httpGet, socketTimeout, connectTimeout);
	}

    public String postJson(String url, String json) throws IOException {
        return this.postJson(url, json, -1, -1);
    }

    public String postJson(String url, String json, int socketTimeout, int connectTimeout) throws IOException {
        StringEntity entity = new StringEntity(json, Consts.UTF_8);
        entity.setContentEncoding(ContentType.APPLICATION_JSON.getCharset().name());
        entity.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        return this.post(url, entity, socketTimeout, connectTimeout);
    }

    public String post(String url, HttpEntity entity) throws IOException {
        return this.post(url, entity, -1, -1);
    }

    public String post(String url, HttpEntity entity, int socketTimeout, int connectTimeout) throws IOException {
        HttpPost method = new HttpPost(url);
        method.setEntity(entity);
        return this.execute(method, socketTimeout, connectTimeout);
    }

    public String execute(HttpRequestBase request) throws IOException {
        return this.execute(request, -1, -1);
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
    public String execute(HttpRequestBase request,int socketTimeout, int connectTimeout) throws IOException {
        if (socketTimeout <= 0) {
            socketTimeout = this.defaultSocketTimeout;
        }
        if (connectTimeout <= 0) {
            connectTimeout = this.defaultConnectTimeout;
        }

        RequestConfig requestConfig = RequestConfig.custom()
                .setSocketTimeout(socketTimeout)
                .setConnectTimeout(connectTimeout)
                .build();

        request.setConfig(requestConfig);
        return httpClient.execute(request, new StringResponseHandler());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (this.httpClient != null) {
            return;
        }

        int timeToLive;
        if (this.timeToLive != null) {
            timeToLive = this.timeToLive.intValue();
        } else {
            timeToLive = DEFAULT_TIME_TO_LIVE;
        }
        PoolingHttpClientConnectionManager connectionManager =
                new PoolingHttpClientConnectionManager(timeToLive, TimeUnit.MILLISECONDS);

        SocketConfig socketConfig = SocketConfig.custom().setTcpNoDelay(true).build();
        connectionManager.setDefaultSocketConfig(socketConfig);

        connectionManager.setValidateAfterInactivity(1000);

        if (this.maxPerRoute != null) {
            connectionManager.setDefaultMaxPerRoute(this.maxPerRoute);
        } else {
            connectionManager.setDefaultMaxPerRoute(DEFAULT_MAX_PER_ROUTE);
        }
        if (this.maxTotal != null) {
            connectionManager.setMaxTotal(this.maxTotal);
        } else {
            connectionManager.setMaxTotal(DEFAULT_MAX_TOTAL);
        }

        HttpClientBuilder clientBuilder = HttpClients.custom();
        clientBuilder.setConnectionManager(connectionManager);
        this.httpClient = clientBuilder.build();
    }

    public void setHttpClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public CloseableHttpClient getHttpClient() {
        return this.httpClient;
    }

    public Integer getMaxPerRoute() {
        return maxPerRoute;
    }

    public void setMaxPerRoute(Integer maxPerRoute) {
        this.maxPerRoute = maxPerRoute;
    }

    public Integer getMaxTotal() {
        return maxTotal;
    }

    public void setMaxTotal(Integer maxTotal) {
        this.maxTotal = maxTotal;
    }

    public Integer getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(Integer timeToLive) {
        this.timeToLive = timeToLive;
    }

    public Integer getSocketTimeout() {
        return defaultSocketTimeout;
    }

    public void setSocketTimeout(Integer socketTimeout) {
        this.defaultSocketTimeout = socketTimeout;
    }

    public Integer getConnectTimeout() {
        return defaultConnectTimeout;
    }

    public void setConnectTimeout(Integer connectTimeout) {
        this.defaultConnectTimeout = connectTimeout;
    }

    /**
     * 将Http响应解析为字符串
     * 默认编码格式设置为UTF-8
     */
    public static class StringResponseHandler extends BasicResponseHandler {
        @Override
        public String handleEntity(final HttpEntity entity) throws IOException {
            return EntityUtils.toString(entity, "UTF-8");
        }
    }
	
}

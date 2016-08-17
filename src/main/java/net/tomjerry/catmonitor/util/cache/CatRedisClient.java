package net.tomjerry.catmonitor.util.cache;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;

import com.dianping.cat.Cat;
import com.dianping.cat.message.Transaction;

/**
 * 添加了Cat监控的Redis工具类
 * 只对execute(JedisCallback<T> jedisCallback, String key)添加了监控
 * execute(SharedRedisCallback<T> redisCallback)需要自己添加cat监控
 * 
 * @author potato
 */
public class CatRedisClient extends RedisClient {

	private Logger logger = LoggerFactory.getLogger(CatRedisClient.class);
	
	public static final String REDIS_TYPE_PREFIX = "Cache.";
    public static final String REDIS_EVENT_TYPE = "Cache.redis.server";
    private static final String REDIS_SERVER_IP_PATTERN = "%s(%s)";

    //cat auth level，设置这个字段在cat报表中区分不同的auth level
    private String catAuthLevel = "default";

    private String redisGroup;	//需注入来初始化，redis分组，用于在CAT分类redis服务器
    private String redisType;
    
    public String getRedisGroup() {
		return redisGroup;
	}

	public void setRedisGroup(String redisGroup) {
		this.redisGroup = redisGroup;
		
		if(StringUtils.isBlank(redisGroup)){
			this.redisType = "Cache.redis";
		}else{
			this.redisType = REDIS_TYPE_PREFIX + this.redisGroup;
		}
	}
    
    @Override
    public <T> T execute(JedisCallback<T> jedisCallback, String key) {
        return super.execute(new CatJedisCallback<T>(jedisCallback), key);
    }

    public class CatJedisCallback<T> implements JedisCallback<T> {
        private JedisCallback<T> callback;
        public CatJedisCallback(JedisCallback<T> callback) {
            this.callback = callback;
        }

        @Override
        public T doInJedis(Jedis jedis) {
            String host = jedis.getClient().getHost();
            int port = jedis.getClient().getPort();

            Transaction t = Cat.newTransaction(redisType, CatRedisClient.this.catAuthLevel + ':' + this.callback.redisMethodName());

            Cat.logEvent(REDIS_EVENT_TYPE, String.format(REDIS_SERVER_IP_PATTERN, host, port));
            try {
                T ret = this.callback.doInJedis(jedis);
                t.setStatus(Transaction.SUCCESS);
                return ret;
            } catch (Exception e) {
                t.setStatus(e.getClass().getSimpleName());
                //throw e;
                
                logger.error("--- redis cat error : ", e);
                return null;
                
            } finally {
                t.complete();
            }
        }

        @Override
        public String redisMethodName() {
            return this.callback.redisMethodName();
        }
    }

    public String getCatAuthLevel() {
        return catAuthLevel;
    }

    public void setCatAuthLevel(String catAuthLevel) {
        this.catAuthLevel = catAuthLevel;
    }
    
}

package net.tomjerry.catmonitor.util.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ShardedJedis;
import redis.clients.jedis.ShardedJedisPool;

/**
 * redis工具类
 * @author potato
 * @date 2016-6-16
 */
public class RedisClient {

	private static final Logger log = LoggerFactory.getLogger(RedisClient.class);

    private ShardedJedisPool shardedJedisPool;
    private int redisTimeout;	//redis缓存默认过时时间(秒)


    public boolean set(final String key, final String value) {
        return this.execute(new JedisCallback<Boolean>() {
            @Override
            public Boolean doInJedis(Jedis jedis) {
                String s = jedis.set(key, value);

                return "OK".equals(s);
            }

            @Override
            public String redisMethodName() {
                return "set";
            }
        }, key);
    }
    
    /**
     * 支持value为byte[]
     * @date 2016-8-15
     */
    public boolean setByte(final String key, final byte[] value){
    	
    	return this.execute(new JedisCallback<Boolean>() {
            @Override
            public Boolean doInJedis(Jedis jedis) {
                String s = jedis.set(key.getBytes(), value);

                return "OK".equals(s);
            }

            @Override
            public String redisMethodName() {
                return "set";
            }
        }, key);
    	
    }
    

    /**
     * 使用默认超时时间
     * @param key
     * @param value
     * @return
     */
    public boolean setex(final String key, final String value) {
        return this.setex(key, value, this.redisTimeout);
    }
    
    public boolean setex(final String key, final byte[] value) {
        return this.setexByte(key, value, this.redisTimeout);
    }

    /**
     *
     * @param key
     * @param value
     * @param timeout 超时时间(秒)
     * @return
     */
    public boolean setex(final String key, final String value, final int timeout) {
        return this.execute(new JedisCallback<Boolean>() {
            @Override
            public Boolean doInJedis(Jedis jedis) {
                String s = jedis.setex(key, timeout, value);
                return "OK".equals(s);
            }

            @Override
            public String redisMethodName() {
                return "setex";
            }
        }, key);
    }
    
    /**
     * 支持byte[]
     * @param key
     * @param value
     * @param timeout
     * @return
     */
    public boolean setexByte(final String key, final byte[] value, final int timeout) {
        return this.execute(new JedisCallback<Boolean>() {
            @Override
            public Boolean doInJedis(Jedis jedis) {
                String s = jedis.setex(key.getBytes(), timeout, value);
                return "OK".equals(s);
            }

            @Override
            public String redisMethodName() {
                return "setex";
            }
        }, key);
    }

    public String get(final String key) {
        return this.execute(new JedisCallback<String>() {
            @Override
            public String doInJedis(Jedis jedis) {
                String value = jedis.get(key);
                return value;
            }

            @Override
            public String redisMethodName() {
                return "get";
            }
        }, key);
    }
    
    /**
     * 支持key为byte[]
     * @param key
     * @return
     */
    public byte[] getByte(final String key) {
        return this.execute(new JedisCallback<byte[]>() {
            @Override
            public byte[] doInJedis(Jedis jedis) {
            	byte[] value = jedis.get(key.getBytes());
                return value;
            }

            @Override
            public String redisMethodName() {
                return "get";
            }
        }, key);
    }

    public Long delete(final String key) {
        return this.execute(new JedisCallback<Long>() {
            @Override
            public Long doInJedis(Jedis jedis) {
                return jedis.del(key);
            }

            @Override
            public String redisMethodName() {
                return "delete";
            }
        }, key);
    }

    public <T> T execute(SharedRedisCallback<T> redisCallback) {
        ShardedJedis jedis = null;
        try {
            jedis = this.getShardedJedisPool().getResource();
            return redisCallback.doInRedis(jedis);
        } finally {
            if (jedis != null) {
                try {
                    jedis.close();
                } catch (Exception e) {
                    log.error("redis close exception", e);
                }
            }
        }
    }

    public <T> T execute(JedisCallback<T> jedisCallback, String key) {
        ShardedJedis sharededJedis = null;
        try {
            sharededJedis = this.getShardedJedisPool().getResource();
            Jedis j = sharededJedis.getShard(key);
            return jedisCallback.doInJedis(j);
        } finally {
            if (sharededJedis != null) {
                try {
                    sharededJedis.close();
                } catch (Exception e) {
                    log.error("redis close exception", e);
                }
            }
        }
    }

    public ShardedJedisPool getShardedJedisPool() {
        return shardedJedisPool;
    }

    public void setShardedJedisPool(ShardedJedisPool shardedJedisPool) {
        this.shardedJedisPool = shardedJedisPool;
    }

    public int getRedisTimeout() {
        return redisTimeout;
    }

    public void setRedisTimeout(int redisTimeout) {
        this.redisTimeout = redisTimeout;
    }

    public static interface SharedRedisCallback<T> {

        public T doInRedis(ShardedJedis jedis);
    }

    /**
     * 使用于只对一个key进行操作的情况
     * @param <T>
     */
    public static interface JedisCallback<T> {

        public T doInJedis(Jedis jedis);

        /**
         * 返回要执行的redis方法名，如setex, get等
         */
        public String redisMethodName();

    }
    
}

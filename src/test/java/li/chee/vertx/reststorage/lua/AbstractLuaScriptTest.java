/*
 * ------------------------------------------------------------------------------------------------
 * Copyright 2014 by Swiss Post, Information Technology Services
 * ------------------------------------------------------------------------------------------------
 * $Id$
 * ------------------------------------------------------------------------------------------------
 */

package li.chee.vertx.reststorage.lua;

import li.chee.vertx.reststorage.RedisEmbeddedConfiguration;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Abstract class containing common methods for LuaScript tests
 */
public abstract class AbstractLuaScriptTest {

    final static String prefixResources = "rest-storage:resources";
    final static String prefixCollections = "rest-storage:collections";
    final static String expirableSet = "rest-storage:expirable";
    final static String prefixDeltaResources = "delta:resources";
    final static String prefixDeltaEtags = "delta:etags";

    public static final String MAX_EXPIRE = "9999999999999";

    Jedis jedis = null;

    @BeforeClass
    public static void config() {
        if (!useExternalRedis()) {
            RedisEmbeddedConfiguration.redisServer.start();
        }
    }

    @AfterClass
    public static void stopRedis() {
        if (!useExternalRedis()) {
            RedisEmbeddedConfiguration.redisServer.stop();
        }
    }

    @Before
    public void connect() {
        jedis = JedisFactory.createJedis();
    }

    @After
    public void disconnnect() {
        jedis.flushAll();
        jedis.close();
    }

    protected static boolean useExternalRedis() {
        String externalRedis = System.getenv("EXTERNAL_REDIS");
        return externalRedis != null;
    }

    protected double getNowAsDouble() {
        return (double) System.currentTimeMillis();
    }

    protected String getNowAsString() {
        return String.valueOf(System.currentTimeMillis());
    }

    protected String readScript(String scriptFileName) {
        return readScript(scriptFileName, false);
    }

    protected String readScript(String scriptFileName, boolean stripLogNotice) {
        BufferedReader in = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(scriptFileName)));
        StringBuilder sb;
        try {
            sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (stripLogNotice && line.contains("redis.LOG_NOTICE,")) {
                    continue;
                }
                sb.append(line + "\n");
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        return sb.toString();
    }

    protected String evalScriptPut(final String resourceName, final String resourceValue) {
        return evalScriptPut(resourceName, resourceValue, MAX_EXPIRE);
    }

    protected String evalScriptPut(final String resourceName, final String resourceValue, final String expire) {
        return evalScriptPut(resourceName, resourceValue, expire, UUID.randomUUID().toString());
    }

    @SuppressWarnings({"rawtypes", "unchecked", "serial"})
    protected String evalScriptPut(final String resourceName, final String resourceValue, final String expire, final String etag) {
        String putScript = readScript("put.lua");
        String etagTmp;
        if (etag != null && !etag.isEmpty()) {
            etagTmp = etag;
        } else {
            etagTmp = UUID.randomUUID().toString();
        }
        final String etagValue = etagTmp;
        return (String) jedis.eval(putScript, new ArrayList() {
                    {
                        add(resourceName);
                    }
                }, new ArrayList() {
                    {
                        add(prefixResources);
                        add(prefixCollections);
                        add(expirableSet);
                        add("false");
                        add(expire);
                        add("9999999999999");
                        add(resourceValue);
                        add(etagValue);
                    }
                }
        );
    }

}

package com.xxl.job.core.glue;

import com.xuxueli.crawler.parser.PageParser;
import com.xxl.job.core.glue.impl.CrawlerSpringGlueFactory;
import com.xxl.job.core.glue.impl.SpringGlueFactory;
import groovy.lang.GroovyClassLoader;
import groovy.util.GroovyScriptEngine;

import java.lang.reflect.Constructor;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * CrawlerGlueFactory
 *
 * @author Supreme
 * @date 2022/12/25
 */
public class CrawlerGlueFactory {
    private static CrawlerGlueFactory crawlerGlueFactory = new CrawlerSpringGlueFactory();
    public static CrawlerGlueFactory getInstance(){
        return crawlerGlueFactory;
    }
    public static void refreshInstance(int type){
        if (type == 0) {
            crawlerGlueFactory = new CrawlerGlueFactory();
        } else if (type == 1) {
            crawlerGlueFactory = new CrawlerSpringGlueFactory();
        }
    }
    /**
     * groovy class loader
     */
    private GroovyClassLoader groovyClassLoader = new GroovyClassLoader();
    /**
     * CLASS_CACHE
     */
    private ConcurrentMap<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    /**
     * load new instance, prototype
     *
     * @param codeSource
     * @return
     * @throws Exception
     */
    public PageParser loadNewInstance(String codeSource, String redirectUrl, Consumer<Boolean> consumer) throws Exception{
        if (codeSource!=null && codeSource.trim().length()>0) {
            Class<?> clazz = getCodeSourceClass(codeSource);
            if (clazz != null) {
                Constructor<?> constructor = clazz.getConstructor(String.class, Consumer.class);
                Object instance = constructor.newInstance(redirectUrl, consumer);
                if (instance!=null) {
                    if (instance instanceof PageParser) {
                        this.injectService(instance);
                        return (PageParser) instance;
                    } else {
                        throw new IllegalArgumentException(">>>>>>>>>>> xxl-glue, loadNewInstanceForPageParser error, "
                                + "cannot convert from instance["+ instance.getClass() +"] to PageParser");
                    }
                }
            }
        }
        throw new IllegalArgumentException(">>>>>>>>>>> xxl-glue, loadNewInstanceForPageParser error, instance is null");
    }

    /**
     * getCodeSourceClass
     *
     * @param codeSource
     * @return {@link Class}<{@link ?}>
     */
    private Class<?> getCodeSourceClass(String codeSource){
        try {
            // md5
            byte[] md5 = MessageDigest.getInstance("MD5").digest(codeSource.getBytes());
            String md5Str = new BigInteger(1, md5).toString(16);

            Class<?> clazz = CLASS_CACHE.get(md5Str);
            if(clazz == null){
                clazz = groovyClassLoader.parseClass(codeSource);
                CLASS_CACHE.putIfAbsent(md5Str, clazz);
            }
            return clazz;
        } catch (Exception e) {
            return groovyClassLoader.parseClass(codeSource);
        }
    }


    /**
     * inject service of bean field
     *
     * @param instance
     */
    public void injectService(Object instance) {
        // do something
    }
}

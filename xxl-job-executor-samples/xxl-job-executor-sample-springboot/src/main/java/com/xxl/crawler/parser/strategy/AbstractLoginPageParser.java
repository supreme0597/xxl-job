package com.xxl.crawler.parser.strategy;

import com.xuxueli.crawler.model.PageRequest;
import com.xuxueli.crawler.parser.PageParser;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.HashMap;
import java.util.function.Consumer;

/**
 * AbstractLoginPageParser
 *
 * @author Supreme
 * @date 2022/12/25
 */
public abstract class AbstractLoginPageParser<T> extends PageParser<T> {
    /**
     * redirectUrl
     */
    private final String redirectUrl;
    /**
     * consumer
     */
    private final Consumer<Boolean> consumer;

    /**
     * AbstractLoginPageParser
     */
    public AbstractLoginPageParser(String redirectUrl, Consumer<Boolean> consumer) {
        this.redirectUrl = redirectUrl;
        this.consumer = consumer;
    }

    /**
     * preParse
     *
     * @param pageRequest
     */
    @Override
    public void preParse(PageRequest pageRequest) {
        // TODO 登录获取cookies
        pageRequest.setCookieMap(new HashMap<>());
        pageRequest.setValidateTLSCertificates(Boolean.FALSE);
        super.preParse(pageRequest);
    }

    @Override
    public void parse(Document html, Element pageVoElement, T pageVo) {
        consumer.accept(parse(pageVo));
    }


    /**
     * parse pageVo
     *
     * @param pageVo            pageVo object
     */
    public abstract Boolean parse(T pageVo);
}
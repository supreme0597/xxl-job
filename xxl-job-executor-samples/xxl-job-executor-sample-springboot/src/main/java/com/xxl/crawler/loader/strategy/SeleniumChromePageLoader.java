package com.xxl.crawler.loader.strategy;

import com.xuxueli.crawler.loader.PageLoader;
import com.xuxueli.crawler.loader.strategy.SeleniumPhantomjsPageLoader;
import com.xuxueli.crawler.model.PageRequest;
import com.xuxueli.crawler.util.UrlUtil;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * "selenisum + chrome" page loader
 *
 * // TODO, selenium not support feature like : paramMap、headerMap、userAgent、referrer、ifPost
 *
 * @author Supreme
 * @date 2022/12/25
 */
public class SeleniumChromePageLoader extends PageLoader {
    /**
     * logger
     */
    private static Logger logger = LoggerFactory.getLogger(SeleniumPhantomjsPageLoader.class);

    /**
     * driverPath
     */
    private String driverPath;

    /**
     * consumer
     */
    private Consumer<WebDriver> consumer;

    /**
     * SeleniumChromePageLoader
     *
     * @param driverPath
     * @param consumer
     */
    public SeleniumChromePageLoader(String driverPath, Consumer<WebDriver> consumer) {
        this.driverPath = driverPath;
        this.consumer = consumer;
    }

    /**
     * load
     *
     * @param pageRequest
     * @return {@link Document}
     */
    @Override
    public Document load(PageRequest pageRequest) {
        if (!UrlUtil.isUrl(pageRequest.getUrl())) {
            return null;
        }

        // driver init
        ChromeOptions dcaps = new ChromeOptions();
        dcaps.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, !pageRequest.isValidateTLSCertificates());
        //dcaps.setCapability(CapabilityType.TAKES_SCREENSHOT, false);  // Deprecated
        if (driverPath!=null && driverPath.trim().length()>0) {
            System.setProperty(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY, driverPath);
        }

        if (pageRequest.getProxy() != null) {
            /*dcaps.setCapability(CapabilityType.ForSeleniumServer.AVOIDING_PROXY, true);   // Deprecated
            dcaps.setCapability(CapabilityType.ForSeleniumServer.ONLY_PROXYING_SELENIUM_TRAFFIC, true);*/
            System.setProperty("http.nonProxyHosts", "localhost");
            dcaps.setCapability(CapabilityType.PROXY, pageRequest.getProxy());
        }

        /*dcaps.setBrowserName(BrowserType.CHROME);
        dcaps.setVersion("70");
        dcaps.setPlatform(Platform.WIN10);*/

        dcaps.addArguments("--headless");
        ChromeDriver webDriver = new ChromeDriver(dcaps);

        try {
            // driver run
            webDriver.get(pageRequest.getUrl());

            if (pageRequest.getCookieMap() != null && !pageRequest.getCookieMap().isEmpty()) {
                for (Map.Entry<String, String> item: pageRequest.getCookieMap().entrySet()) {
                    webDriver.manage().addCookie(new Cookie(item.getKey(), item.getValue()));
                }
            }

            webDriver.manage().timeouts().implicitlyWait(Duration.ofMillis(pageRequest.getTimeoutMillis()));
            webDriver.manage().timeouts().pageLoadTimeout(Duration.ofMillis(pageRequest.getTimeoutMillis()));
            webDriver.manage().timeouts().setScriptTimeout(Duration.ofMillis(pageRequest.getTimeoutMillis()));

            consumer.accept(webDriver);

            String pageSource = webDriver.getPageSource();
            if (pageSource != null) {
                Document html = Jsoup.parse(pageSource);
                return html;
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (webDriver != null) {
                webDriver.quit();
            }
        }
        return null;
    }

}

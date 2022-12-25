package com.xxl.job.executor.service.jobhandler;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.xuxueli.crawler.XxlCrawler;
import com.xuxueli.crawler.annotation.PageFieldSelect;
import com.xuxueli.crawler.annotation.PageSelect;
import com.xuxueli.crawler.parser.PageParser;
import com.xxl.crawler.loader.strategy.SeleniumChromePageLoader;
import com.xxl.crawler.parser.strategy.AbstractLoginPageParser;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.glue.CrawlerGlueFactory;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.Data;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.By;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * XxlJob开发示例（Bean模式）
 *
 * 开发步骤：
 *      1、任务开发：在Spring Bean实例中，开发Job方法；
 *      2、注解配置：为Job方法添加注解 "@XxlJob(value="自定义jobhandler名称", init = "JobHandler初始化方法", destroy = "JobHandler销毁方法")"，注解value值对应的是调度中心新建任务的JobHandler属性的值。
 *      3、执行日志：需要通过 "XxlJobHelper.log" 打印执行日志；
 *      4、任务结果：默认任务结果为 "成功" 状态，不需要主动设置；如有诉求，比如设置任务结果为失败，可以通过 "XxlJobHelper.handleFail/handleSuccess" 自主设置任务结果；
 *
 * @author xuxueli 2019-12-11 21:52:51
 */
@Component
public class SampleXxlJob {
    private static Logger logger = LoggerFactory.getLogger(SampleXxlJob.class);


    /**
     * 1、简单任务示例（Bean模式）
     */
    @XxlJob("demoJobHandler")
    public void demoJobHandler() throws Exception {
        XxlJobHelper.log("XXL-JOB, Hello World.");

        for (int i = 0; i < 5; i++) {
            XxlJobHelper.log("beat at:" + i);
            TimeUnit.SECONDS.sleep(2);
        }
        // default success
    }


    /**
     * 2、分片广播任务
     */
    @XxlJob("shardingJobHandler")
    public void shardingJobHandler() throws Exception {

        // 分片参数
        int shardIndex = XxlJobHelper.getShardIndex();
        int shardTotal = XxlJobHelper.getShardTotal();

        XxlJobHelper.log("分片参数：当前分片序号 = {}, 总分片数 = {}", shardIndex, shardTotal);

        // 业务逻辑
        for (int i = 0; i < shardTotal; i++) {
            if (i == shardIndex) {
                XxlJobHelper.log("第 {} 片, 命中分片开始处理", i);
            } else {
                XxlJobHelper.log("第 {} 片, 忽略", i);
            }
        }

    }


    /**
     * 3、命令行任务
     */
    @XxlJob("commandJobHandler")
    public void commandJobHandler() throws Exception {
        String command = XxlJobHelper.getJobParam();
        int exitValue = -1;

        BufferedReader bufferedReader = null;
        try {
            // command process
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();
            //Process process = Runtime.getRuntime().exec(command);

            BufferedInputStream bufferedInputStream = new BufferedInputStream(process.getInputStream());
            bufferedReader = new BufferedReader(new InputStreamReader(bufferedInputStream));

            // command log
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                XxlJobHelper.log(line);
            }

            // command exit
            process.waitFor();
            exitValue = process.exitValue();
        } catch (Exception e) {
            XxlJobHelper.log(e);
        } finally {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }

        if (exitValue == 0) {
            // default success
        } else {
            XxlJobHelper.handleFail("command exit value("+exitValue+") is failed");
        }

    }


    /**
     * 4、跨平台Http任务
     *  参数示例：
     *      "url: http://www.baidu.com\n" +
     *      "method: get\n" +
     *      "data: content\n";
     */
    @XxlJob("httpJobHandler")
    public void httpJobHandler() throws Exception {

        // param parse
        String param = XxlJobHelper.getJobParam();
        if (param==null || param.trim().length()==0) {
            XxlJobHelper.log("param["+ param +"] invalid.");

            XxlJobHelper.handleFail();
            return;
        }

        String[] httpParams = param.split("\n");
        String url = null;
        String method = null;
        String data = null;
        for (String httpParam: httpParams) {
            if (httpParam.startsWith("url:")) {
                url = httpParam.substring(httpParam.indexOf("url:") + 4).trim();
            }
            if (httpParam.startsWith("method:")) {
                method = httpParam.substring(httpParam.indexOf("method:") + 7).trim().toUpperCase();
            }
            if (httpParam.startsWith("data:")) {
                data = httpParam.substring(httpParam.indexOf("data:") + 5).trim();
            }
        }

        // param valid
        if (url==null || url.trim().length()==0) {
            XxlJobHelper.log("url["+ url +"] invalid.");

            XxlJobHelper.handleFail();
            return;
        }
        if (method==null || !Arrays.asList("GET", "POST").contains(method)) {
            XxlJobHelper.log("method["+ method +"] invalid.");

            XxlJobHelper.handleFail();
            return;
        }
        boolean isPostMethod = method.equals("POST");

        // request
        HttpURLConnection connection = null;
        BufferedReader bufferedReader = null;
        try {
            // connection
            URL realUrl = new URL(url);
            connection = (HttpURLConnection) realUrl.openConnection();

            // connection setting
            connection.setRequestMethod(method);
            connection.setDoOutput(isPostMethod);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setReadTimeout(5 * 1000);
            connection.setConnectTimeout(3 * 1000);
            connection.setRequestProperty("connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            connection.setRequestProperty("Accept-Charset", "application/json;charset=UTF-8");

            // do connection
            connection.connect();

            // data
            if (isPostMethod && data!=null && data.trim().length()>0) {
                DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
                dataOutputStream.write(data.getBytes("UTF-8"));
                dataOutputStream.flush();
                dataOutputStream.close();
            }

            // valid StatusCode
            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                throw new RuntimeException("Http Request StatusCode(" + statusCode + ") Invalid.");
            }

            // result
            bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }
            String responseMsg = result.toString();

            XxlJobHelper.log(responseMsg);

            return;
        } catch (Exception e) {
            XxlJobHelper.log(e);

            XxlJobHelper.handleFail();
            return;
        } finally {
            try {
                if (bufferedReader != null) {
                    bufferedReader.close();
                }
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e2) {
                XxlJobHelper.log(e2);
            }
        }

    }

    /**
     * 5、生命周期任务示例：任务初始化与销毁时，支持自定义相关逻辑；
     */
    @XxlJob(value = "demoJobHandler2", init = "init", destroy = "destroy")
    public void demoJobHandler2() throws Exception {
        XxlJobHelper.log("XXL-JOB, Hello World.");

    }
    public void init(){
        logger.info("init");
    }
    public void destroy(){
        logger.info("destroy");
    }



    /**
     * 6、分布式爬虫任务
     */
    @XxlJob(value = "crawlerJob")
    public void crawlerJob() throws Exception {
        XxlJobHelper.log("XXL-JOB, Hello World.");

        String crawlerJobParamStr = XxlJobHelper.getJobParam();
        CrawlerJobParam crawlerJobParam = new CrawlerJobParam();

        if (StringUtils.hasLength(crawlerJobParamStr)) {
            crawlerJobParam = BeanUtil.toBean(JSONUtil.parseObj(crawlerJobParamStr),
                    CrawlerJobParam.class,
                    CopyOptions.create().setIgnoreProperties(CrawlerJobParam::getPageParser));
        }

        logger.info("==={}===", JSONUtil.toJsonStr(crawlerJobParam));

        PageParser pageParser = CrawlerGlueFactory.getInstance()
                .loadNewInstance(crawlerJobParam.getPageParserCodeSource(), crawlerJobParam.getUrl(), dealSuccess -> {
                    logger.info("处理结果：{}", dealSuccess);
                });
        crawlerJobParam.setPageParser(pageParser);

        String driverPath = "/Users/laiyouxu/Downloads/chromedriver";

        CrawlerJobParam finalCrawlerJobParam = crawlerJobParam;
        XxlCrawler crawler = new XxlCrawler.Builder()
                .setUrls(crawlerJobParam.getUrl())
                .setWhiteUrlRegexs(crawlerJobParam.getWhiteUrlRegexs())
                .setThreadCount(1)
                .setPageLoader(new SeleniumChromePageLoader(driverPath, webDriver -> {
                    if (CollUtil.isNotEmpty(finalCrawlerJobParam.getCrawlerJobClickPathList())) {
                        finalCrawlerJobParam.getCrawlerJobClickPathList().stream()
                                .sorted(Comparator.comparingInt(CrawlerJobParam.CrawlerJobClickPath::getSorting))
                                .forEach(crawlerJobClickPath -> {
                                    try {
                                        if (ObjectUtil.isNotNull(crawlerJobClickPath.getDelayingBefore())) {
                                            Thread.sleep(crawlerJobClickPath.getDelayingBefore());
                                        }
                                        if (StrUtil.isNotBlank(crawlerJobClickPath.getCssSelector())) {
                                            webDriver.findElement(By.cssSelector(crawlerJobClickPath.getCssSelector())).click();
                                        }
                                        if (ObjectUtil.isNotNull(crawlerJobClickPath.getDelayingAfter())) {
                                            Thread.sleep(crawlerJobClickPath.getDelayingAfter());
                                        }
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }
                }))
                .setPageParser(crawlerJobParam.getPageParser())
                .build();

        System.out.println("start");
        crawler.start(true);
        System.out.println("end");
    }

    /**
     * 分布式爬虫结合定时任务的参数
     *
     * @author Supreme
     * @date 2022/12/25
     */
    @Data
    public static class CrawlerJobParam {
        /**
         * 爬取目标url
         */
        private String url = "https://gitee.com/xuxueli0323/projects?page=1";

        /**
         * 爬取目标白名单url
         */
        private String whiteUrlRegexs = "https://gitee\\.com/xuxueli0323/projects\\?page=\\d+";

        /**
         * 爬虫点击事件
         */
        private List<CrawlerJobClickPath> crawlerJobClickPathList = ListUtil.of(new CrawlerJobClickPath());

        /**
         * 页面处理器
         */
        private PageParser pageParser;

        /**
         * 页面处理器 源码字符串
         */
        private String pageParserCodeSource =
                "package com.xxl.job.executor.service.jobhandler;\n" +
                "import com.xxl.crawler.parser.strategy.AbstractLoginPageParser;\n" +
                "import com.xuxueli.crawler.annotation.PageFieldSelect;\n" +
                "import com.xuxueli.crawler.annotation.PageSelect;\n" +
                "import org.jsoup.nodes.Document;\n" +
                "import org.jsoup.nodes.Element;\n" +
                "import java.util.function.Consumer;\n" +
                "public class TestPageParser extends AbstractLoginPageParser<TestPageVo> {\n" +
                "    /**\n" +
                "     * AbstractLoginPageParser\n" +
                "     *\n" +
                "     * @param redirectUrl\n" +
                "     * @param consumer\n" +
                "     */\n" +
                "    public TestPageParser(String redirectUrl, Consumer<Boolean> consumer) {\n" +
                "        super(redirectUrl, consumer);\n" +
                "    }\n" +
                "    @Override\n" +
                "    public Boolean parse(TestPageVo pageVo) {\n" +
                "        System.out.println(\"=======：\" + pageVo.toString());\n" +
                "        return Boolean.TRUE;\n" +
                "    }\n" +
                "}\n" +

                "@PageSelect(cssQuery = \"#search-projects-ulist .project\")\n" +
                "public class TestPageVo {\n" +
                "    @PageFieldSelect(cssQuery = \".repository\")\n" +
                "    private String repository;\n" +
                "    @PageFieldSelect(cssQuery = \".description\")\n" +
                "    private String description;\n" +
                "    public String getRepository() {\n" +
                "        return repository;\n" +
                "    }\n" +
                "    public void setRepository(String repository) {\n" +
                "        this.repository = repository;\n" +
                "    }\n" +
                "    public String getDescription() {\n" +
                "        return description;\n" +
                "    }\n" +
                "    public void setDescription(String description) {\n" +
                "        this.description = description;\n" +
                "    }\n" +
                "    @Override\n" +
                "    public String toString() {\n" +
                "        return \"PageVo{\" +\n" +
                "                \"repository='\" + repository + '\\'' +\n" +
                "                \", description='\" + description + '\\'' +\n" +
                "                '}';\n" +
                "    }\n" +
                "}"
                ;

        /**
         * 爬虫点击事件
         *
         * @author Supreme
         * @date 2022/12/25
         */
        @Data
        public static class CrawlerJobClickPath {
            /**
             * css选择器
             */
            private String cssSelector = "#search-projects-ulist";

            /**
             * 点击前延迟（毫秒）
             */
            private Integer delayingBefore = 300;

            /**
             * 点击后延迟（毫秒）
             */
            private Integer delayingAfter = 300;

            /**
             * 排序，按顺序点击，数值小的在前
             */
            private Integer sorting = 1;
        }
    }
}

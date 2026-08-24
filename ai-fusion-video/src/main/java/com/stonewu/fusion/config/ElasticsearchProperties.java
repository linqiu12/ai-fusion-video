package com.stonewu.fusion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 融光小说检索使用的 Elasticsearch 连接参数。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.search.elasticsearch")
public class ElasticsearchProperties {

    private boolean enabled = true;
    private String endpoint = "http://127.0.0.1:49200";
    private String novelIndex = "rongguang-novel-chunk-v1";
    private Duration connectTimeout = Duration.ofSeconds(3);
    private Duration requestTimeout = Duration.ofSeconds(10);
}

package com.stonewu.fusion.config;

import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.function.Function;

/**
 * 本机运行时，Redis Cluster 容器会将节点公告为 host.docker.internal。
 * 将该地址映射回宿主机端口，避免本机 JVM 连接 Docker 网关时被提前关闭。
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalRedisClusterConfiguration {

    private static final String DOCKER_HOST = "host.docker.internal";

    @Bean(destroyMethod = "shutdown")
    public ClientResources redisClientResources() {
        return DefaultClientResources.builder()
                .socketAddressResolver(MappingSocketAddressResolver.create((Function<HostAndPort, HostAndPort>) hostAndPort -> {
                    if (!DOCKER_HOST.equals(hostAndPort.getHostText())) {
                        return hostAndPort;
                    }
                    return HostAndPort.of("localhost", hostAndPort.getPort());
                }))
                .build();
    }
}

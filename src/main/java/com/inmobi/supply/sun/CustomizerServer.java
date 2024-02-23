//package com.inmobi.supply.sun;
//
//import io.netty.buffer.PooledByteBufAllocator;
//import io.netty.channel.ChannelOption;
//import io.netty.channel.MultithreadEventLoopGroup;
//import io.netty.channel.epoll.EpollEventLoopGroup;
//import io.netty.channel.epoll.EpollServerSocketChannel;
//import io.netty.channel.nio.NioEventLoopGroup;
//import io.netty.channel.socket.nio.NioServerSocketChannel;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
//import org.springframework.boot.web.embedded.netty.NettyServerCustomizer;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import reactor.core.scheduler.Scheduler;
//import reactor.core.scheduler.Schedulers;
//import reactor.netty.http.server.HttpServer;
//
///**
// * custom netty server
// * @author remi.li
// */
//@Configuration
//@Slf4j
//@SuppressWarnings("unchecked")
//public class CustomizerServer {
//    @Bean
//    public Scheduler getScheduler() {
//        return Schedulers.newBoundedElastic(2000, 500, "sun-process-pool");
//    }
//
//    @Bean
//    public Scheduler getSendScheduler() {
//        return Schedulers.newBoundedElastic(3500, 500, "sun-request-pool");
//    }
//
//    @Bean
//    public Scheduler getNappScheduler() {
//        return Schedulers.newBoundedElastic(2000, 500, "sun-napp-pool");
//    }
//
//    @Bean
//    public NettyReactiveWebServerFactory nettyReactiveWebServerFactory() {
//        NettyReactiveWebServerFactory webServerFactory = new NettyReactiveWebServerFactory();
//        webServerFactory.addServerCustomizers(new EventLoopNettyCustomizer());
//        return webServerFactory;
//    }
//
//    private static class EventLoopNettyCustomizer implements NettyServerCustomizer {
//        @Override
//        public HttpServer apply(HttpServer httpServer) {
//            String osName = System.getProperty("os.name");
//            MultithreadEventLoopGroup parentEventExecutors;
//            MultithreadEventLoopGroup childEventExecutors;
//            Class channelClass;
//            if (osName.startsWith("Mac OS") || osName.startsWith("Windows")) {
//                parentEventExecutors = new NioEventLoopGroup(2);
//                childEventExecutors = new NioEventLoopGroup(32);
//                channelClass = NioServerSocketChannel.class;
//            } else {
//                parentEventExecutors = new EpollEventLoopGroup(2);
//                childEventExecutors = new EpollEventLoopGroup(32);
//                channelClass = EpollServerSocketChannel.class;
//            }
//            MultithreadEventLoopGroup finalParentEventExecutors = parentEventExecutors;
//            MultithreadEventLoopGroup finalChildEventExecutors = childEventExecutors;
//            Class finalChannel = channelClass;
//            return httpServer.tcpConfiguration(tcpServer ->
//                    tcpServer.bootstrap(serverBootstrap ->
//                                    serverBootstrap
//                                            .group(finalParentEventExecutors, finalChildEventExecutors)
//                                            .channel(finalChannel)
//                                            .childOption(ChannelOption.SO_LINGER,null)
//                                            .childOption(ChannelOption.SO_KEEPALIVE, true)
//                                            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
//                                            .option(ChannelOption.SO_BACKLOG, 8192)));
//        }
//    }
//}
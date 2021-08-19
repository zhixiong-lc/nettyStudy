/*
 * Copyright 2013-2018 Lilinfeng.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nettyexample.demo.server;

import com.nettyexample.demo.codec.NettyMessageDecoder;
import com.nettyexample.demo.codec.NettyMessageEncoder;
import com.nettyexample.demo.domain.NettyConstant;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;

public class NettyServer {

    private static final Log LOG = LogFactory.getLog(NettyServer.class);

    public static void main(String[] args) throws Exception {

        new NettyServer().bind();
        System.out.println("----------");
    }

    public void bind() throws Exception {
        // 配置服务端的NIO线程组 --创建反应器轮询组
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        //服务引导类 是一个组装和集成器， 它的职责将不同的 Netty组件装在一起。
        // 此外 ServerBootstrap能够按照应用场景的需要，为组件设置好基础性的参数，
        // 最后 帮助快速 实现 Netty服务器的监听和启动。
        ServerBootstrap b = new ServerBootstrap();

        try{
            b.group(bossGroup, workerGroup)
                    //设置NIO 类型的通道
                    .channel(NioServerSocketChannel.class)
                    //此为TCP传输选项，表示服务器端接收连接的队列长度，如果队列已满，客户端连接将被拒绝
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    //装配子通道流水线
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        //有连接到达时会创建一个通道
                        @Override
                        public void initChannel(SocketChannel ch)
                                throws IOException {
                            // 流水线的职责：负责管理通道中的 Handler处理器

                            // 向“子通道”（传输通道）流水线添加handler处理器
                            //解码处理器
                            ch.pipeline().addLast(
                                    new NettyMessageDecoder(1024 * 1024, 4, 4));
                            //编码处理器
                            ch.pipeline().addLast(new NettyMessageEncoder());

                            //读超时
                            ch.pipeline().addLast("readTimeoutHandler",
                                    new ReadTimeoutHandler(50));

                            //接入认证
                            ch.pipeline().addLast(new LoginAuthRespHandler());

                            //心跳应答
                            ch.pipeline().addLast("HeartBeatHandler",
                                    new HeartBeatRespHandler());
                        }
                    });

            // 绑定端口，同步等待成功
//        b.bind(8120).sync();
            // 开始绑定服务器 // 通过调用 sync同步方法阻塞直到绑定成功
            ChannelFuture channelFuture = b.bind(NettyConstant.REMOTEIP, NettyConstant.PORT).sync();
//        b.bind("127.0.0.1", 8120).sync();

            LOG.info("Netty server start ok : "+ (NettyConstant.REMOTEIP + " : " + NettyConstant.PORT));
            // 7 等待通道关闭的异步任务结束 // 服务监听通道会一直等待通道关闭的异步任务结束
            ChannelFuture closeFuture =channelFuture.channel().closeFuture();
            closeFuture.sync();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            // 8优雅关闭 EventLoopGroup
            // 释放掉所有资源包括创建的线程
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }

    }
}

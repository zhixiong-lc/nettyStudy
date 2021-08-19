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
package com.nettyexample.demo.client;

import com.nettyexample.demo.codec.NettyMessageDecoder;
import com.nettyexample.demo.codec.NettyMessageEncoder;
import com.nettyexample.demo.domain.NettyConstant;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Lilinfeng
 * @version 1.0
 * @date 2014年3月15日
 */
public class NettyClient {

    private static final Log LOG = LogFactory.getLog(NettyClient.class);

    EventLoopGroup group = new NioEventLoopGroup();
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        new NettyClient().connect(NettyConstant.PORT, NettyConstant.REMOTEIP);
    }

    public void connect(int port, String host) throws Exception {
        // 配置客户端NIO线程组
        try {
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch)
                                throws Exception {
                            //为了防止由于单条消息过大导致的内存溢出或者畸形码流导致解码错位引起内存分配失败，
                            // 我们对单条消息最大长度进行了上限限制。
                            ch.pipeline().addLast(
                                    new NettyMessageDecoder(1024 * 1024, 4, 4));
                            ch.pipeline().addLast("MessageEncoder",
                                    new NettyMessageEncoder());
                            //超时handler
                            ch.pipeline().addLast("readTimeoutHandler",
                                    new ReadTimeoutHandler(50));
                            //握手请求handler
                            ch.pipeline().addLast("LoginAuthHandler",
                                    new LoginAuthReqHandler());
                            //心跳handler
                            ch.pipeline().addLast("HeartBeatHandler",
                                    new HeartBeatReqHandler());
                        }
                    });
            // 发起异步连接操作
            ChannelFuture future = b.connect(
                    new InetSocketAddress(host, port),
                    //我们绑定了本地端口 ， 主要用于服务端重复登录保护 ，
                    // 另外， 从产品管理角度看 ， 一般情况下不允许系统随便使用随机端口
                    new InetSocketAddress(NettyConstant.LOCALIP,NettyConstant.LOCAL_PORT))
                    .sync();
//            ChannelFuture future = b.connect(
//                    new InetSocketAddress("127.0.0.1", 8120))
//                    .sync();

            // 当对应的channel关闭的时候，就会返回对应的channel。
            // Returns the ChannelFuture which will be notified when this channel is closed. This method always returns the same future instance.
            future.channel().closeFuture().sync();
            System.out.println("------client------");
        } finally {
            // 所有资源释放完成之后，清空资源，再次发起重连操作
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                        try {
                            connect(NettyConstant.PORT, NettyConstant.REMOTEIP);// 发起重连操作
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

}

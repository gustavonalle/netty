/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.example.http2.helloworld.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An HTTP2 client that allows you to send HTTP2 frames to a server. Inbound and outbound frames are
 * logged. When run from the command-line, sends a single HEADERS frame to the server and gets back
 * a "Hello World" response.
 */
public final class Http2Client {
    private final Http2ClientInitializer initializer;
    private final boolean priorKnowledge;
    private Channel channel;
    private AtomicInteger streamId = new AtomicInteger(1);
    private final EventLoopGroup workerGroup;

    public Http2Client(String host, int port, boolean enableSSL, boolean priorKnowledge) throws Exception {
        this.priorKnowledge = priorKnowledge;
        // Configure SSL.
        final SslContext sslCtx;
        if (enableSSL) {
            SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
            sslCtx = SslContextBuilder.forClient()
                    .sslProvider(provider)
                    /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                     * Please refer to the HTTP/2 specification for cipher requirements. */
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .applicationProtocolConfig(new ApplicationProtocolConfig(
                            Protocol.ALPN,
                            // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                            SelectorFailureBehavior.NO_ADVERTISE,
                            // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                            SelectedListenerFailureBehavior.ACCEPT,
                            ApplicationProtocolNames.HTTP_2,
                            ApplicationProtocolNames.HTTP_1_1))
                    .build();
        } else {
            sslCtx = null;
        }

        workerGroup = new NioEventLoopGroup();
        initializer = new Http2ClientInitializer(sslCtx, Integer.MAX_VALUE, priorKnowledge);

        // Configure the client.
        Bootstrap b = new Bootstrap();
        b.group(workerGroup);
        b.channel(NioSocketChannel.class);
        b.option(ChannelOption.SO_KEEPALIVE, true);
        b.remoteAddress(host, port);
        b.handler(initializer);
        channel = b.connect().syncUninterruptibly().channel();
    }

    public static String getResponseBody(FullHttpResponse response) {
        ByteBuf content = response.content();
        if (content.isReadable()) {
            int contentLength = content.readableBytes();
            byte[] arr = new byte[contentLength];
            content.readBytes(arr);
            return new String(arr, 0, contentLength, CharsetUtil.UTF_8);
        }
        return null;
    }

    private volatile boolean handshake;

    public CompletionStage<FullHttpResponse> sendRequest(FullHttpRequest request) throws Exception {
        HttpResponseHandler responseHandler = initializer.responseHandler();
        if (!handshake && !priorKnowledge) {
            synchronized (this) {
                if (!handshake) {
                    int andAdd = streamId.getAndAdd(2);
                    CompletionStage<FullHttpResponse> resp = resp(request, responseHandler, andAdd);
                    FullHttpResponse fullHttpResponse = resp.toCompletableFuture().get(5, TimeUnit.SECONDS);
                    handshake = true;
                    return CompletableFuture.completedFuture(fullHttpResponse);
                }
            }
        }
        return resp(request, responseHandler, streamId.getAndAdd(2));
    }

    private CompletionStage<FullHttpResponse> resp(FullHttpRequest request, HttpResponseHandler responseHandler, int andAdd) {
        request.headers().set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), andAdd);
        CompletableFuture<FullHttpResponse> promise = new CompletableFuture<>();
        responseHandler.registerRequest(andAdd, promise);
        channel.writeAndFlush(request);
        return promise;
    }

    public void shutdown() {
        workerGroup.shutdownGracefully();
    }
}

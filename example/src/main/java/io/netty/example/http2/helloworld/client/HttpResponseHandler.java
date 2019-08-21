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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http2.HttpConversionUtil;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process {@link io.netty.handler.codec.http.FullHttpResponse} translated from HTTP/2 frames
 */
public class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private final Map<Integer, CompletableFuture<FullHttpResponse>> responseMap = new ConcurrentHashMap<>();

    void registerRequest(int streamId, CompletableFuture<FullHttpResponse> promise) {
        responseMap.put(streamId, promise);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        Integer streamId = msg.headers().getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text());
        if (streamId == null) {
            System.err.println("HttpResponseHandler unexpected message received: " + msg);
            return;
        }
        CompletableFuture<FullHttpResponse> future = responseMap.remove(streamId);
        if (future == null) {
            System.err.println("Message received for unknown stream id " + streamId);
        } else {
            future.complete(msg.retain());
        }
    }
}

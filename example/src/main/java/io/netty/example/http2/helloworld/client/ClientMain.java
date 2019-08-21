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

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpScheme;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Main class to run the sample HTTP/2 client
 */
public class ClientMain {
    /**
     * Enable SSL, the server must be launched with the -ssl flag as well
     */
    private static final boolean SSL = System.getProperty("ssl") != null;
    /**
     * Host and Port to connect to the server. Each client uses a dedicated connection for all requests
     */
    private static final String HOST = System.getProperty("host", "127.0.0.1");
    private static final int PORT = Integer.parseInt(System.getProperty("port", SSL ? "8443" : "8080"));

    /**
     * The URL to be called in the server
     */
    private static final String URL = System.getProperty("url", "/whatever");

    /**
     * If provided, will execute a POST request instead of a GET
     */
    private static final String POST_DATA = System.getProperty("data");

    /**
     * Number of requests to perform. If more than 1, will execute multiple requests in parallel
     */
    private static final int NUM_REQUEST = Integer.parseInt(System.getProperty("threads", "1"));

    /**
     * If set, will bypass server upgrades and send HTTP/2 frames directly. When SSL is enabled this setting is
     * ignored since SSL must perform a protocol upgrade
     */
    private static final boolean PRIOR_KNOWLEDGE = System.getProperty("pri") != null;

    public static void main(String[] args) throws Exception {

        final Http2Client http2Client = new Http2Client(HOST, PORT, SSL, PRIOR_KNOWLEDGE);

        final HttpScheme scheme = SSL ? HttpScheme.HTTPS : HttpScheme.HTTP;
        final AsciiString hostName = new AsciiString(HOST + ':' + PORT);
        System.err.println("Sending request(s)...");
        if (URL != null) {
            List<CompletableFuture<FullHttpResponse>> responses = new ArrayList<>();
            for (int i = 0; i < NUM_REQUEST; i++) {
                System.out.println("Invoking request");
                FullHttpRequest request;
                if (POST_DATA == null) {
                    request = new DefaultFullHttpRequest(HTTP_1_1, GET, URL, Unpooled.EMPTY_BUFFER);
                } else {
                    request = new DefaultFullHttpRequest(HTTP_1_1, POST, URL, wrappedBuffer(POST_DATA.getBytes(CharsetUtil.UTF_8)));
                }
                request.headers().add(HttpHeaderNames.HOST, hostName);
                request.headers().add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme.name());
                request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);
                request.headers().add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE);
                responses.add(http2Client.sendRequest(request).toCompletableFuture());

            }
            responses.stream().map(f -> {
                try {
                    FullHttpResponse fullHttpResponse = f.get(5, TimeUnit.SECONDS);
                    return Http2Client.getResponseBody(fullHttpResponse);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    e.printStackTrace();
                }
                return null;
            }).forEach(System.out::println);
        }

        http2Client.shutdown();
    }

}

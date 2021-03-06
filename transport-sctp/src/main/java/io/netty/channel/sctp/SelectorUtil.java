/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.sctp;

import io.netty.logging.InternalLogger;
import io.netty.logging.InternalLoggerFactory;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.Selector;

final class SelectorUtil {
    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SelectorUtil.class);

    static final int DEFAULT_IO_THREADS = Runtime.getRuntime().availableProcessors() * 2;

    static void select(Selector selector) throws IOException {
        try {
            selector.select(10); // does small timeout give more throughput + less CPU usage?
        } catch (CancelledKeyException e) {
            // Harmless exception - log anyway
            logger.debug(
                    CancelledKeyException.class.getSimpleName() +
                    " raised by a Selector - JDK bug?", e);
        }
    }

    private SelectorUtil() {
        // Unused
    }
}

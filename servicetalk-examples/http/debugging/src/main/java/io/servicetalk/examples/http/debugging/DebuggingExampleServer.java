/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.examples.http.debugging;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.HttpServerBuilder;
import io.servicetalk.http.netty.H2ProtocolConfigBuilder;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.http.netty.HttpServers;
import io.servicetalk.logging.api.LogLevel;

import java.util.function.BooleanSupplier;

import static io.servicetalk.concurrent.api.Single.succeeded;
import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.logging.api.LogLevel.TRACE;

/**
 * Extends the async "Hello World" sample with debugging features. Four debugging features are demonstrated:
 * <ol>
 *     <li>Disabling {@link AsyncContext}</li>
 *     <li>Disabling {@link HttpExecutionStrategy offloading}</li>
 *     <li>Enabling {@link HttpServerBuilder#enableWireLogging(String, LogLevel, BooleanSupplier) HTTP wire logging}</li>
 *     <li>Enabling {@link H2ProtocolConfigBuilder#enableFrameLogging(String, LogLevel, BooleanSupplier) HTTP/2 frame logging}</li>
 * </ol>
 * <p>The wire and frame logging features require that you configure a logger with an appropriate log level. For this
 * example {@code log4j2.xml} is used by both the client and server and configures the
 * ({@code servicetalk-examples-wire-logger} logger at {@link io.servicetalk.logging.api.LogLevel#TRACE TRACE} level.
 *
 * <p>When configured correctly the output should be similar to the following:
 * <pre>
 * 2021-03-26 19:42:07,095 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] OUTBOUND SETTINGS: ack=false settings={MAX_HEADER_LIST_SIZE=8192}
 * 2021-03-26 19:42:07,117 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] WRITE: 15B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 06 04 00 00 00 00 00 00 06 00 00 20 00    |............. . |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,119 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] REGISTERED
 * 2021-03-26 19:42:07,120 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] ACTIVE
 * 2021-03-26 19:42:07,120 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] READ_REQUEST
 * 2021-03-26 19:42:07,184 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] READ: 134B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 50 52 49 20 2a 20 48 54 54 50 2f 32 2e 30 0d 0a |PRI * HTTP/2.0..|
 * |00000010| 0d 0a 53 4d 0d 0a 0d 0a 00 00 12 04 00 00 00 00 |..SM............|
 * |00000020| 00 00 02 00 00 00 00 00 03 00 00 00 00 00 06 00 |................|
 * |00000030| 00 20 00 00 00 3b 01 04 00 00 00 03 41 0e 6c 6f |. ...;......A.lo|
 * |00000040| 63 61 6c 68 6f 73 74 3a 38 30 38 30 83 86 44 09 |calhost:8080..D.|
 * |00000050| 2f 73 61 79 48 65 6c 6c 6f 5f 19 74 65 78 74 2f |/sayHello_.text/|
 * |00000060| 70 6c 61 69 6e 3b 20 63 68 61 72 73 65 74 3d 55 |plain; charset=U|
 * |00000070| 54 46 2d 38 5c 01 36 00 00 06 00 01 00 00 00 03 |TF-8\.6.........|
 * |00000080| 47 65 6f 72 67 65                               |George          |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,186 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] INBOUND SETTINGS: ack=false settings={ENABLE_PUSH=0, MAX_CONCURRENT_STREAMS=0, MAX_HEADER_LIST_SIZE=8192}
 * 2021-03-26 19:42:07,187 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] OUTBOUND SETTINGS: ack=true
 * 2021-03-26 19:42:07,188 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] WRITE: 9B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 00 04 01 00 00 00 00                      |.........       |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,196 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] INBOUND HEADERS: streamId=3 headers=DefaultHttp2Headers[:authority: localhost:8080, :method: POST, :scheme: http, :path: /sayHello, content-type: text/plain; charset=UTF-8, content-length: 6] padding=0 endStream=false
 * 2021-03-26 19:42:07,254 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] INBOUND DATA: streamId=3 padding=0 endStream=true length=6 bytes=47656f726765
 * 2021-03-26 19:42:07,350 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] OUTBOUND HEADERS: streamId=3 headers=DefaultHttp2Headers[:status: 200, content-type: text/plain; charset=UTF-8, content-length: 13] padding=0 endStream=false
 * 2021-03-26 19:42:07,351 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] WRITE: 9B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 20 01 04 00 00 00 03                      |.. ......       |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,351 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] WRITE: 32B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 88 5f 19 74 65 78 74 2f 70 6c 61 69 6e 3b 20 63 |._.text/plain; c|
 * |00000010| 68 61 72 73 65 74 3d 55 54 46 2d 38 5c 02 31 33 |harset=UTF-8\.13|
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,360 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] READ_COMPLETE
 * 2021-03-26 19:42:07,362 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] OUTBOUND DATA: streamId=3 padding=0 endStream=true length=13 bytes=48656c6c6f2047656f72676521
 * 2021-03-26 19:42:07,362 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] WRITE: 9B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 0d 00 01 00 00 00 03                      |.........       |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,363 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] WRITE: 13B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 48 65 6c 6c 6f 20 47 65 6f 72 67 65 21          |Hello George!   |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,364 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] FLUSH
 * 2021-03-26 19:42:07,364 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] FLUSH
 * 2021-03-26 19:42:07,364 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] FLUSH
 * 2021-03-26 19:42:07,365 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] READ_REQUEST
 * 2021-03-26 19:42:07,370 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] READ: 9B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 00 04 01 00 00 00 00                      |.........       |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,370 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] INBOUND SETTINGS: ack=true
 * 2021-03-26 19:42:07,371 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] READ_COMPLETE
 * 2021-03-26 19:42:07,371 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] FLUSH
 * 2021-03-26 19:42:07,371 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 - R:/127.0.0.1:57266] READ_REQUEST
 * 2021-03-26 19:42:07,396 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 ! R:/127.0.0.1:57266] INACTIVE
 * 2021-03-26 19:42:07,396 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xfdbee21a, L:/127.0.0.1:8080 ! R:/127.0.0.1:57266] UNREGISTERED
 * </pre>
 */
public final class DebuggingExampleServer {

    static {
        /*
         * 1. (optional) Disables the AsyncContext associated with individual request/responses to reduce stack-trace
         * depth and simplify execution tracing. This will disable/break some features such as request tracing,
         * authentication, propagated timeouts, etc. that rely upon the AsyncContext so should only be disabled when
         * necessary for debugging:
         */
        // AsyncContext.disable();
    }

    public static void main(String... args) throws Exception {
        HttpServers.forPort(8080)
                /*
                 * 2. Disables most asynchronous offloading to simplify execution tracing. Changing this may
                 * significantly change application behavior and introduce unexpected blocking. It is most useful for
                 * being able to directly trace through situations that would normally involve a thread handoff.
                 */
                // .executionStrategy(HttpExecutionStrategies.noOffloadsStrategy())

                /*
                 * 3. Enables detailed logging of I/O and I/O states.
                 * Be sure to also enable the logger in your logging config file (log4j2.xml for this example).
                 */
                .enableWireLogging("servicetalk-examples-wire-logger", TRACE, Boolean.TRUE::booleanValue)

                /*
                 * 4. Enables detailed logging of HTTP2 frames.
                 * Be sure to also enable the logger in your logging config file (log4j2.xml for this example).
                 */
                .protocols(HttpProtocolConfigs.h2()
                        .enableFrameLogging("servicetalk-examples-h2-frame-logger", TRACE, Boolean.TRUE::booleanValue)
                        .build())
                .listenAndAwait((ctx, request, responseFactory) -> {
                    String who = request.payloadBody(textSerializerUtf8());
                    return succeeded(responseFactory.ok().payloadBody("Hello " + who + "!", textSerializerUtf8()));
                })
                .awaitShutdown();
    }
}

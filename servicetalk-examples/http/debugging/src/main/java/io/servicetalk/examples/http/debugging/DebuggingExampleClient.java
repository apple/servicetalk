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
import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.netty.H2ProtocolConfigBuilder;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.logging.api.LogLevel;

import java.util.function.BooleanSupplier;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.logging.api.LogLevel.TRACE;

/**
 * The async "Hello World" example with debugging features enabled. Four debugging features are demonstrated:
 * <ol>
 *     <li>Disabling {@link AsyncContext}</li>
 *     <li>Disabling {@link HttpExecutionStrategy offloading}</li>
 *     <li>Enabling {@link SingleAddressHttpClientBuilder#enableWireLogging(String, LogLevel, BooleanSupplier) HTTP wire logging}</li>
 *     <li>Enabling {@link H2ProtocolConfigBuilder#enableFrameLogging(String, LogLevel, BooleanSupplier) HTTP/2 frame logging}</li>
 * </ol>
 * <p>The wire and frame logging features require that you configure a logger with an appropriate log level. For this
 * example {@code log4j2.xml} is used by both the client and server and configures the
 * ({@code servicetalk-examples-wire-logger} logger at {@link io.servicetalk.logging.api.LogLevel#TRACE TRACE} level.
 *
 * <p>When configured correctly the output should be similar to the following:
 * <pre>
 * 2021-03-26 19:42:07,000 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09] REGISTERED
 * 2021-03-26 19:42:07,002 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09] CONNECT: localhost/127.0.0.1:8080
 * 2021-03-26 19:42:07,005 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] ACTIVE
 * 2021-03-26 19:42:07,006 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] WRITE: 24B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 50 52 49 20 2a 20 48 54 54 50 2f 32 2e 30 0d 0a |PRI * HTTP/2.0..|
 * |00000010| 0d 0a 53 4d 0d 0a 0d 0a                         |..SM....        |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,013 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] OUTBOUND SETTINGS: ack=false settings={ENABLE_PUSH=0, MAX_CONCURRENT_STREAMS=0, MAX_HEADER_LIST_SIZE=8192}
 * 2021-03-26 19:42:07,027 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] WRITE: 27B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 12 04 00 00 00 00 00 00 02 00 00 00 00 00 |................|
 * |00000010| 03 00 00 00 00 00 06 00 00 20 00                |......... .     |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,166 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] OUTBOUND HEADERS: streamId=3 headers=DefaultHttp2Headers[:authority: localhost:8080, :method: POST, :scheme: http, :path: /sayHello, content-type: text/plain; charset=UTF-8, content-length: 6] padding=0 endStream=false
 * 2021-03-26 19:42:07,171 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] WRITE: 9B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 3b 01 04 00 00 00 03                      |..;......       |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,171 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] WRITE: 59B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 41 0e 6c 6f 63 61 6c 68 6f 73 74 3a 38 30 38 30 |A.localhost:8080|
 * |00000010| 83 86 44 09 2f 73 61 79 48 65 6c 6c 6f 5f 19 74 |..D./sayHello_.t|
 * |00000020| 65 78 74 2f 70 6c 61 69 6e 3b 20 63 68 61 72 73 |ext/plain; chars|
 * |00000030| 65 74 3d 55 54 46 2d 38 5c 01 36                |et=UTF-8\.6     |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,181 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] OUTBOUND DATA: streamId=3 padding=0 endStream=true length=6 bytes=47656f726765
 * 2021-03-26 19:42:07,181 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] WRITE: 9B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 06 00 01 00 00 00 03                      |.........       |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,181 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] WRITE: 6B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 47 65 6f 72 67 65                               |George          |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,183 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] FLUSH
 * 2021-03-26 19:42:07,185 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * 2021-03-26 19:42:07,365 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] READ: 87B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 06 04 00 00 00 00 00 00 06 00 00 20 00 00 |............. ..|
 * |00000010| 00 00 04 01 00 00 00 00 00 00 20 01 04 00 00 00 |.......... .....|
 * |00000020| 03 88 5f 19 74 65 78 74 2f 70 6c 61 69 6e 3b 20 |.._.text/plain; |
 * |00000030| 63 68 61 72 73 65 74 3d 55 54 46 2d 38 5c 02 31 |charset=UTF-8\.1|
 * |00000040| 33 00 00 0d 00 01 00 00 00 03 48 65 6c 6c 6f 20 |3.........Hello |
 * |00000050| 47 65 6f 72 67 65 21                            |George!         |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,367 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] INBOUND SETTINGS: ack=false settings={MAX_HEADER_LIST_SIZE=8192}
 * 2021-03-26 19:42:07,369 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] OUTBOUND SETTINGS: ack=true
 * 2021-03-26 19:42:07,370 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] WRITE: 9B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 00 04 01 00 00 00 00                      |.........       |
 * +--------+-------------------------------------------------+----------------+
 * 2021-03-26 19:42:07,370 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] FLUSH
 * 2021-03-26 19:42:07,370 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] INBOUND SETTINGS: ack=true
 * 2021-03-26 19:42:07,371 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] INBOUND HEADERS: streamId=3 headers=DefaultHttp2Headers[:status: 200, content-type: text/plain; charset=UTF-8, content-length: 13] padding=0 endStream=false
 * 2021-03-26 19:42:07,378 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] INBOUND DATA: streamId=3 padding=0 endStream=true length=13 bytes=48656c6c6f2047656f72676521
 * HTTP/2.0 200 OK
 * NettyH2HeadersToHttpHeaders[content-type: text/plain; charset=UTF-8
 * content-length: 13]
 * Hello George!
 * 2021-03-26 19:42:07,381 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] READ_COMPLETE
 * 2021-03-26 19:42:07,381 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] FLUSH
 * 2021-03-26 19:42:07,381 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] FLUSH
 * 2021-03-26 19:42:07,381 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * 2021-03-26 19:42:07,394 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 - R:localhost/127.0.0.1:8080] CLOSE
 * 2021-03-26 19:42:07,394 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 ! R:localhost/127.0.0.1:8080] INACTIVE
 * 2021-03-26 19:42:07,395 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xf11baf09, L:/127.0.0.1:57266 ! R:localhost/127.0.0.1:8080] UNREGISTERED
 * </pre>
 */
public final class DebuggingExampleClient {

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
        try (HttpClient client = HttpClients.forSingleAddress("localhost", 8080)
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
                .build()) {
            client.request(client.post("/sayHello").payloadBody("George", textSerializerUtf8()))
                    .whenOnSuccess(resp -> {
                        System.out.println(resp.toString((name, value) -> value));
                        System.out.println(resp.payloadBody(textSerializerUtf8()));
                    })
            // This example is demonstrating asynchronous execution, but needs to prevent the main thread from exiting
            // before the response has been processed. This isn't typical usage for an asynchronous API but is useful
            // for demonstration purposes.
                    .toFuture().get();
        }
    }
}

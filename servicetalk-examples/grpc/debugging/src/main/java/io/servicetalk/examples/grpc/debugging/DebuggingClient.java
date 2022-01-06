/*
 * Copyright © 2022 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.examples.grpc.debugging;

import io.servicetalk.concurrent.api.AsyncContext;
import io.servicetalk.grpc.netty.GrpcClients;
import io.servicetalk.http.api.HttpExecutionStrategy;
import io.servicetalk.http.api.SingleAddressHttpClientBuilder;
import io.servicetalk.http.netty.H2ProtocolConfigBuilder;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.logging.api.LogLevel;

import io.grpc.examples.debugging.Greeter.BlockingGreeterClient;
import io.grpc.examples.debugging.Greeter.ClientFactory;
import io.grpc.examples.debugging.HelloReply;
import io.grpc.examples.debugging.HelloRequest;

import java.util.function.BooleanSupplier;

import static io.servicetalk.logging.api.LogLevel.TRACE;

/**
 * The blocking "Hello World" example with debugging features enabled. Five debugging features are demonstrated:
 * <ol>
 *     <li>Disabling {@link AsyncContext}</li>
 *     <li>Disabling {@link HttpExecutionStrategy offloading}</li>
 *     <li>Enabling {@link SingleAddressHttpClientBuilder#enableWireLogging(String, LogLevel, BooleanSupplier) HTTP wire logging}</li>
 *     <li>Enabling {@link H2ProtocolConfigBuilder#enableFrameLogging(String, LogLevel, BooleanSupplier) HTTP/2 frame logging}</li>
 *     <li>Enabling additional logger verbosity in the {@code log4j2.xml} configuration file</li>
 * </ol>
 * <p>The wire and frame logging features require that you configure a logger with an appropriate log level. For this
 * example {@code log4j2.xml} is used by both the client and server and configures the
 * ({@code servicetalk-examples-wire-logger} logger at {@link io.servicetalk.logging.api.LogLevel#TRACE TRACE} level.
 *
 * <p>When configured correctly the output should be similar to the following:
 * <pre>
 * 2022-01-06 12:48:50,653                           main [DEBUG] AsyncContext                   - Enabled.
 * 2022-01-06 12:48:51,241 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888] REGISTERED
 * 2022-01-06 12:48:51,241 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888] CONNECT: localhost/127.0.0.1:8080
 * 2022-01-06 12:48:51,247 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] ACTIVE
 * 2022-01-06 12:48:51,248 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] WRITE: 24B
 * +-------------------------------------------------+
 * |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 50 52 49 20 2a 20 48 54 54 50 2f 32 2e 30 0d 0a |PRI * HTTP/2.0..|
 * |00000010| 0d 0a 53 4d 0d 0a 0d 0a                         |..SM....        |
 * +--------+-------------------------------------------------+----------------+
 * 2022-01-06 12:48:51,255 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] OUTBOUND SETTINGS: ack=false settings={ENABLE_PUSH=0, MAX_CONCURRENT_STREAMS=0, MAX_HEADER_LIST_SIZE=8192}
 * 2022-01-06 12:48:51,272 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] WRITE: 27B
 * +-------------------------------------------------+
 * |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 12 04 00 00 00 00 00 00 02 00 00 00 00 00 |................|
 * |00000010| 03 00 00 00 00 00 06 00 00 20 00                |......... .     |
 * +--------+-------------------------------------------------+----------------+
 * 2022-01-06 12:48:51,355 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] OUTBOUND HEADERS: streamId=3 headers=DefaultHttp2Headers[:authority: localhost:8080, :method: POST, :scheme: http, :path: /helloworld.Greeter/SayHello, user-agent: servicetalk-grpc/, te: trailers, content-type: application/grpc+proto, content-length: 12] padding=0 endStream=false
 * 2022-01-06 12:48:51,360 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] WRITE: 9B
 * +-------------------------------------------------+
 * |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 6c 01 04 00 00 00 03                      |..l......       |
 * +--------+-------------------------------------------------+----------------+
 * 2022-01-06 12:48:51,361 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] WRITE: 108B
 * +-------------------------------------------------+
 * |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 41 0e 6c 6f 63 61 6c 68 6f 73 74 3a 38 30 38 30 |A.localhost:8080|
 * |00000010| 83 86 44 1c 2f 68 65 6c 6c 6f 77 6f 72 6c 64 2e |..D./helloworld.|
 * |00000020| 47 72 65 65 74 65 72 2f 53 61 79 48 65 6c 6c 6f |Greeter/SayHello|
 * |00000030| 7a 11 73 65 72 76 69 63 65 74 61 6c 6b 2d 67 72 |z.servicetalk-gr|
 * |00000040| 70 63 2f 40 02 74 65 08 74 72 61 69 6c 65 72 73 |pc/@.te.trailers|
 * |00000050| 5f 16 61 70 70 6c 69 63 61 74 69 6f 6e 2f 67 72 |_.application/gr|
 * |00000060| 70 63 2b 70 72 6f 74 6f 5c 02 31 32             |pc+proto\.12    |
 * +--------+-------------------------------------------------+----------------+
 * 2022-01-06 12:48:51,374 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] OUTBOUND DATA: streamId=3 padding=0 endStream=true length=12 bytes=00000000070a05576f726c64
 * 2022-01-06 12:48:51,374 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] WRITE: 9B
 * +-------------------------------------------------+
 * |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 0c 00 01 00 00 00 03                      |.........       |
 * +--------+-------------------------------------------------+----------------+
 * 2022-01-06 12:48:51,374 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] WRITE: 12B
 * +-------------------------------------------------+
 * |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 00 00 07 0a 05 57 6f 72 6c 64             |.......World    |
 * +--------+-------------------------------------------------+----------------+
 * 2022-01-06 12:48:51,375 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] FLUSH
 * 2022-01-06 12:48:51,378 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * 2022-01-06 12:48:51,486 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] READ: 24B
 * +-------------------------------------------------+
 * |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 06 04 00 00 00 00 00 00 06 00 00 20 00 00 |............. ..|
 * |00000010| 00 00 04 01 00 00 00 00                         |........        |
 * +--------+-------------------------------------------------+----------------+
 * 2022-01-06 12:48:51,489 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] INBOUND SETTINGS: ack=false settings={MAX_HEADER_LIST_SIZE=8192}
 * 2022-01-06 12:48:51,493 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] OUTBOUND SETTINGS: ack=true
 * 2022-01-06 12:48:51,493 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] WRITE: 9B
 * +-------------------------------------------------+
 * |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 00 04 01 00 00 00 00                      |.........       |
 * +--------+-------------------------------------------------+----------------+
 * 2022-01-06 12:48:51,493 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] FLUSH
 * 2022-01-06 12:48:51,493 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] INBOUND SETTINGS: ack=true
 * 2022-01-06 12:48:51,493 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] READ_COMPLETE
 * 2022-01-06 12:48:51,494 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] FLUSH
 * 2022-01-06 12:48:51,494 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * 2022-01-06 12:48:51,552 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] READ: 108B
 * +-------------------------------------------------+
 * |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 30 01 04 00 00 00 03 88 76 11 73 65 72 76 |..0.......v.serv|
 * |00000010| 69 63 65 74 61 6c 6b 2d 67 72 70 63 2f 5f 16 61 |icetalk-grpc/_.a|
 * |00000020| 70 70 6c 69 63 61 74 69 6f 6e 2f 67 72 70 63 2b |pplication/grpc+|
 * |00000030| 70 72 6f 74 6f 5c 02 31 38 00 00 12 00 00 00 00 |proto\.18.......|
 * |00000040| 00 03 00 00 00 00 0d 0a 0b 48 65 6c 6c 6f 20 57 |.........Hello W|
 * |00000050| 6f 72 6c 64 00 00 0f 01 05 00 00 00 03 40 0b 67 |orld.........@.g|
 * |00000060| 72 70 63 2d 73 74 61 74 75 73 01 30             |rpc-status.0    |
 * +--------+-------------------------------------------------+----------------+
 * 2022-01-06 12:48:51,555 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] INBOUND HEADERS: streamId=3 headers=DefaultHttp2Headers[:status: 200, server: servicetalk-grpc/, content-type: application/grpc+proto, content-length: 18] padding=0 endStream=false
 * 2022-01-06 12:48:51,563 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] INBOUND DATA: streamId=3 padding=0 endStream=false length=18 bytes=000000000d0a0b48656c6c6f20576f726c64
 * 2022-01-06 12:48:51,564 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] INBOUND HEADERS: streamId=3 headers=DefaultHttp2Headers[grpc-status: 0] padding=0 endStream=true
 * 2022-01-06 12:48:51,566 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] READ_COMPLETE
 * 2022-01-06 12:48:51,566 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] FLUSH
 * 2022-01-06 12:48:51,566 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] FLUSH
 * 2022-01-06 12:48:51,567 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * message: "Hello World"
 *
 * 2022-01-06 12:48:51,623 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 - R:localhost/127.0.0.1:8080] CLOSE
 * 2022-01-06 12:48:51,624 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 ! R:localhost/127.0.0.1:8080] INACTIVE
 * 2022-01-06 12:48:51,624 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0x35810888, L:/127.0.0.1:55604 ! R:localhost/127.0.0.1:8080] UNREGISTERED
 * </pre>
 */
public final class DebuggingClient {

    static {
        /*
         * 1. (optional) Disables the AsyncContext associated with individual request/responses to reduce stack-trace
         * depth and simplify execution tracing. This will disable/break some features such as request tracing,
         * authentication, propagated timeouts, etc. that rely upon the AsyncContext so should only be disabled when
         * necessary for debugging:
         */
        // AsyncContext.disable();
    }

    public static void main(String[] args) throws Exception {
        try (BlockingGreeterClient client = GrpcClients.forAddress("localhost", 8080)
                .initializeHttp(builder -> builder
                        /*
                         * 2. Disables most asynchronous offloading to simplify execution tracing. Changing this may
                         * significantly change application behavior and introduce unexpected blocking. It is most
                         * useful for being able to directly trace through situations that would normally involve a
                         * thread handoff.
                         */
                        // .executionStrategy(HHttpExecutionStrategies.offloadNever())
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
                                .enableFrameLogging(
                                        "servicetalk-examples-h2-frame-logger", TRACE, Boolean.TRUE::booleanValue)
                                .build()))
                .buildBlocking(new ClientFactory())) {
            HelloReply reply = client.sayHello(HelloRequest.newBuilder().setName("World").build());
            System.out.println(reply);
        }
    }
}

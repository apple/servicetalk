/*
 * Copyright © 2018, 2023 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpClient;
import io.servicetalk.http.netty.HttpClients;
import io.servicetalk.http.netty.HttpProtocolConfigs;
import io.servicetalk.logging.api.LogLevel;
import io.servicetalk.transport.api.HostAndPort;

import java.util.function.BooleanSupplier;

import static io.servicetalk.http.api.HttpSerializers.textSerializerUtf8;
import static io.servicetalk.logging.api.LogLevel.TRACE;

/**
 * The async "Hello World" example with debugging features enabled. Five debugging features are demonstrated:
 * <ol>
 *     <li>Disabling {@link io.servicetalk.concurrent.api.AsyncContext}</li>
 *     <li>Disabling {@link io.servicetalk.http.api.HttpExecutionStrategy offloading}</li>
 *     <li>Enabling {@link io.servicetalk.http.api.SingleAddressHttpClientBuilder#enableWireLogging(String, LogLevel, BooleanSupplier) HTTP wire logging}</li>
 *     <li>Enabling {@link io.servicetalk.http.netty.H2ProtocolConfigBuilder#enableFrameLogging(String, LogLevel, BooleanSupplier) HTTP/2 frame logging}</li>
 *     <li>Enabling additional logger verbosity in the {@code log4j2.xml} configuration file</li>
 * </ol>
 * <p>The wire and frame logging features require that you configure a logger with an appropriate log level. For this
 * example {@code log4j2.xml} is used by both the client and server and configures the
 * {@code servicetalk-examples-wire-logger} and {@code servicetalk-examples-h2-frame-logger} loggers at
 * {@link io.servicetalk.logging.api.LogLevel#TRACE TRACE} level. Wire logging is configured to include logging of all
 * user data contained in the payload, see {@link #LOG_USER_DATA}.
 *
 * <p>When configured correctly the output should be similar to the following:
 * <pre>
 * 2023-08-29 18:02:12,121 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2] REGISTERED
 * 2023-08-29 18:02:12,122 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2] CONNECT: localhost/127.0.0.1:8080
 * 2023-08-29 18:02:12,126 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] ACTIVE
 * 2023-08-29 18:02:12,126 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] WRITE: 24B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 50 52 49 20 2a 20 48 54 54 50 2f 32 2e 30 0d 0a |PRI * HTTP/2.0..|
 * |00000010| 0d 0a 53 4d 0d 0a 0d 0a                         |..SM....        |
 * +--------+-------------------------------------------------+----------------+
 * 2023-08-29 18:02:12,130 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] OUTBOUND SETTINGS: ack=false settings={ENABLE_PUSH=0, MAX_CONCURRENT_STREAMS=0, INITIAL_WINDOW_SIZE=1048576, MAX_HEADER_LIST_SIZE=8192}
 * 2023-08-29 18:02:12,135 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] WRITE: 33B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 18 04 00 00 00 00 00 00 02 00 00 00 00 00 |................|
 * |00000010| 03 00 00 00 00 00 04 00 10 00 00 00 06 00 00 20 |............... |
 * |00000020| 00                                              |.               |
 * +--------+-------------------------------------------------+----------------+
 * 2023-08-29 18:02:12,136 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] OUTBOUND WINDOW_UPDATE: streamId=0 windowSizeIncrement=1966082
 * 2023-08-29 18:02:12,136 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] WRITE: 13B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 04 08 00 00 00 00 00 00 1e 00 02          |.............   |
 * +--------+-------------------------------------------------+----------------+
 * 2023-08-29 18:02:12,136 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] FLUSH
 * 2023-08-29 18:02:12,197 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] OUTBOUND HEADERS: streamId=3 headers=DefaultHttp2Headers[:authority: localhost:8080, :method: POST, :scheme: http, :path: /sayHello, content-length: 6, accept: text/plain, content-type: text/plain; charset=UTF-8] padding=0 endStream=false
 * 2023-08-29 18:02:12,200 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] WRITE: 9B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 47 01 04 00 00 00 03                      |..G......       |
 * +--------+-------------------------------------------------+----------------+
 * 2023-08-29 18:02:12,200 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] WRITE: 71B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 41 0e 6c 6f 63 61 6c 68 6f 73 74 3a 38 30 38 30 |A.localhost:8080|
 * |00000010| 83 86 44 09 2f 73 61 79 48 65 6c 6c 6f 5c 01 36 |..D./sayHello\.6|
 * |00000020| 53 0a 74 65 78 74 2f 70 6c 61 69 6e 5f 19 74 65 |S.text/plain_.te|
 * |00000030| 78 74 2f 70 6c 61 69 6e 3b 20 63 68 61 72 73 65 |xt/plain; charse|
 * |00000040| 74 3d 55 54 46 2d 38                            |t=UTF-8         |
 * +--------+-------------------------------------------------+----------------+
 * 2023-08-29 18:02:12,210 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] OUTBOUND DATA: streamId=3 padding=0 endStream=true length=6 bytes=47656f726765
 * 2023-08-29 18:02:12,210 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] WRITE: 9B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 06 00 01 00 00 00 03                      |.........       |
 * +--------+-------------------------------------------------+----------------+
 * 2023-08-29 18:02:12,211 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] WRITE: 6B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 47 65 6f 72 67 65                               |George          |
 * +--------+-------------------------------------------------+----------------+
 * 2023-08-29 18:02:12,211 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] FLUSH
 * 2023-08-29 18:02:12,213 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * 2023-08-29 18:02:12,297 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] READ: 34B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 0c 04 00 00 00 00 00 00 04 00 10 00 00 00 |................|
 * |00000010| 06 00 00 20 00 00 00 04 08 00 00 00 00 00 00 1e |... ............|
 * |00000020| 00 02                                           |..              |
 * +--------+-------------------------------------------------+----------------+
 * 2023-08-29 18:02:12,299 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] INBOUND SETTINGS: ack=false settings={INITIAL_WINDOW_SIZE=1048576, MAX_HEADER_LIST_SIZE=8192}
 * 2023-08-29 18:02:12,301 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] OUTBOUND SETTINGS: ack=true
 * 2023-08-29 18:02:12,301 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] WRITE: 9B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 00 04 01 00 00 00 00                      |.........       |
 * +--------+-------------------------------------------------+----------------+
 * 2023-08-29 18:02:12,301 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] FLUSH
 * 2023-08-29 18:02:12,301 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] INBOUND WINDOW_UPDATE: streamId=0 windowSizeIncrement=1966082
 * 2023-08-29 18:02:12,301 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] READ_COMPLETE
 * 2023-08-29 18:02:12,301 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] FLUSH
 * 2023-08-29 18:02:12,301 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * 2023-08-29 18:02:12,409 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] READ: 9B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 00 04 01 00 00 00 00                      |.........       |
 * +--------+-------------------------------------------------+----------------+
 * 2023-08-29 18:02:12,410 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] INBOUND SETTINGS: ack=true
 * 2023-08-29 18:02:12,411 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] READ_COMPLETE
 * 2023-08-29 18:02:12,411 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] FLUSH
 * 2023-08-29 18:02:12,411 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * 2023-08-29 18:02:12,492 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] READ: 75B
 *          +-------------------------------------------------+
 *          |  0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f |
 * +--------+-------------------------------------------------+----------------+
 * |00000000| 00 00 2c 01 04 00 00 00 03 88 5f 19 74 65 78 74 |..,......._.text|
 * |00000010| 2f 70 6c 61 69 6e 3b 20 63 68 61 72 73 65 74 3d |/plain; charset=|
 * |00000020| 55 54 46 2d 38 53 0a 74 65 78 74 2f 70 6c 61 69 |UTF-8S.text/plai|
 * |00000030| 6e 5c 02 31 33 00 00 0d 00 01 00 00 00 03 48 65 |n\.13.........He|
 * |00000040| 6c 6c 6f 20 47 65 6f 72 67 65 21                |llo George!     |
 * +--------+-------------------------------------------------+----------------+
 * 2023-08-29 18:02:12,495 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] INBOUND HEADERS: streamId=3 headers=DefaultHttp2Headers[:status: 200, content-type: text/plain; charset=UTF-8, accept: text/plain, content-length: 13] padding=0 endStream=false
 * 2023-08-29 18:02:12,511 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-h2-frame-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] INBOUND DATA: streamId=3 padding=0 endStream=true length=13 bytes=48656c6c6f2047656f72676521
 * 2023-08-29 18:02:12,513 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] READ_COMPLETE
 * 2023-08-29 18:02:12,513 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] FLUSH
 * 2023-08-29 18:02:12,513 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] FLUSH
 * 2023-08-29 18:02:12,513 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] READ_REQUEST
 * HTTP/2.0 200 OK
 * NettyH2HeadersToHttpHeaders[content-type: text/plain; charset=UTF-8
 * accept: text/plain
 * content-length: 13]
 * Hello George!
 * 2023-08-29 18:02:12,535 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 - R:localhost/127.0.0.1:8080] CLOSE
 * 2023-08-29 18:02:12,536 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 ! R:localhost/127.0.0.1:8080] INACTIVE
 * 2023-08-29 18:02:12,537 servicetalk-global-io-executor-1-2 [TRACE] servicetalk-examples-wire-logger - [id: 0xc0b781e2, L:/127.0.0.1:63690 ! R:localhost/127.0.0.1:8080] UNREGISTERED
 */
public class DebuggingExampleUrlClient {

    static {
        /*
         * 1. (optional) Disables the AsyncContext associated with individual request/responses to reduce stack-trace
         * depth and simplify execution tracing. This will disable/break some features such as request tracing,
         * authentication, propagated timeouts, etc. that rely upon the AsyncContext so should only be disabled when
         * necessary for debugging:
         */
        // AsyncContext.disable();
    }

    /**
     * Log all protocol user data. Logging user data may expose sensitive content contained in the headers or payload
     * body. Care and consideration should be taken before enabling this feature on production systems.
     *
     * <p>If {@link Boolean#TRUE} then all user data in the headers and payload bodies will be logged in addition to
     * network events.
     * <p>If {@link Boolean#FALSE} then only network events will be logged, but user data contents will be omitted.
     *
     * <p>This implementation uses a constant function to enable or disable logging of user data, your implementation
     * could selectively choose at runtime to log user data based upon application state or context.</p>
     */
    static final BooleanSupplier LOG_USER_DATA = Boolean.TRUE::booleanValue;

    public static void main(String... args) throws Exception {
        final HostAndPort debuggingExampleServerAddress = HostAndPort.of("localhost", 8080);
        try (HttpClient client = buildHttpClient(debuggingExampleServerAddress)) {
            final String requestTarget = String.format("http://%s/sayHello", debuggingExampleServerAddress);
            client.request(client.post(requestTarget).payloadBody("George", textSerializerUtf8()))
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

    private static HttpClient buildHttpClient(final HostAndPort debuggingExampleServerAddress) {
        return HttpClients.forMultiAddressUrl().initializer((scheme, address, builder) -> {
            if (debuggingExampleServerAddress.equals(address)) {
                builder
                /*
                 * 2. (optional) Disables most asynchronous offloading to simplify execution tracing. Changing
                 * this may significantly change application behavior and introduce unexpected blocking. It is
                 * most useful for being able to directly trace through situations that would normally involve a
                 * thread handoff.
                 */
                // .executionStrategy(HttpExecutionStrategies.offloadNever())

                /*
                 * 3. Enables detailed logging of I/O events and I/O states.
                 *
                 * Be sure to also enable the TRACE logger in your logging config file (log4j2.xml for this
                 * example) or raise the configured logging level (2nd argument) to INFO/WARNING to get
                 * visibility without modifying the logger config.
                 * Dumping of protocol bodies is disabled to reduce output but can be enabled by using
                 * {@code Boolean.TRUE::booleanValue} as the 3rd argument. Note that logging all data may leak
                 * sensitive information into logs output. Be careful enabling data logging in production
                 * environments.
                 */
                .enableWireLogging("servicetalk-examples-wire-logger", TRACE, LOG_USER_DATA)

                /*
                 * 4. (optional) Enables detailed logging of HTTP/2 frames.
                 * Use this only if your client communicates over HTTP/2. For HTTP/1.1 use-cases skip this.
                 *
                 * Be sure to also enable the TRACE logger in your logging config file (log4j2.xml for this
                 * example) or raise the configured logging level (2nd argument) to INFO/WARNING to get
                 * visibility without modifying the logger config.
                 * Dumping of protocol bodies is disabled to reduce output but can be enabled by using
                 * {@code Boolean.TRUE::booleanValue} as the 3rd argument. Note that logging all data may leak
                 * sensitive information into logs output. Be careful enabling data logging in production
                 * environments.
                 */
                .protocols(HttpProtocolConfigs.h2()
                        .enableFrameLogging("servicetalk-examples-h2-frame-logger", TRACE, LOG_USER_DATA)
                        .build());
                /*
                 * For ALPN, make sure to supply both HTTP/2 and HTTP/1.X HttpProtocolConfigs and SslConfig.
                 */
                // Note: DefaultTestCerts contains self-signed certificates that may be used only for local testing
                // or demonstration purposes. Never use those for real use-cases.
                // .sslConfig(new ClientSslConfigBuilder(DefaultTestCerts::loadServerCAPem).build())
                // .protocols(HttpProtocolConfigs.h2()
                //         .enableFrameLogging("servicetalk-examples-h2-frame-logger", TRACE, LOG_USER_DATA)
                //         .build(),
                //         HttpProtocolConfigs.h1Default())
            }
        }).build();
    }
}

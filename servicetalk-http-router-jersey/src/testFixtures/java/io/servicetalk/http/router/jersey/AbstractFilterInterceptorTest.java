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
package io.servicetalk.http.router.jersey;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Publisher;
import io.servicetalk.concurrent.api.Single;
import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.router.jersey.resources.AsynchronousResources;
import io.servicetalk.http.router.jersey.resources.SynchronousResources;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.ReaderInterceptorContext;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;

import static io.servicetalk.buffer.api.CharSequences.newAsciiString;
import static io.servicetalk.concurrent.api.Publisher.fromInputStream;
import static io.servicetalk.concurrent.api.Publisher.fromIterable;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.router.jersey.AbstractMessageBodyReaderWriter.isSourceOfType;
import static java.lang.Character.toUpperCase;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static javax.ws.rs.Priorities.ENTITY_CODER;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

public abstract class AbstractFilterInterceptorTest extends AbstractJerseyStreamingHttpServiceTest {

    @Priority(ENTITY_CODER)
    @Provider
    public static class TestGlobalFilter implements ContainerRequestFilter, ContainerResponseFilter {
        @Override
        public void filter(final ContainerRequestContext requestCtx) {
            requestCtx.setEntityStream(new UpperCaseInputStream(requestCtx.getEntityStream()));
        }

        @SuppressWarnings("unchecked")
        @Override
        public void filter(final ContainerRequestContext requestCtx, final ContainerResponseContext responseCtx) {
            // ContainerResponseFilter allows replacing the entity altogether, so we can optimize
            // for cases when the resource has returned a Publisher, while making sure we correctly carry the
            // generic type of the entity so the correct response body writer will be used
            if (isSourceOfType(responseCtx.getEntityType(), Publisher.class, Buffer.class)) {
                final Publisher<Buffer> contentWith0 =
                        ((Publisher<Buffer>) responseCtx.getEntity()).map(AbstractFilterInterceptorTest::oTo0);
                responseCtx.setEntity(new GenericEntity<Publisher<Buffer>>(contentWith0) {
                });
            } else if (isSourceOfType(responseCtx.getEntityType(), Single.class, Buffer.class)) {
                final Single<Buffer> contentWith0 =
                        ((Single<Buffer>) responseCtx.getEntity()).map(AbstractFilterInterceptorTest::oTo0);
                responseCtx.setEntity(new GenericEntity<Single<Buffer>>(contentWith0) {
                });
            } else if (isSourceOfType(responseCtx.getEntityType(), Single.class, Map.class)) {
                final Single<Map> contentWith0 =
                        ((Single<Map>) responseCtx.getEntity()).map(AbstractFilterInterceptorTest::oTo0);
                responseCtx.setEntity(new GenericEntity<Single<Map>>(contentWith0) {
                });
            } else if (responseCtx.getEntity() instanceof Buffer) {
                responseCtx.setEntity(oTo0(((Buffer) responseCtx.getEntity())));
            } else if (responseCtx.getEntity() instanceof Map) {
                final Map<String, Object> contentWith0 = oTo0(((Map<String, Object>) responseCtx.getEntity()));
                responseCtx.setEntity(new GenericEntity<Map<String, Object>>(contentWith0) {
                });
            } else if (responseCtx.getEntity() instanceof TestPojo) {
                final TestPojo contentWith0 = (TestPojo) responseCtx.getEntity();
                assertThat(contentWith0.getaString(), is(notNullValue()));
                contentWith0.setaString(oTo0(contentWith0.getaString()));
                responseCtx.setEntity(contentWith0);
            } else {
                responseCtx.setEntityStream(new Oto0OutputStream(responseCtx.getEntityStream()));
            }
        }
    }

    @Priority(ENTITY_CODER)
    @Provider
    public static class TestInputConsumingGlobalFilter extends TestGlobalFilter {
        @Override
        public void filter(final ContainerRequestContext requestCtx) {
            // Simulate an ill-behaved filter that consumes the all request content beforehand
            // instead of modifying it in a streaming fashion (as done in super)
            try {
                Collection<byte[]> collection = fromInputStream(new UpperCaseInputStream(
                        requestCtx.getEntityStream())).toFuture().get();
                requestCtx.setEntityStream(fromIterable(collection).toInputStream(identity()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Priority(ENTITY_CODER)
    @Provider
    public static class TestGlobalInterceptor implements ReaderInterceptor, WriterInterceptor {
        @Override
        public Object aroundReadFrom(final ReaderInterceptorContext readerInterceptorCtx) throws IOException {
            final InputStream old = readerInterceptorCtx.getInputStream();
            readerInterceptorCtx.setInputStream(new UpperCaseInputStream(old));
            try {
                return readerInterceptorCtx.proceed();
            } finally {
                readerInterceptorCtx.setInputStream(old);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void aroundWriteTo(final WriterInterceptorContext writerInterceptorCtx) throws IOException {
            // WriterInterceptor allows replacing the entity altogether, so we can optimize
            // for cases when the resource has returned a Publisher
            if (isSourceOfType(writerInterceptorCtx.getGenericType(), Publisher.class, Buffer.class)) {
                final Publisher<Buffer> contentWith0 = ((Publisher<Buffer>) writerInterceptorCtx.getEntity())
                        .map(AbstractFilterInterceptorTest::oTo0);
                writerInterceptorCtx.setEntity(contentWith0);
                writerInterceptorCtx.proceed();
            } else if (isSourceOfType(writerInterceptorCtx.getGenericType(), Single.class, Buffer.class)) {
                final Single<Buffer> contentWith0 = ((Single<Buffer>) writerInterceptorCtx.getEntity())
                        .map(AbstractFilterInterceptorTest::oTo0);
                writerInterceptorCtx.setEntity(contentWith0);
                writerInterceptorCtx.proceed();
            } else if (isSourceOfType(writerInterceptorCtx.getGenericType(), Single.class, Map.class)) {
                final Single<Map> contentWith0 = ((Single<Map>) writerInterceptorCtx.getEntity())
                        .map(AbstractFilterInterceptorTest::oTo0);
                writerInterceptorCtx.setEntity(contentWith0);
                writerInterceptorCtx.proceed();
            } else if (writerInterceptorCtx.getEntity() instanceof Buffer) {
                writerInterceptorCtx.setEntity(oTo0(((Buffer) writerInterceptorCtx.getEntity())));
                writerInterceptorCtx.proceed();
            } else if (writerInterceptorCtx.getEntity() instanceof Map) {
                final Map<String, Object> contentWith0 = oTo0(((Map<String, Object>) writerInterceptorCtx.getEntity()));
                writerInterceptorCtx.setEntity(contentWith0);
                writerInterceptorCtx.setType(Map.class);
                writerInterceptorCtx.setGenericType(Map.class);
                writerInterceptorCtx.proceed();
            } else if (writerInterceptorCtx.getEntity() instanceof TestPojo) {
                final TestPojo contentWith0 = (TestPojo) writerInterceptorCtx.getEntity();
                assertThat(contentWith0.getaString(), is(notNullValue()));
                contentWith0.setaString(oTo0(contentWith0.getaString()));
                writerInterceptorCtx.setEntity(contentWith0);
                writerInterceptorCtx.proceed();
            } else {
                final OutputStream previous = writerInterceptorCtx.getOutputStream();
                writerInterceptorCtx.setOutputStream(new Oto0OutputStream(previous));
                try {
                    writerInterceptorCtx.proceed();
                } finally {
                    writerInterceptorCtx.setOutputStream(previous);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void synchronousResource(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post(SynchronousResources.PATH + "/text", "foo1", TEXT_PLAIN),
                    OK, TEXT_PLAIN, "G0T: F001");

            sendAndAssertResponse(post(SynchronousResources.PATH + "/text-response", "foo2", TEXT_PLAIN),
                    ACCEPTED, TEXT_PLAIN, "G0T: F002");

            sendAndAssertResponse(post(SynchronousResources.PATH + "/json", "{\"key\":\"val1\"}",
                    APPLICATION_JSON), OK, APPLICATION_JSON,
                    jsonEquals("{\"KEY\":\"VAL1\",\"f00\":\"bar1\"}"), getJsonResponseContentLengthExtractor());

            sendAndAssertResponse(put(SynchronousResources.PATH + "/json-response", "{\"key\":\"val2\"}",
                    APPLICATION_JSON), ACCEPTED, APPLICATION_JSON,
                    jsonEquals("{\"KEY\":\"VAL2\",\"f00\":\"bar2\"}"), getJsonResponseContentLengthExtractor());
        });
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void publisherResources(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post(SynchronousResources.PATH + "/text-strin-pubout", "foo1", TEXT_PLAIN),
                    OK, TEXT_PLAIN, is("G0T: F001"), __ -> null);

            sendAndAssertResponse(post(SynchronousResources.PATH + "/text-pubin-strout", "foo2", TEXT_PLAIN),
                    OK, TEXT_PLAIN, "G0T: F002");

            sendAndAssertResponse(post(SynchronousResources.PATH + "/text-pubin-pubout", "foo3", TEXT_PLAIN),
                    OK, TEXT_PLAIN, is("G0T: F003"), __ -> null);
        });
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void oioStreamsResource(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() ->
                sendAndAssertResponse(post(SynchronousResources.PATH + "/text-oio-streams", "bar", TEXT_PLAIN),
                                                              OK, TEXT_PLAIN, "G0T: BAR"));
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void asynchronousResource(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post(AsynchronousResources.PATH + "/text", "baz1", TEXT_PLAIN),
                    OK, TEXT_PLAIN, "G0T: BAZ1");

            sendAndAssertResponse(post(AsynchronousResources.PATH + "/text-response", "baz2", TEXT_PLAIN),
                    ACCEPTED, TEXT_PLAIN, "G0T: BAZ2");
        });
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void singleResources(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> {
            sendAndAssertResponse(post(SynchronousResources.PATH + "/json-buf-sglin-sglout-response",
                    "{\"key\":\"val1\"}", APPLICATION_JSON), ACCEPTED, APPLICATION_JSON,
                    jsonEquals("{\"KEY\":\"VAL1\",\"f00\":\"bar6\"}"), __ -> null);

            sendAndAssertResponse(get(AsynchronousResources.PATH + "/single-response"), ACCEPTED, TEXT_PLAIN, "D0NE");

            sendAndAssertResponse(get(AsynchronousResources.PATH + "/single-map"), OK, APPLICATION_JSON,
                    jsonEquals("{\"f00\":\"bar4\"}"), getJsonResponseContentLengthExtractor());

            sendAndAssertResponse(get(AsynchronousResources.PATH + "/single-pojo"), OK, APPLICATION_JSON,
                    jsonEquals("{\"aString\":\"b00\",\"anInt\":456}"), getJsonResponseContentLengthExtractor());

            sendAndAssertResponse(post(AsynchronousResources.PATH + "/json-buf-sglin-sglout", "{\"key\":\"val3\"}",
                    APPLICATION_JSON), OK, APPLICATION_JSON,
                    jsonEquals("{\"KEY\":\"VAL3\",\"f00\":\"bar6\"}"), String::length);
        });
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void bufferResources(RouterApi api) throws Exception {
        setUp(api);
        runTwiceToEnsureEndpointCache(() -> {

            sendAndAssertResponse(get(SynchronousResources.PATH + "/text-buffer"), OK, TEXT_PLAIN, "D0NE");

            final StreamingHttpResponse res =
                    sendAndAssertResponse(withHeader(get(SynchronousResources.PATH + "/text-buffer-response"), "hdr",
                            "bar"), NON_AUTHORITATIVE_INFORMATION, TEXT_PLAIN, "D0NE");
            assertThat(res.headers().get("X-Test"), is(newAsciiString("bar")));

            sendAndAssertResponse(post(SynchronousResources.PATH + "/text-buffer", "foo", TEXT_PLAIN), OK, TEXT_PLAIN,
                    "G0T: F00");

            sendAndAssertResponse(withHeader(post(SynchronousResources.PATH + "/text-buffer-response", "foo",
                    TEXT_PLAIN), "hdr", "bar"), ACCEPTED, TEXT_PLAIN, "G0T: F00");

            sendAndAssertResponse(post(SynchronousResources.PATH + "/json-buf-pubin-pubout",
                    "{\"key\":\"foo2\"}", APPLICATION_JSON), OK, APPLICATION_JSON,
                    jsonEquals("{\"KEY\":\"F002\"}"), __ -> null);

            sendAndAssertResponse(post(AsynchronousResources.PATH + "/json-buf-sglin-sglout?fail=true",
                    "{\"key\":\"val5\"}", APPLICATION_JSON), INTERNAL_SERVER_ERROR, null, "");
        });
    }

    public static class UpperCaseInputStream extends FilterInputStream {
        private boolean closed;

        public UpperCaseInputStream(final InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        @Override
        public int read() throws IOException {
            ensureNotClosed();

            return toUpperCase(super.read());
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            ensureNotClosed();

            final int read = super.read(b, off, len);
            for (int i = 0; i < read; i++) {
                b[off + i] = (byte) toUpperCase((int) b[off + i]);
            }
            return read;
        }

        private void ensureNotClosed() throws IOException {
            if (closed) {
                throw new IOException("Stream is closed");
            }
        }
    }

    private static class Oto0OutputStream extends FilterOutputStream {
        private boolean closed;

        Oto0OutputStream(final OutputStream out) {
            super(out);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        @Override
        public void write(final int b) throws IOException {
            if (closed) {
                throw new IOException("Stream is closed");
            }

            super.write(b == 'o' || b == 'O' ? '0' : b);
        }
    }

    private static Buffer oTo0(final Buffer buf) {
        for (int i = 0; i < buf.readableBytes(); i++) {
            final byte b = buf.getByte(buf.readerIndex() + i);
            if (b == 'o' || b == 'O') {
                buf.setByte(buf.readerIndex() + i, '0');
            }
        }
        return buf;
    }

    private static Map<String, Object> oTo0(final Map<String, Object> map) {
        return map.entrySet().stream().collect(toMap(e -> oTo0(e.getKey()), e -> oTo0(e.getValue())));
    }

    @SuppressWarnings("unchecked")
    private static <T> T oTo0(final T o) {
        return (o instanceof String) ? (T) ((String) o).replaceAll("[oO]", "0") : o;
    }
}

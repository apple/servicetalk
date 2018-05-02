/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpRequest;
import io.servicetalk.http.api.HttpResponse;
import io.servicetalk.http.router.jersey.resources.AsynchronousResources;
import io.servicetalk.http.router.jersey.resources.SynchronousResources;

import org.junit.Test;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static io.servicetalk.http.api.HttpHeaderNames.CONTENT_TYPE;
import static io.servicetalk.http.api.HttpRequestMethods.POST;
import static io.servicetalk.http.api.HttpResponseStatuses.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.router.jersey.TestUtil.assertResponse;
import static io.servicetalk.http.router.jersey.TestUtil.newH11Request;
import static java.lang.Character.toUpperCase;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.hamcrest.Matchers.is;

public abstract class AbstractFilterInterceptorTest extends AbstractJerseyHttpServiceTest {
    @Test
    public void synchronousResource() {
        HttpRequest<HttpPayloadChunk> req =
                newH11Request(POST, SynchronousResources.PATH + "/text", ctx.getBufferAllocator().fromUtf8("foo1"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: FOO1!");

        req = newH11Request(POST, SynchronousResources.PATH + "/text-response", ctx.getBufferAllocator().fromUtf8("foo2"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);
        res = handler.apply(req);
        assertResponse(res, ACCEPTED, TEXT_PLAIN, "GOT: FOO2!");
    }

    @Test
    public void publisherResources() {
        HttpRequest<HttpPayloadChunk> req = newH11Request(POST, SynchronousResources.PATH + "/text-strin-pubout",
                ctx.getBufferAllocator().fromUtf8("foo1"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);
        HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, is("GOT: FOO1!"), $ -> null);

        req = newH11Request(POST, SynchronousResources.PATH + "/text-pubin-strout",
                ctx.getBufferAllocator().fromUtf8("foo2"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);
        res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: FOO2!");

        req = newH11Request(POST, SynchronousResources.PATH + "/text-pubin-pubout",
                ctx.getBufferAllocator().fromUtf8("foo3"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);
        res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, is("GOT: FOO3!"), $ -> null);
    }

    @Test
    public void oioStreamsResource() {
        final HttpRequest<HttpPayloadChunk> req =
                newH11Request(POST, SynchronousResources.PATH + "/text-oio-streams",
                        ctx.getBufferAllocator().fromUtf8("bar"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);

        final HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: BAR!");
    }

    @Test
    public void asynchronousResource() {
        HttpRequest<HttpPayloadChunk> req =
                newH11Request(POST, AsynchronousResources.PATH + "/text", ctx.getBufferAllocator().fromUtf8("baz1"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);
        HttpResponse<HttpPayloadChunk> res = handler.apply(req);
        assertResponse(res, OK, TEXT_PLAIN, "GOT: BAZ1!");

        req = newH11Request(POST, AsynchronousResources.PATH + "/text-response", ctx.getBufferAllocator().fromUtf8("baz2"));
        req.getHeaders().add(CONTENT_TYPE, TEXT_PLAIN);
        res = handler.apply(req);
        assertResponse(res, ACCEPTED, TEXT_PLAIN, "GOT: BAZ2!");
    }

    protected static class UpperCaseInputStream extends FilterInputStream {
        protected UpperCaseInputStream(final InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            return toUpperCase(super.read());
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            final int read = super.read(b, off, len);
            for (int i = 0; i < read; i++) {
                b[off + i] = (byte) toUpperCase((int) b[off + i]);
            }
            return read;
        }
    }

    protected static class ExclamatoryOutputStream extends FilterOutputStream {
        private boolean finished;

        protected ExclamatoryOutputStream(final OutputStream out) {
            super(out);
        }

        void finish() throws IOException {
            if (finished) {
                return;
            }
            finished = true;
            super.write('!');
            super.flush();
        }

        @Override
        public void close() throws IOException {
            finish();
            super.close();
        }
    }
}

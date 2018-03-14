/**
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
package io.servicetalk.http.api;

import java.util.Iterator;

import javax.annotation.Nullable;

import static java.util.Collections.emptyIterator;

final class EmptyHttpCookies implements HttpCookies {
    static final HttpCookies INSTANCE = new EmptyHttpCookies();

    private EmptyHttpCookies() {
    }

    @Nullable
    @Override
    public HttpCookie getCookie(String name) {
        return null;
    }

    @Override
    public Iterator<? extends HttpCookie> getCookies(String name) {
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpCookie> getCookies(String name, String domain, String path) {
        return emptyIterator();
    }

    @Override
    public HttpCookies addCookie(HttpCookie cookie) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeCookies(String name) {
        return false;
    }

    @Override
    public boolean removeCookies(String name, String domain, String path) {
        return false;
    }

    @Override
    public void encodeToHttpHeaders() {
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public Iterator<HttpCookie> iterator() {
        return emptyIterator();
    }
}

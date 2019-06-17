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
package io.servicetalk.http.api;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

import static java.util.Collections.emptyIterator;
import static java.util.Collections.emptySet;

/**
 * {@link HttpHeaders} which are always empty and does not allow modification.
 */
public final class EmptyHttpHeaders implements HttpHeaders {
    public static final HttpHeaders INSTANCE = new EmptyHttpHeaders();

    private EmptyHttpHeaders() {
        // singleton
    }

    @Nullable
    @Override
    public CharSequence get(CharSequence name) {
        return null;
    }

    @Override
    public CharSequence get(CharSequence name, CharSequence defaultValue) {
        return defaultValue;
    }

    @Nullable
    @Override
    public CharSequence getAndRemove(CharSequence name) {
        return null;
    }

    @Override
    public Iterator<? extends CharSequence> valuesIterator(CharSequence name) {
        return emptyIterator();
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value) {
        return false;
    }

    @Override
    public boolean containsIgnoreCase(CharSequence name, CharSequence value) {
        return false;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public Set<? extends CharSequence> names() {
        return emptySet();
    }

    @Override
    public HttpHeaders add(CharSequence name, CharSequence value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<? extends CharSequence> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(CharSequence name, CharSequence... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders add(HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(CharSequence name, CharSequence value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<? extends CharSequence> values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(CharSequence name, CharSequence... values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders set(HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders replace(HttpHeaders headers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(CharSequence name) {
        return false;
    }

    @Override
    public boolean remove(CharSequence name, CharSequence value) {
        return false;
    }

    @Override
    public boolean removeIgnoreCase(CharSequence name, CharSequence value) {
        return false;
    }

    @Override
    public HttpHeaders clear() {
        return this;
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iterator() {
        return emptyIterator();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HttpHeaders)) {
            return false;
        }

        HttpHeaders rhs = (HttpHeaders) o;
        return isEmpty() && rhs.isEmpty();
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[]";
    }

    @Override
    public String toString(BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter) {
        return toString();
    }

    @Nullable
    @Override
    public HttpCookiePair getCookie(final CharSequence name) {
        return null;
    }

    @Nullable
    @Override
    public HttpSetCookie getSetCookie(final CharSequence name) {
        return null;
    }

    @Override
    public Iterator<? extends HttpCookiePair> getCookiesIterator() {
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpCookiePair> getCookiesIterator(final CharSequence name) {
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookiesIterator() {
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookiesIterator(final CharSequence name) {
        return emptyIterator();
    }

    @Override
    public Iterator<? extends HttpSetCookie> getSetCookiesIterator(final CharSequence name, final CharSequence domain,
                                                                   final CharSequence path) {
        return emptyIterator();
    }

    @Override
    public HttpHeaders addCookie(final HttpCookiePair cookie) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HttpHeaders addSetCookie(final HttpSetCookie cookie) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeCookies(final CharSequence name) {
        return false;
    }

    @Override
    public boolean removeSetCookies(final CharSequence name) {
        return false;
    }

    @Override
    public boolean removeSetCookies(final CharSequence name, final CharSequence domain, final CharSequence path) {
        return false;
    }
}

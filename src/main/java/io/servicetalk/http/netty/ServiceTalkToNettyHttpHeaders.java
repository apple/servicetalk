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
package io.servicetalk.http.netty;

import io.netty.handler.codec.DateFormatter;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import javax.annotation.Nullable;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static io.servicetalk.http.netty.HeaderUtils.DEFAULT_HEADER_FILTER;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

final class ServiceTalkToNettyHttpHeaders extends HttpHeaders {
    private final io.servicetalk.http.api.HttpHeaders serviceTalkHeaders;

    ServiceTalkToNettyHttpHeaders(io.servicetalk.http.api.HttpHeaders serviceTalkHeaders) {
        this.serviceTalkHeaders = requireNonNull(serviceTalkHeaders);
    }

    @Nullable
    @Override
    public String get(CharSequence name) {
        CharSequence v = serviceTalkHeaders.get(name);
        return v == null ? null : v.toString();
    }

    @Nullable
    @Override
    public String get(String name) {
        CharSequence v = serviceTalkHeaders.get(name);
        return v == null ? null : v.toString();
    }

    @Nullable
    @Override
    public Integer getInt(CharSequence name) {
        CharSequence value = serviceTalkHeaders.get(name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Nullable
    @Override
    public int getInt(CharSequence name, int defaultValue) {
        Integer v = getInt(name);
        return v != null ? v : defaultValue;
    }

    @Nullable
    @Override
    public Short getShort(CharSequence name) {
        CharSequence value = serviceTalkHeaders.get(name);
        if (value == null) {
            return null;
        }
        try {
            return Short.parseShort(value.toString());
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    @Nullable
    @Override
    public short getShort(CharSequence name, short defaultValue) {
        Short v = getShort(name);
        return v != null ? v : defaultValue;
    }

    @Nullable
    @Override
    public Long getTimeMillis(CharSequence name) {
        CharSequence value = serviceTalkHeaders.get(name);
        if (value == null) {
            return null;
        }
        Date date = DateFormatter.parseHttpDate(value);
        if (date == null) {
            return null;
        }
        return date.getTime();
    }

    @Nullable
    @Override
    public long getTimeMillis(CharSequence name, long defaultValue) {
        Long v = getTimeMillis(name);
        return v != null ? v : defaultValue;
    }

    @Override
    public List<String> getAll(CharSequence name) {
        List<String> list = new ArrayList<>(4);
        serviceTalkHeaders.getAll(name).forEachRemaining(charSeq -> list.add(charSeq.toString()));
        return unmodifiableList(list);
    }

    @Override
    public List<String> getAll(String name) {
        List<String> list = new ArrayList<>(4);
        serviceTalkHeaders.getAll(name).forEachRemaining(charSeq -> list.add(charSeq.toString()));
        return unmodifiableList(list);
    }

    @Override
    public List<Map.Entry<String, String>> entries() {
        if (isEmpty()) {
            return Collections.emptyList();
        }
        List<Map.Entry<String, String>> entriesConverted = new ArrayList<>(serviceTalkHeaders.size());
        for (Map.Entry<String, String> entry : this) {
            entriesConverted.add(entry);
        }
        return unmodifiableList(entriesConverted);
    }

    @Override
    public boolean contains(CharSequence name) {
        return serviceTalkHeaders.contains(name);
    }

    @Override
    public boolean contains(String name) {
        return serviceTalkHeaders.contains(name);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return serviceTalkHeaders.contains(name, value, ignoreCase);
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return new StringEntryIterator(serviceTalkHeaders.iterator());
    }

    @Override
    public Iterator<Map.Entry<CharSequence, CharSequence>> iteratorCharSequence() {
        return serviceTalkHeaders.iterator();
    }

    @Override
    public boolean isEmpty() {
        return serviceTalkHeaders.isEmpty();
    }

    @Override
    public int size() {
        return serviceTalkHeaders.size();
    }

    @Override
    public Set<String> names() {
        return new CharSequenceDelegatingStringSet(serviceTalkHeaders.getNames());
    }

    @Override
    public HttpHeaders add(CharSequence name, Object value) {
        serviceTalkHeaders.add(name, convertObject(value));
        return this;
    }

    @Override
    public HttpHeaders add(String name, Object value) {
        serviceTalkHeaders.add(name, convertObject(value));
        return this;
    }

    @Override
    public HttpHeaders add(CharSequence name, Iterable<?> values) {
        for (Object value : values) {
            serviceTalkHeaders.add(name, convertObject(value));
        }
        return this;
    }

    @Override
    public HttpHeaders add(String name, Iterable<?> values) {
        for (Object value : values) {
            serviceTalkHeaders.add(name, convertObject(value));
        }
        return this;
    }

    @Override
    public HttpHeaders addInt(CharSequence name, int value) {
        serviceTalkHeaders.add(name, String.valueOf(value));
        return this;
    }

    @Override
    public HttpHeaders addShort(CharSequence name, short value) {
        serviceTalkHeaders.add(name, String.valueOf(value));
        return this;
    }

    @Override
    public HttpHeaders set(CharSequence name, Object value) {
        serviceTalkHeaders.set(name, convertObject(value));
        return this;
    }

    @Override
    public HttpHeaders set(String name, Object value) {
        return set((CharSequence) name, value);
    }

    @Override
    public HttpHeaders set(CharSequence name, Iterable<?> values) {
        serviceTalkHeaders.remove(name);
        for (Object value : values) {
            serviceTalkHeaders.add(name, convertObject(value));
        }
        return this;
    }

    @Override
    public HttpHeaders set(String name, Iterable<?> values) {
        serviceTalkHeaders.remove(name);
        for (Object value : values) {
            serviceTalkHeaders.add(name, convertObject(value));
        }
        return this;
    }

    @Override
    public HttpHeaders setInt(CharSequence name, int value) {
        serviceTalkHeaders.set(name, String.valueOf(value));
        return this;
    }

    @Override
    public HttpHeaders setShort(CharSequence name, short value) {
        serviceTalkHeaders.set(name, String.valueOf(value));
        return this;
    }

    @Override
    public HttpHeaders remove(CharSequence name) {
        serviceTalkHeaders.remove(name);
        return this;
    }

    @Override
    public HttpHeaders remove(String name) {
        serviceTalkHeaders.remove(name);
        return this;
    }

    @Override
    public HttpHeaders clear() {
        serviceTalkHeaders.clear();
        return this;
    }

    @Override
    public HttpHeaders copy() {
        return new ServiceTalkToNettyHttpHeaders(serviceTalkHeaders.copy());
    }

    @Override
    public String toString() {
        return toString(DEFAULT_HEADER_FILTER);
    }

    String toString(BiFunction<? super CharSequence, ? super CharSequence, CharSequence> filter) {
        return HeaderUtils.toString(serviceTalkHeaders, filter);
    }

    @Override
    public boolean equals(Object o) {
        return o == this ||
               o instanceof ServiceTalkToNettyHttpHeaders && HeaderUtils.equals(serviceTalkHeaders, ((ServiceTalkToNettyHttpHeaders) o).serviceTalkHeaders) ||
               o instanceof io.servicetalk.http.api.HttpHeaders && HeaderUtils.equals(serviceTalkHeaders, (io.servicetalk.http.api.HttpHeaders) o);
    }

    @Override
    public int hashCode() {
        return HeaderUtils.hashCode(serviceTalkHeaders);
    }

    private static CharSequence convertObject(Object value) {
        if (value instanceof CharSequence) {
            return (CharSequence) value;
        }
        return value.toString();
    }

    private static final class StringEntryIterator implements Iterator<Map.Entry<String, String>> {
        private final Iterator<Map.Entry<CharSequence, CharSequence>> iter;

        StringEntryIterator(Iterator<Map.Entry<CharSequence, CharSequence>> iter) {
            this.iter = iter;
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public Map.Entry<String, String> next() {
            return new StringEntry(iter.next());
        }

        @Override
        public void remove() {
            iter.remove();
        }
    }

    private static final class StringEntry implements Map.Entry<String, String> {
        private final Map.Entry<CharSequence, CharSequence> entry;
        @Nullable
        private String name;
        @Nullable
        private String value;

        StringEntry(Map.Entry<CharSequence, CharSequence> entry) {
            this.entry = entry;
        }

        @Override
        public String getKey() {
            if (name == null) {
                name = entry.getKey().toString();
            }
            return name;
        }

        @Override
        public String getValue() {
            if (value == null && entry.getValue() != null) {
                value = entry.getValue().toString();
            }
            return value;
        }

        @Override
        public String setValue(String value) {
            String old = getValue();
            entry.setValue(value);
            return old;
        }

        @Override
        public String toString() {
            return entry.toString();
        }
    }

    private static final class CharSequenceDelegatingStringSet extends DelegatingStringSet<CharSequence> {
        CharSequenceDelegatingStringSet(Set<? extends CharSequence> allNames) {
            super(allNames);
        }

        @Override
        public boolean add(String e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends String> c) {
            throw new UnsupportedOperationException();
        }
    }

    private abstract static class DelegatingStringSet<T> extends AbstractCollection<String> implements Set<String> {
        protected final Set<? extends T> allNames;

        DelegatingStringSet(Set<? extends T> allNames) {
            this.allNames = checkNotNull(allNames, "allNames");
        }

        @Override
        public int size() {
            return allNames.size();
        }

        @Override
        public boolean isEmpty() {
            return allNames.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return allNames.contains(o.toString());
        }

        @Override
        public Iterator<String> iterator() {
            return new StringIterator<>(allNames.iterator());
        }

        @Override
        public boolean remove(Object o) {
            return allNames.remove(o);
        }

        @Override
        public void clear() {
            allNames.clear();
        }
    }

    private static final class StringIterator<T> implements Iterator<String> {
        private final Iterator<T> iter;

        StringIterator(Iterator<T> iter) {
            this.iter = iter;
        }

        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public String next() {
            T next = iter.next();
            return next != null ? next.toString() : "null";
        }

        @Override
        public void remove() {
            iter.remove();
        }
    }
}

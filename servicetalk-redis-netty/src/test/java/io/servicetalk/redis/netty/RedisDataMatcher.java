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
package io.servicetalk.redis.netty;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.redis.api.DefaultBaseRedisData;
import io.servicetalk.redis.api.RedisData;
import io.servicetalk.redis.api.RedisData.Array;
import io.servicetalk.redis.api.RedisData.ArraySize;
import io.servicetalk.redis.api.RedisData.BulkStringChunk;
import io.servicetalk.redis.api.RedisData.CompleteBulkString;
import io.servicetalk.redis.api.RedisData.DefaultFirstBulkStringChunk;
import io.servicetalk.redis.api.RedisData.SimpleString;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

final class RedisDataMatcher extends BaseMatcher<RedisData> {
    private final Class<? extends RedisData> expectedClass;
    private final MatcherHolder matcherHolder;

    private <T> RedisDataMatcher(final Class<? extends RedisData> expectedClass, final Function<RedisData, T> extractor, final Matcher<T> contentMatcher) {
        this.expectedClass = requireNonNull(expectedClass);
        this.matcherHolder = new MatcherHolder(contentMatcher, extractor);
    }

    static Matcher<RedisData> redisNull() {
        return sameInstance(RedisData.NULL);
    }

    static RedisDataMatcher redisSimpleString(final String value) {
        return redisSimpleString(is(value));
    }

    static RedisDataMatcher redisSimpleString(final Matcher<String> valueMatcher) {
        return new RedisDataMatcher(SimpleString.class, data -> data.charSequenceValue().toString(), valueMatcher);
    }

    static RedisDataMatcher redisInteger(final long value) {
        return redisInteger(is(value));
    }

    static RedisDataMatcher redisInteger(final Matcher<Long> valueMatcher) {
        return new RedisDataMatcher(RedisData.Integer.class, RedisData::longValue, valueMatcher);
    }

    static RedisDataMatcher redisBulkStringChunk(final Buffer buf) {
        return redisBulkStringChunk(is(buf));
    }

    static RedisDataMatcher redisBulkStringChunk(final Matcher<Buffer> bufMatcher) {
        return new RedisDataMatcher(BulkStringChunk.class, RedisData::bufferValue, bufMatcher);
    }

    static RedisDataMatcher redisFirstBulkStringChunk(final Buffer buf) {
        return redisFirstBulkStringChunk(is(buf));
    }

    static RedisDataMatcher redisFirstBulkStringChunk(final Matcher<Buffer> bufMatcher) {
        return new RedisDataMatcher(DefaultFirstBulkStringChunk.class, RedisData::bufferValue, bufMatcher);
    }

    static RedisDataMatcher redisFirstBulkStringChunkSize(final Matcher<Integer> sizeMatcher) {
        return new RedisDataMatcher(DefaultFirstBulkStringChunk.class, rd -> rd.bufferValue().readableBytes(), sizeMatcher);
    }

    static RedisDataMatcher redisCompleteBulkString(final Buffer buf) {
        return redisCompleteBulkString(is(buf));
    }

    static RedisDataMatcher redisCompleteBulkString(final Matcher<Buffer> bufMatcher) {
        return new RedisDataMatcher(CompleteBulkString.class, RedisData::bufferValue, bufMatcher);
    }

    static RedisDataMatcher redisCompleteBulkStringSize(final Matcher<Integer> sizeMatcher) {
        return new RedisDataMatcher(CompleteBulkString.class, rd -> rd.bufferValue().readableBytes(), sizeMatcher);
    }

    static RedisDataMatcher redisArraySize(final long size) {
        return redisArraySize(is(size));
    }

    static RedisDataMatcher redisArraySize(final Matcher<Long> sizeMatcher) {
        return new RedisDataMatcher(ArraySize.class, RedisData::longValue, sizeMatcher);
    }

    static RedisDataMatcher redisArray(final RedisData... content) {
        return redisArray(contains(content));
    }

    static RedisDataMatcher redisArray(final Matcher<Iterable<? extends RedisData>> contentMatcher) {
        return new RedisDataMatcher(Array.class, RedisData::listValue, contentMatcher);
    }

    static RedisDataMatcher redisError(final String msg) {
        return redisError(is(msg));
    }

    static RedisDataMatcher redisError(final Matcher<String> msgMatcher) {
        return new RedisDataMatcher(RedisData.Error.class, data -> data.charSequenceValue().toString(), msgMatcher);
    }

    @Override
    public boolean matches(final Object argument) {
        return argument instanceof DefaultBaseRedisData
                && argument.getClass().equals(expectedClass)
                && matcherHolder.match((RedisData) argument);
    }

    @Override
    public void describeTo(final Description description) {
        description.appendText(expectedClass.getSimpleName())
                .appendText("{")
                .appendDescriptionOf(matcherHolder.matcher)
                .appendText("}");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class MatcherHolder {

        private final Matcher matcher;
        private final Function<RedisData, Object> extractor;

        <T> MatcherHolder(Matcher<T> matcher, Function<RedisData, T> extractor) {
            this.matcher = matcher;
            this.extractor = (Function<RedisData, Object>) extractor;
        }

        boolean match(final RedisData data) {
            return matcher.matches(extractor.apply(data));
        }
    }
}

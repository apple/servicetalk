/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.dns.discovery.netty;

import io.netty.channel.EventLoop;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

import static java.lang.Integer.getInteger;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

abstract class DnsResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DnsResolver.class);

    // Backup request static configuration: values > 0 mean allow a backup request with fixed delay, disabled otherwise.
    private static final String DNS_BACKUP_REQUEST_DELAY_MS_PROPERTY =
            "io.servicetalk.dns.discovery.netty.experimental.dnsBackupRequestDelayMs";

    private DnsResolver() {
        // only used by inner classes
    }

    abstract void close();

    abstract Future<List<InetAddress>> resolveAll(Object publisher, String name);

    abstract Future<List<DnsRecord>> resolveAll(Object publisher, DnsQuestion name);

    abstract long queryTimeoutMillis();

    static DnsResolver build(String resolverId, EventLoop eventLoop, DnsNameResolverBuilder builder) {
        // If the process started life with an unset delay or a delay of 0 or less, we disable backup requests
        // for the lifetime of the DNS resolver.
        Integer backupRequestDelay = getDelay();
        if (backupRequestDelay == null || backupRequestDelay <= 0) {
            LOGGER.debug("Configured DNS backup request delay was {}. DNS backup requests disabled.",
                    backupRequestDelay);
            return new DefaultResolver(builder.build());
        } else {
            // The backup resolver is really two resolvers: a primary and a backup. This lets us avoid the
            // consolidation cache in the second resolver so that the backup isn't simply consolidated.
            // Note that they still share result caches and EventLoop thread.
            LOGGER.debug("Configured DNS backup request delay was {}. DNS backup requests enabled.",
                    backupRequestDelay);
            return new BackupRequestResolver(resolverId,
                    builder.build(),
                    builder.consolidateCacheSize(0).build(),
                    eventLoop, DnsResolver::getDelay);
        }
    }

    private static @Nullable Integer getDelay() {
        return getInteger(DNS_BACKUP_REQUEST_DELAY_MS_PROPERTY);
    }

    static final class DefaultResolver extends DnsResolver {
        private final DnsNameResolver nettyResolver;

        DefaultResolver(DnsNameResolver nettyResolver) {
            this.nettyResolver = nettyResolver;
        }

        @Override
        public void close() {
            nettyResolver.close();
        }

        @Override
        public Future<List<InetAddress>> resolveAll(Object publisher, String name) {
            return nettyResolver.resolveAll(name);
        }

        @Override
        public Future<List<DnsRecord>> resolveAll(Object publisher, DnsQuestion question) {
            return nettyResolver.resolveAll(question);
        }

        @Override
        public long queryTimeoutMillis() {
            return nettyResolver.queryTimeoutMillis();
        }
    }

    static final class BackupRequestResolver extends DnsResolver {

        private final String resolverId;
        private final DnsNameResolver primaryResolver;
        private final DnsNameResolver backupResolver;
        private final EventLoop eventLoop;
        private final Supplier<Integer> backupDelayMs;

        BackupRequestResolver(String resolverId, DnsNameResolver primaryResolver, DnsNameResolver backupResolver,
                              EventLoop eventLoop, Supplier<Integer> backupDelayMs) {
            this.resolverId = resolverId;
            this.primaryResolver = primaryResolver;
            this.backupResolver = backupResolver;
            this.eventLoop = eventLoop;
            this.backupDelayMs = backupDelayMs;
        }

        @Override
        public void close() {
            try {
                primaryResolver.close();
            } finally {
                backupResolver.close();
            }
        }

        @Override
        public Future<List<InetAddress>> resolveAll(Object publisher, String name) {
            return withBackup(publisher, resolver -> resolver.resolveAll(name));
        }

        @Override
        public Future<List<DnsRecord>> resolveAll(Object publisher, DnsQuestion name) {
            return withBackup(publisher, resolver -> resolver.resolveAll(name));
        }

        @Override
        public long queryTimeoutMillis() {
            return primaryResolver.queryTimeoutMillis();
        }

        private <T> Future<T> withBackup(Object publisher,
                                         Function<? super DnsNameResolver, ? extends Future<T>> query) {
            Future<T> primaryQuery = query.apply(primaryResolver);
            if (primaryQuery.isDone()) {
                return primaryQuery;
            }
            Integer backupDelay = backupDelayMs.get();
            if (backupDelay == null || backupDelay <= 0) {
                // no backup for this request
                return primaryQuery;
            }
            Promise<T> result = eventLoop.newPromise();
            Future<?> timer = eventLoop.schedule(() -> {
                if (allowBackupRequest()) {
                    LOGGER.debug("Publisher {} from resolver {} issuing DNS backup request after {} delay.",
                            publisher, resolverId, backupDelay);
                    PromiseNotifier.cascade(false, query.apply(backupResolver), result);
                }
            }, backupDelay, MILLISECONDS);
            primaryQuery.addListener(_unused -> timer.cancel(true));
            PromiseNotifier.cascade(false, primaryQuery, result);
            return result;
        }

        private boolean allowBackupRequest() {
            // In the future we should make this predicated on a token bucket.
            return true;
        }
    }
}

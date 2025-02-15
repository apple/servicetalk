package io.servicetalk.dns.discovery.netty;

import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.util.concurrent.Future;

import java.io.Closeable;
import java.net.InetAddress;
import java.util.List;

interface UnderlyingDnsResolver extends Closeable {

    Future<List<DnsRecord>> resolveAllQuestion(DnsQuestion t);

    Future<List<InetAddress>> resolveAll(String t);

    long queryTimeoutMillis();

    @Override
    void close();

    static final class NettyDnsNameResolver implements UnderlyingDnsResolver {
        private final DnsNameResolver resolver;

        NettyDnsNameResolver(final DnsNameResolver resolver) {
            this.resolver = resolver;
        }

        @Override
        public Future<List<DnsRecord>> resolveAllQuestion(DnsQuestion t) {
            return resolver.resolveAll(t);
        }

        @Override
        public Future<List<InetAddress>> resolveAll(String t) {
            return resolver.resolveAll(t);
        }

        @Override
        public long queryTimeoutMillis() {
            return resolver.queryTimeoutMillis();
        }

        @Override
        public void close() {
            resolver.close();
        }
    }
}

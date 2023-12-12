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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.transport.netty.internal.AddressUtils;

import org.apache.directory.server.dns.DnsException;
import org.apache.directory.server.dns.DnsServer;
import org.apache.directory.server.dns.io.encoder.DnsMessageEncoder;
import org.apache.directory.server.dns.io.encoder.ResourceRecordEncoder;
import org.apache.directory.server.dns.messages.DnsMessage;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.protocol.DnsProtocolHandler;
import org.apache.directory.server.dns.protocol.DnsUdpDecoder;
import org.apache.directory.server.dns.protocol.DnsUdpEncoder;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.apache.directory.server.protocol.shared.transport.UdpTransport;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.transport.socket.DatagramAcceptor;
import org.apache.mina.transport.socket.DatagramSessionConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Set;

import static java.util.Objects.requireNonNull;

class TestDnsServer extends DnsServer {
    private final DelegateRecordStore store;
    private final InetSocketAddress bindAddress;

    TestDnsServer(RecordStore store) {
        this(store, AddressUtils.localAddress(0));
    }

    TestDnsServer(RecordStore store, InetSocketAddress bindAddress) {
        this.store = new DelegateRecordStore(store);
        this.bindAddress = requireNonNull(bindAddress);
    }

    @Override
    public void start() throws IOException {
        UdpTransport transport = new UdpTransport(bindAddress.getHostString(), bindAddress.getPort());
        setTransports(transport);

        DatagramAcceptor acceptor = transport.getAcceptor();

        acceptor.setHandler(new DnsProtocolHandler(this, store) {
            @Override
            public void sessionCreated(IoSession session) {
                // Use our own codec to support AAAA testing
                session.getFilterChain()
                        .addFirst("codec", new ProtocolCodecFilter(new TestDnsProtocolUdpCodecFactory()));
            }
        });

        ((DatagramSessionConfig) acceptor.getSessionConfig()).setReuseAddress(true);

        // Start the listener
        acceptor.bind();
    }

    public InetSocketAddress localAddress() {
        return (InetSocketAddress) getTransports()[0].getAcceptor().getLocalAddress();
    }

    protected DnsMessage filterMessage(DnsMessage message) {
        return message;
    }

    /**
     * {@link ProtocolCodecFactory} which allows to test AAAA resolution.
     */
    private final class TestDnsProtocolUdpCodecFactory implements ProtocolCodecFactory {
        private final DnsMessageEncoder encoder = new DnsMessageEncoder();
        private final TestAAAARecordEncoder recordEncoder = new TestAAAARecordEncoder();

        @Override
        public ProtocolEncoder getEncoder(IoSession session) {
            return new DnsUdpEncoder() {

                @Override
                public void encode(IoSession session, Object message, ProtocolEncoderOutput out) {
                    IoBuffer buf = IoBuffer.allocate(1024);
                    DnsMessage dnsMessage = filterMessage((DnsMessage) message);
                    encoder.encode(buf, dnsMessage);
                    for (ResourceRecord record : dnsMessage.getAnswerRecords()) {
                        // This is a hack to allow to also test for AAAA resolution as DnsMessageEncoder
                        // does not support it and it is hard to extend, because the interesting methods
                        // are private...
                        // In case of RecordType.AAAA we need to encode the RecordType by ourselves.
                        if (record.getRecordType() == RecordType.AAAA) {
                            try {
                                recordEncoder.put(buf, record);
                            } catch (IOException e) {
                                // Should never happen
                                throw new IllegalStateException(e);
                            }
                        }
                    }
                    buf.flip();

                    out.write(buf);
                }
            };
        }

        @Override
        public ProtocolDecoder getDecoder(IoSession session) {
            return new DnsUdpDecoder();
        }

        private final class TestAAAARecordEncoder extends ResourceRecordEncoder {

            @Override
            protected void putResourceRecordData(IoBuffer ioBuffer, ResourceRecord resourceRecord) {
                byte[] bytes;
                try {
                    bytes = InetAddress.getByName(resourceRecord.get(DnsAttribute.IP_ADDRESS)).getAddress();
                } catch (UnknownHostException e) {
                    throw new IllegalStateException(e);
                }
                ioBuffer.put(bytes);
            }
        }
    }

    private static final class DelegateRecordStore implements RecordStore {
        private volatile RecordStore store;

        DelegateRecordStore(final RecordStore store) {
            this.store = store;
        }

        void setStore(final RecordStore store) {
            this.store = store;
        }

        @Override
        public Set<ResourceRecord> getRecords(final QuestionRecord question) throws DnsException {
            return store.getRecords(question);
        }
    }
}

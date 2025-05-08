package io.servicetalk.transport.api;

import static java.util.Objects.requireNonNull;

public class DelegatingConnectionObserver implements ConnectionObserver {

    public DelegatingConnectionObserver(ConnectionObserver delegate) {
        this.delegate = requireNonNull(delegate, "delegate");
    }

    private final ConnectionObserver delegate;

    public ConnectionObserver delegate() {
        return delegate;
    }

    @Override
    public void onDataRead(int size) {
        delegate.onDataRead(size);
    }

    @Override
    public void onDataWrite(int size) {
        delegate.onDataWrite(size);
    }

    @Override
    public void onFlush() {
        delegate.onFlush();
    }

    @Override
    public void onTransportHandshakeComplete() {
        delegate.onTransportHandshakeComplete();
    }

    @Override
    public void onTransportHandshakeComplete(ConnectionInfo info) {
        delegate().onTransportHandshakeComplete(info);
    }

    @Override
    public ProxyConnectObserver onProxyConnect(Object connectMsg) {
        return delegate().onProxyConnect(connectMsg);
    }

    @Override
    public SecurityHandshakeObserver onSecurityHandshake() {
        return delegate().onSecurityHandshake();
    }

    @Override
    public SecurityHandshakeObserver onSecurityHandshake(SslConfig sslConfig) {
        return delegate().onSecurityHandshake(sslConfig);
    }

    @Override
    public DataObserver connectionEstablished(ConnectionInfo info) {
        return delegate().connectionEstablished(info);
    }

    @Override
    public MultiplexedObserver multiplexedConnectionEstablished(ConnectionInfo info) {
        return delegate().multiplexedConnectionEstablished(info);
    }

    @Override
    public void connectionWritabilityChanged(boolean isWritable) {
        delegate().connectionWritabilityChanged(isWritable);
    }

    @Override
    public void connectionClosed(Throwable error) {
        delegate().connectionClosed(error);
    }

    @Override
    public void connectionClosed() {
        delegate().connectionClosed();
    }
}

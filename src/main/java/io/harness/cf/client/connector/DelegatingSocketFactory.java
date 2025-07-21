package io.harness.cf.client.connector;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.SocketFactory;

final class DelegatingSocketFactory extends SocketFactory {
  private final SocketFactory delegate;

  public DelegatingSocketFactory(SocketFactory delegate) {
    this.delegate = delegate;
  }

  @Override
  public Socket createSocket() throws IOException {
    return configureSocket(delegate.createSocket());
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    return configureSocket(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localAddress, int localPort)
      throws IOException {
    return configureSocket(delegate.createSocket(host, port, localAddress, localPort));
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    return configureSocket(delegate.createSocket(host, port));
  }

  @Override
  public Socket createSocket(InetAddress host, int port, InetAddress localAddress, int localPort)
      throws IOException {
    return configureSocket(delegate.createSocket(host, port, localAddress, localPort));
  }

  Socket configureSocket(Socket socket) {
    return socket;
  }
}

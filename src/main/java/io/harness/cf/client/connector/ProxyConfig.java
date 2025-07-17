package io.harness.cf.client.connector;

import java.net.InetSocketAddress;
import java.net.Proxy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;

import javax.net.ssl.SSLSocketFactory;

@Slf4j
public class ProxyConfig {

  public static Proxy getProxyConfig() {
    final String host = System.getProperty("https.proxyHost", System.getProperty("http.proxyHost"));
    final String port = System.getProperty("https.proxyPort", System.getProperty("http.proxyPort"));
    if (host == null || host.isEmpty() || port == null || port.isEmpty()) {
      return Proxy.NO_PROXY;
    }
    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, Integer.parseInt(port)));
  }

  public static Authenticator getProxyAuthentication() {
    final String user = System.getProperty("http.proxyUser");
    final String password = System.getProperty("http.proxyPassword");
    if (user == null || user.isEmpty() || password == null || password.isEmpty()) {
      return Authenticator.NONE;
    }

    return (route, response) -> {
      final String targetIpPort = getIpAndPort((route == null ? null : route.socketAddress()));
      final String configuredIpPort = getIpAndPort((InetSocketAddress) getProxyConfig().address());

      if (targetIpPort.equalsIgnoreCase(configuredIpPort)) {
        final String credential = Credentials.basic(user, password);
        return response.request().newBuilder().header("Proxy-Authorization", credential).build();
      } else {
        log.warn(
            "Target proxy `{}` does not match configured proxy `{}`. Credentials not sent",
            targetIpPort,
            configuredIpPort);
        return null;
      }
    };
  }

  private static String getIpAndPort(InetSocketAddress addr) {
    if (addr == null) {
      return "null";
    }
    return addr.getAddress().getHostAddress() + ":" + addr.getPort();
  }

  public static void configureTls(OkHttpClient.Builder builder) {
    if (builder == null) {
      return;
    }
    final String host = System.getProperty("https.proxyHost");
    final String port = System.getProperty("https.proxyPort");
    if (host == null || host.isEmpty() || port == null || port.isEmpty()) {
      return;
    }

    builder.socketFactory(new DelegatingSocketFactory(SSLSocketFactory.getDefault()));
  }
}

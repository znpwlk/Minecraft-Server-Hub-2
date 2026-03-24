import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;

public class ProxyDetector {

    private static final int[] COMMON_PROXY_PORTS = {
        10809,  // v2rayN (默认)
        7890,   // Clash (默认)
        1080,   // Shadowsocks / SSR / SOCKS5
        8080,   // 通用 HTTP 代理
        8888,   // 通用代理 / Fiddler
        1087,   // Surge / 其他
        1088,   // Surge / 其他
        8118,   // Privoxy
        8123,   // Polipo
        7070,   // GoProxy
        9666,   // GAppProxy
        8388,   // Shadowsocks (备用)
        8389,   // Shadowsocks (备用)
        443,    // HTTPS 代理
        3128,   // Squid
        9050,   // Tor
        9150,   // Tor Browser
        7891,   // Clash (备用)
        7892,   // Clash (备用)
        1081,   // SOCKS (备用)
        1082,   // SOCKS (备用)
        1090,   // SSR (备用)
        1091,   // SSR (备用)
        2080,   // 通用代理
        2081,   // 通用代理
        33128,  // 自定义代理
        44128   // 自定义代理
    };
    private static final String[] COMMON_PROXY_HOSTS = {"127.0.0.1", "localhost"};

    private static Proxy cachedProxy = null;
    private static long lastCheckTime = 0;
    private static final long CACHE_DURATION = 30000;

    public static Proxy getProxyForUrl(URL url) {
        long currentTime = System.currentTimeMillis();
        if (cachedProxy != null && (currentTime - lastCheckTime) < CACHE_DURATION) {
            return cachedProxy;
        }

        Proxy proxy = detectProxy(url);
        cachedProxy = proxy;
        lastCheckTime = currentTime;
        return proxy;
    }

    private static Proxy detectProxy(URL url) {
        Proxy proxy = checkSystemProperties(url);
        if (proxy != null) return proxy;

        proxy = checkEnvironmentVariables(url);
        if (proxy != null) return proxy;

        proxy = checkWindowsSystemProxy(url);
        if (proxy != null) return proxy;

        proxy = checkCommonProxyPorts(url);
        if (proxy != null) return proxy;

        return Proxy.NO_PROXY;
    }

    private static Proxy checkSystemProperties(URL url) {
        String proxyHost;
        String proxyPort;

        if (url.getProtocol().equals("https")) {
            proxyHost = System.getProperty("https.proxyHost");
            proxyPort = System.getProperty("https.proxyPort");
        } else {
            proxyHost = System.getProperty("http.proxyHost");
            proxyPort = System.getProperty("http.proxyPort");
        }

        if (proxyHost != null && !proxyHost.isEmpty()) {
            try {
                int port = (proxyPort != null && !proxyPort.isEmpty()) ?
                    Integer.parseInt(proxyPort) :
                    (url.getProtocol().equals("https") ? 443 : 80);
                if (isProxyReachable(proxyHost, port)) {
                    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, port));
                }
            } catch (Exception e) {
            }
        }

        return null;
    }

    private static Proxy checkEnvironmentVariables(URL url) {
        String proxyUrl = null;

        if (url.getProtocol().equals("https")) {
            proxyUrl = System.getenv("HTTPS_PROXY");
            if (proxyUrl == null) proxyUrl = System.getenv("https_proxy");
        } else {
            proxyUrl = System.getenv("HTTP_PROXY");
            if (proxyUrl == null) proxyUrl = System.getenv("http_proxy");
        }

        if (proxyUrl == null) {
            proxyUrl = System.getenv("ALL_PROXY");
            if (proxyUrl == null) proxyUrl = System.getenv("all_proxy");
        }

        if (proxyUrl != null && !proxyUrl.isEmpty()) {
            return parseProxyUrl(proxyUrl);
        }

        return null;
    }

    private static Proxy parseProxyUrl(String proxyUrl) {
        try {
            if (proxyUrl.startsWith("http://")) {
                proxyUrl = proxyUrl.substring(7);
            } else if (proxyUrl.startsWith("https://")) {
                proxyUrl = proxyUrl.substring(8);
            } else if (proxyUrl.startsWith("socks5://")) {
                proxyUrl = proxyUrl.substring(9);
            }

            String[] parts = proxyUrl.split(":");
            if (parts.length >= 2) {
                String host = parts[0];
                int port = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                if (isProxyReachable(host, port)) {
                    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    private static Proxy checkWindowsSystemProxy(URL url) {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            return null;
        }

        try {
            String proxyEnable = readWindowsRegistry("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings", "ProxyEnable");
            if (!"1".equals(proxyEnable)) {
                return null;
            }

            String proxyServer = readWindowsRegistry("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings", "ProxyServer");
            if (proxyServer == null || proxyServer.isEmpty()) {
                return null;
            }

            String[] proxies = proxyServer.split(";");
            for (String proxy : proxies) {
                if (proxy.startsWith("http=") || proxy.startsWith("https=")) {
                    String[] parts = proxy.split("=");
                    if (parts.length == 2) {
                        String[] hostPort = parts[1].split(":");
                        if (hostPort.length == 2) {
                            String host = hostPort[0];
                            int port = Integer.parseInt(hostPort[1]);
                            if (isProxyReachable(host, port)) {
                                return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                            }
                        }
                    }
                } else if (proxy.contains(":")) {
                    String[] hostPort = proxy.split(":");
                    if (hostPort.length == 2) {
                        String host = hostPort[0];
                        int port = Integer.parseInt(hostPort[1]);
                        if (isProxyReachable(host, port)) {
                            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                        }
                    }
                }
            }
        } catch (Exception e) {
        }

        return null;
    }

    private static String readWindowsRegistry(String key, String value) {
        try {
            Process process = Runtime.getRuntime().exec("reg query \"" + key + "\" /v \"" + value + "\"");
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(value)) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3) {
                        return parts[parts.length - 1];
                    }
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    private static Proxy checkCommonProxyPorts(URL url) {
        for (String host : COMMON_PROXY_HOSTS) {
            for (int port : COMMON_PROXY_PORTS) {
                if (isProxyReachable(host, port)) {
                    return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
                }
            }
        }
        return null;
    }

    private static boolean isProxyReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

package io.axor.runtime;

import java.net.URI;
import java.util.Objects;

public record StreamAddress(String host, int port, String service, String method) {

    public static StreamAddress fromURI(URI uri, int defaultPort, String defaultService,
                                        String defaultMethod) {
        String host = Objects.requireNonNull(uri.getHost(), "uri host: " + uri);
        int port = uri.getPort() == -1 ? defaultPort : uri.getPort();
        String service = uri.getUserInfo() == null ? defaultService : uri.getUserInfo();
        String uriPath = uri.getPath();
        String method = uriPath == null || uriPath.isEmpty() || uriPath.equals("/") ?
                defaultMethod : uriPath.substring(1);
        return new StreamAddress(host, port, service, method);
    }

    public static StreamAddress fromURI(URI uri) {
        return fromComponents(
                Objects.requireNonNull(uri.getUserInfo(), "service"),
                Objects.requireNonNull(uri.getHost(), "host"),
                uri.getPort(),
                Objects.requireNonNull(uri.getPath(), "method").substring(1));
    }

    public static StreamAddress fromString(String address) {
        return fromURI(URI.create("//" + address));
    }

    public static StreamAddress fromComponents(String system, String host, int port, String name) {
        return new StreamAddress(host, port, system, name);
    }

    @Override
    public String toString() {
        return service + "@" + host + ":" + port + "/" + method;
    }

}

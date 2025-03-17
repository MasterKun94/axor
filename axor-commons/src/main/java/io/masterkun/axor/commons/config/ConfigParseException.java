package io.masterkun.axor.commons.config;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigParseException extends RuntimeException {
    private final List<String> pathElems = new ArrayList<>();

    public ConfigParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigParseException(String message) {
        super(message);
    }

    public void addKey(String key) {
        pathElems.addFirst(key);
    }

    public String getPath() {
        return String.join(".", pathElems);
    }

    @Override
    public String getLocalizedMessage() {
        return pathElems.stream().collect(Collectors
                .joining(".", "[", "] " + super.getLocalizedMessage()));
    }
}

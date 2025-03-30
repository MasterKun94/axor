package io.axor.commons.config;

import java.util.ArrayList;
import java.util.List;

public class ConfigParseException extends RuntimeException {
    private final TypeRef type;
    private final List<String> pathElems = new ArrayList<>();

    public ConfigParseException(TypeRef type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public ConfigParseException(TypeRef type, String message) {
        super(message);
        this.type = type;
    }

    public void addKey(String key) {
        pathElems.addFirst(key);
    }

    public String getPath() {
        return String.join(".", pathElems);
    }

    @Override
    public String getMessage() {
        if (getPath().isEmpty()) {
            return type + " parse failed: " + super.getMessage();
        }
        return type + " parse failed: " + String.format(super.getMessage(), getPath());
    }
}

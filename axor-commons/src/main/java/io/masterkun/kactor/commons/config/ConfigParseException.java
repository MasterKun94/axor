package io.masterkun.kactor.commons.config;

public class ConfigParseException extends RuntimeException {

    public ConfigParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigParseException(String message) {
        super(message);
    }
}

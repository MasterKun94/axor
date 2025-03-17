package io.masterkun.kactor.runtime;

public record Status(int code, Throwable cause) {
    public StatusCode codeStatus() {
        return StatusCode.fromCode(code);
    }
}

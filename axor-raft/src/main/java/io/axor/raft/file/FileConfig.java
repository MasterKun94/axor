package io.axor.raft.file;

import io.axor.commons.config.ConfigField;
import io.axor.commons.config.MemorySize;

import java.io.File;

public record FileConfig(@ConfigField(value = "executorThreadNum", fallback = "5")
                         int executorThreadNum,
                         @ConfigField(value = "fileTransferBytesPerSec", fallback = "100m")
                         MemorySize fileTransferBytesPerSec,
                         @ConfigField(value = "baseDir", fallback = "./snapshot")
                         File baseDir) {
}

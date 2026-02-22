package com.bofa.equity;

import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import org.agrona.concurrent.ShutdownSignalBarrier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MediaDriverApp {
    private static final Logger logger = LogManager.getLogger(MediaDriverApp.class);

    public static void main(String[] args) throws IOException {
        final MediaDriver.Context ctx = new MediaDriver.Context()
                .dirDeleteOnStart(true)
                .threadingMode(ThreadingMode.DEDICATED)
                .dirDeleteOnShutdown(true);

        final String aeronDirProp = System.getProperty("aeron.dir");
        if (aeronDirProp != null) {
            ctx.aeronDirectoryName(aeronDirProp);
        }

        try (MediaDriver driver = MediaDriver.launch(ctx)) {
            final String dirName = driver.aeronDirectoryName();
            logger.info("MediaDriver started: aeronDir={}", dirName);

            // Print to stdout so integration tests and scripts can discover the directory.
            System.out.println("AERON_DIR=" + dirName);
            System.out.flush();

            // Write to a well-known file for scripted use.
            Files.writeString(Path.of("/tmp/aeron-trade-engine.dir"), dirName);

            new ShutdownSignalBarrier().await();
            logger.info("MediaDriver shutting down...");
        }
    }
}

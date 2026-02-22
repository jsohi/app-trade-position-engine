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

    public static void main(String[] args) {
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
            // Default uses the JVM temp directory (cross-platform); override with -Daeron.dir.file=<path>.
            final Path aeronDirFile = Path.of(
                    System.getProperty("aeron.dir.file",
                            Path.of(System.getProperty("java.io.tmpdir"), "aeron-trade-engine.dir").toString()));
            try {
                Files.writeString(aeronDirFile, dirName);
                logger.info("Aeron directory written to: {}", aeronDirFile);
            } catch (IOException e) {
                logger.error("Failed to write Aeron directory to {}: {} — scripts relying on this file will not work," +
                        " but the MediaDriver is still running. Use AERON_DIR={} from stdout instead.",
                        aeronDirFile, e.getMessage(), dirName);
            }

            new ShutdownSignalBarrier().await();
            logger.info("MediaDriver shutting down...");
        }
    }
}

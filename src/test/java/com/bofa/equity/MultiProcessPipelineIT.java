package com.bofa.equity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(120)
public class MultiProcessPipelineIT {

    private static final Logger logger = LogManager.getLogger(MultiProcessPipelineIT.class);
    private static final int SEND_COUNT = 10_000;

    @Test
    void threeProcess_allTradesReceivedAndAggregated() throws Exception {
        // Step 1: Launch MediaDriver and wait for it to print its aeron directory.
        Process mediaDriver = launch("com.bofa.equity.MediaDriverApp");
        String aeronDir = readAeronDir(mediaDriver, 15);
        assertNotNull(aeronDir, "MediaDriver did not print AERON_DIR within 15 seconds");

        try {
            // Step 2: Start Receiver first so its subscription is ready when Publisher connects.
            Process receiver = launch("com.bofa.equity.ReceiverApp",
                    "-Daeron.dir=" + aeronDir,
                    "-Dsend.count=" + SEND_COUNT);
            CompletableFuture<String> receiverOut = drainAsync(receiver);

            // Step 3: Start Publisher — it spins on isConnected() until Receiver subscribes.
            Process publisher = launch("com.bofa.equity.PublisherApp",
                    "-Daeron.dir=" + aeronDir,
                    "-Dsend.count=" + SEND_COUNT);
            drainAsync(publisher); // drain to prevent pipe-buffer deadlock

            // Step 4: Publisher exits after sending all trades.
            assertTrue(publisher.waitFor(60, TimeUnit.SECONDS), "Publisher did not finish within 60 seconds");
            assertEquals(0, publisher.exitValue(), "Publisher exited with non-zero code: " + publisher.exitValue());

            // Step 5: Receiver exits after processing all trades and printing stats.
            assertTrue(receiver.waitFor(60, TimeUnit.SECONDS), "Receiver did not finish within 60 seconds");
            assertEquals(0, receiver.exitValue(), "Receiver exited with non-zero code: " + receiver.exitValue());

            // Step 6: Verify histogram was printed.
            String output = receiverOut.get(10, TimeUnit.SECONDS);
            assertTrue(output.contains("END-TO-END LATENCY"),
                    "Receiver stdout did not contain histogram stats. Output was:\n" + output);

        } finally {
            // Step 7: Terminate MediaDriver regardless of test outcome.
            mediaDriver.destroyForcibly();
        }
    }

    /**
     * Launches a JVM subprocess with the current test classpath and JVM opens.
     * Any extra {@code jvmArgs} (e.g. {@code -Daeron.dir=...}) are inserted before the main class.
     */
    private Process launch(String mainClass, String... jvmArgs) throws IOException {
        String java = System.getProperty("java.home") + "/bin/java";
        String classpath = System.getProperty("java.class.path");

        List<String> cmd = new ArrayList<>();
        cmd.add(java);
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add("--add-opens"); cmd.add("java.base/sun.nio.ch=ALL-UNNAMED");
        cmd.add("--add-opens"); cmd.add("java.base/java.lang.reflect=ALL-UNNAMED");
        cmd.add("--add-opens"); cmd.add("java.base/jdk.internal.misc=ALL-UNNAMED");
        for (String arg : jvmArgs) {
            cmd.add(arg);
        }
        cmd.add(mainClass);

        return new ProcessBuilder(cmd).redirectErrorStream(true).start();
    }

    /**
     * Reads the subprocess stdout in a background thread, looking for a line starting with
     * {@code "AERON_DIR="}. Continues draining after the line is found to prevent the subprocess
     * from blocking on a full pipe buffer.
     *
     * @return the extracted aeron directory path, or {@code null} if not found within the timeout
     */
    private String readAeronDir(Process process, int timeoutSecs) throws Exception {
        AtomicReference<String> found = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Thread drainer = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (found.get() == null && line.startsWith("AERON_DIR=")) {
                        found.set(line.substring("AERON_DIR=".length()).trim());
                        latch.countDown();
                    }
                }
            } catch (IOException e) {
                logger.debug("IOException while reading MediaDriver stdout — process may have exited early", e);
            } finally {
                latch.countDown(); // unblock await if process exits without printing AERON_DIR
            }
        });
        drainer.setDaemon(true);
        drainer.start();

        latch.await(timeoutSecs, TimeUnit.SECONDS);
        return found.get();
    }

    /**
     * Drains the subprocess stdout asynchronously into a {@link StringBuilder}.
     * Returns a {@link CompletableFuture} that completes with the full output when the
     * subprocess stream closes.
     */
    private CompletableFuture<String> drainAsync(Process process) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } catch (IOException ignored) {
            }
            return sb.toString();
        });
    }
}

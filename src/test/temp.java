package test;

import main.AggregationServer;
import main.ContentServer;
import main.GETClient;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.Files;

public class temp {
    // Helper method to start a server in a new thread
    private Thread startServer(Runnable serverRunnable) {
        Thread serverThread = new Thread(serverRunnable);
        serverThread.start();
        return serverThread;
    }

    // Helper method to check file content
    private void assertFileContent(Path filePath, Path expectedFilePath) throws Exception {
        String fileContent = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8).trim();
        String expectedContent = new String(Files.readAllBytes(expectedFilePath), StandardCharsets.UTF_8).trim();
        Assert.assertEquals("The stored data should match the expected JSON", expectedContent, fileContent);
    }

    // Helper method to run a content server and return the instance
    private ContentServer runContentServer(String port, String filePath) {
        ContentServer contentServer = new ContentServer();
        Thread serverThread = new Thread(() -> {
            try {
                contentServer.main(new String[]{"localhost:" + port, filePath});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
        return contentServer; // Return the instance for shutdown
    }

    // Helper method to run a GET client and capture output
    private String captureClientOutput(String port, String id) {
        final StringBuilder clientResponse = new StringBuilder();
        Thread clientThread = new Thread(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos);
                System.setOut(ps);

                if (id.equals("null")){
                    GETClient.main(new String[]{"localhost:" + port});
                } else {
                    GETClient.main(new String[]{"localhost:" + port, id});
                }

                clientResponse.append(baos.toString());
                System.setOut(System.out); // Reset System.out
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        clientThread.start();
        try {
            clientThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return clientResponse.toString();
    }

    // Method to overwrite a file with new data
    private void overwriteFile(Path filePath, String newData) throws Exception {
        // Files.write(filePath, newData.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
        Files.writeString(filePath, newData, StandardOpenOption.TRUNCATE_EXISTING);
    }

    // Tests that the aggregation server does not accept a file with no ID.
    @Test
    public void testNoFileID() {
        String port = "1233";
        String contentFilePath = "src/content/noID.txt";
        Path filePath = Paths.get(contentFilePath);

        // Start the aggregation server
        Thread serverThread = startServer(() -> {
            try {
                AggregationServer.main(new String[]{port});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Start the content server
        ContentServer contentServer = runContentServer(port, contentFilePath);

        try {
            Thread.sleep(1000); // Increase wait time for servers to start

            // Capture output
            PrintStream originalOut = System.out;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos);
            System.setOut(ps);

            // Client should receive no data since the file was not accepted
            String clientResponse = captureClientOutput(port, "null");
            System.err.println("CLIENT RESPONSE: " + clientResponse);

            System.err.flush(); // Ensure output is flushed
            String capturedOutput = baos.toString();
            System.err.println("Captured output: " + capturedOutput);

            Assert.assertTrue("Client did not receive error message", clientResponse.contains("HTTP/1.1 404 Not Found"));

            // Restore original System.err
            System.setOut(originalOut);
            System.out.println("Test finished successfully.");

            // Clean up
            contentServer.shutdown(); // Make sure to shutdown the content server
            AggregationServer.shutdown(); // Make sure to shutdown the aggregation server
            serverThread.join(); // Wait for server thread to finish
        } catch (Exception e) {
            Assert.fail("Test failed: " + e.getMessage());
        }
    }


}
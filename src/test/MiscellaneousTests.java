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

public class MiscellaneousTests {
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

    // Test to check if the aggregation server accepts a port number in the input
    @Test
    public void testPortNumber() {
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"1234"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            serverThread.start(); // Start the server in a separate thread
            Thread.sleep(100); // Sleep for a short time
            int p = AggregationServer.getPort();
            Assert.assertEquals("Server should be started on port 1234", 1234, p);
            AggregationServer.shutdown(); // Shut down the server
            serverThread.join(); // Wait for the server thread to finish
            System.out.println("Test finished successfully.");
        } catch (Exception e) {
            Assert.fail("Server startup failed: " + e.getMessage());
        }
    }


    // Test to make sure the client receives the most recently updated file when no ID is specified
    @Test
    public void testMostRecent() {
        Thread serverThread = startServer(() -> {
            try {
                AggregationServer.main(new String[]{"1234"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            Thread.sleep(500);
            ContentServer contentServer1 = runContentServer("1234", "src/content/IDS60902.txt");
            Thread.sleep(500);
            contentServer1.shutdown();

            ContentServer contentServer2 = runContentServer("1234", "src/content/IDS60901.txt");
            Thread.sleep(500);
            contentServer2.shutdown();


            String clientResponse = captureClientOutput("1234", "null");
            String expectedJson = new String(Files.readAllBytes(Paths.get("tests/weather0check.txt")), StandardCharsets.UTF_8);
            Assert.assertTrue("Client did not receive correct JSON", clientResponse.contains(expectedJson));

            AggregationServer.shutdown();
            serverThread.join();
        } catch (Exception e) {
            Assert.fail("Test failed: " + e.getMessage());
        }
    }

    // Test to check if the aggregation server receives newly written data
    // Here the content server starts with the data from IDS60903.txt, and is then overwritten with the data from IDS60904.
    // A client then requests the latest data a second later, and should receive the new data from IDS60904.
    @Test
    public void testOverwriteData() {
        String port = "1234";
        String contentFilePath = "src/content/IDS60903.txt";
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
            Thread.sleep(1000); // Wait for servers to start

            // Save old data
            String OLD_DATA = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            // New data to write
            String NEW_DATA = new String(Files.readAllBytes(Paths.get("src/content/IDS60904.txt")), StandardCharsets.UTF_8);
            overwriteFile(filePath, NEW_DATA); // Overwrite the file with new data

            Thread.sleep(1000); // Give the server a moment to process the new data

            // Capture the client output
            String clientResponse = captureClientOutput(port, "null");
            String expectedJson = new String(Files.readAllBytes(Paths.get("tests/weather4check.txt")), StandardCharsets.UTF_8);
            // Assert that the received data matches the new data
            Assert.assertTrue("Client did not receive the expected JSON data", clientResponse.contains(expectedJson));

            // Reset IDS60903.txt to back to what it was.
            overwriteFile(filePath, OLD_DATA); // Overwrite the file with old data
            Thread.sleep(1000);

            // Clean up
            contentServer.shutdown(); // Make sure to shutdown the content server
            AggregationServer.shutdown(); // Make sure to shutdown the aggregation server
            serverThread.join(); // Wait for server thread to finish


            System.out.println("Test finished successfully.");
        } catch (Exception e) {
            Assert.fail("Test failed: " + e.getMessage());
        }
    }

    // Tests that the aggregation server does not accept a file with no ID.
    @Test
    public void testNoFileID(){
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
            Thread.sleep(500); // Wait for servers to start

            // Client should recieve no data since the file was not accepted.
            String clientResponse = captureClientOutput(port, "null");
            System.out.println("CLIENT RESPONSE CUNT: " + clientResponse);
            String expectedJson = "HTTP/1.1 404 Not Found";
            Assert.assertTrue("Client did not receive error message", clientResponse.contains(expectedJson));

            // Clean up
            contentServer.shutdown(); // Make sure to shutdown the content server
            AggregationServer.shutdown(); // Make sure to shutdown the aggregation server
            serverThread.join(); // Wait for server thread to finish

            System.out.println("Test finished successfully.");
        } catch (Exception e) {
            Assert.fail("Test failed: " + e.getMessage());
        }
    }

}
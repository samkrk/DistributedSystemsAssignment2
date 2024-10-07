package test;

import main.AggregationServer;
import main.ContentServer;
import main.GETClient;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.logging.Logger;

public class ErrorHandlingTests {
    private static final Logger logger = Logger.getLogger(ErrorHandlingTests.class.getName());

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
                // ByteArrayOutputStream baos = new ByteArrayOutputStream();
                // PrintStream ps = new PrintStream(baos);
                // System.setOut(ps);

                if (id.equals("null")) {
                    GETClient.main(new String[]{"localhost:" + port});
                } else {
                    GETClient.main(new String[]{"localhost:" + port, id});
                }

                // clientResponse.append(baos.toString());
                // System.setOut(System.out); // Reset System.out
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

    // Test that the GET client throws error when incorrect input format is provided
    @Test
    public void testInvalidGet(){
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"1234"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        AggregationServer.RemoveTextFiles();

        try {
            serverThread.start(); // Start the server in a separate thread
            Thread.sleep(100); // Sleep for a short time


            String clientResponse = captureClientOutput("1234", "null"); // Server has no content so should receive an error
            String expectedJson = "HTTP/1.1 404 Not Found";
            Assert.assertTrue("Client did not receive error message", clientResponse.contains(expectedJson));

            System.err.println(clientResponse);
            logger.info("HELLO FUCK YOU");


            AggregationServer.shutdown();
            serverThread.join();
        } catch (Exception e) {
            Assert.fail("Test failed: " + e.getMessage());
        }
    }


    // Test invalid file ID in GET client. Verify server returns a 404 Not Found
    @Test
    public void testInvalidID(){
        String port = "1235";
        String contentFilePath = "src/content/IDS60901.txt";
        Path filePath = Paths.get(contentFilePath);

        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{port});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        AggregationServer.RemoveTextFiles();

        ContentServer contentServer = runContentServer(port, contentFilePath);

        try {
            serverThread.start(); // Start the server in a separate thread
            Thread.sleep(100); // Sleep for a short time


            String clientResponse = captureClientOutput(port, "IDS60902"); // Server has no data associated with this ID
            String expectedJson = "HTTP/1.1 404 Not Found";
            Assert.assertTrue("Client did not receive error message", clientResponse.contains(expectedJson));

            AggregationServer.shutdown();
            serverThread.join();
        } catch (Exception e) {
            Assert.fail("Test failed: " + e.getMessage());
        }
    }

    // Test to make sure the Content server handles incorrect input

    // Test to make sure the Content server does not upload a file which contains no ID



}


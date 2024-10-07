
import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.logging.Logger;

public class ErrorHandlingTests {

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

                if (id.equals("null")) {
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

    // Helper function for starting the aggregation server and ensuring no errors.
    private void waitForServerToStart() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    // Test that the GET client throws error when incorrect input format is provided
    @Test
    public void testEmptyGet(){
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
            String expectedJson = "No data in aggregation server";

            System.err.println("Client Response" + clientResponse);
            System.err.flush();

            Assert.assertTrue("Client did not receive error message", clientResponse.contains(expectedJson));



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
        String contentFilePath = "src/main/content/IDS60901.txt";
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

    // Tests that if the aggregation server crashes and restarts, the data persists and is not lost.
    @Test
    public void testShutdownRecovery() {
        String port = "1235";
        String contentFilePath = "src/main/content/IDS60901.txt";

        // Step 1: Start the aggregation server and remove any existing data
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{port});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();
        AggregationServer.RemoveTextFiles();

        // Step 2: Start the content server to provide data
        ContentServer contentServer = runContentServer(port, contentFilePath);
        waitForServerToStart();

        try {
            // Step 3: Shutdown the aggregation server
            AggregationServer.shutdown();
            serverThread.join();

            // Step 4: Restart the aggregation server
            serverThread = new Thread(() -> {
                try {
                    AggregationServer.main(new String[]{port});
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            serverThread.start();
            waitForServerToStart(); // Ensure the server has started fully

            // Step 5: Send a GET request and check if the data is still there
            String clientResponse = captureClientOutput(port, "IDS60901");
            String expectedJson = new String(Files.readAllBytes(Paths.get("src/test/weather0check.txt")), StandardCharsets.UTF_8);

            // Log the responses for debugging
            System.err.println("Client Response: " + clientResponse);
            System.err.println("Expected JSON: " + expectedJson);

            // Step 6: Verify the data was persisted after the server restart
            Assert.assertTrue("Client did not receive the correct data", clientResponse.contains(expectedJson));

            // Step 7: Shutdown the server after the test
            AggregationServer.shutdown();
            serverThread.join();
        } catch (Exception e) {
            Assert.fail("Test failed: " + e.getMessage());
        }
    }



}


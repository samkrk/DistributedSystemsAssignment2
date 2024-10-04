import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.PrintStream;

public class IntegrationTests {

    // Basic start up and shut-down test
    @Test
    public void testServerStartup() {
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            serverThread.start(); // Start the server in a separate thread
            Thread.sleep(100); // Sleep for a short time
            int p = AggregationServer.getPort();
            assertEquals("Server should be started on port 4567", 4567, p);
            AggregationServer.shutdown(); // Shut down the server
            serverThread.join(); // Wait for the server thread to finish
            System.out.println("Test finished successfully.");
        } catch (Exception e) {
            fail("Server startup failed: " + e.getMessage());
        }
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
            assertEquals("Server should be started on port 1234", 1234, p);
            AggregationServer.shutdown(); // Shut down the server
            serverThread.join(); // Wait for the server thread to finish
            System.out.println("Test finished successfully.");
        } catch (Exception e) {
            fail("Server startup failed: " + e.getMessage());
        }
    }


    // Test that the Aggregation Server can receive a PUT from Content Server
    @Test
    public void testPut() {
        // Start Aggregation Server in a separate thread
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"1236"}); // Start Aggregation Server on port 1235
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Start Content Server in a separate thread
        Thread contentThread = new Thread(() -> {
            try {
                ContentServer.main(new String[]{"localhost:1236", "src/content/IDS60901.txt"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            serverThread.start(); // Start the Aggregation Server
            Thread.sleep(1000); // Wait for the server to initialize fully (increase the delay if necessary)

            contentThread.start(); // Start the Content Server
            Thread.sleep(1000);
            ContentServer.shutdown();
            contentThread.join(); // Wait for the Content Server to finish


            // Check if the file was created by AggregationServer
            Path filePath = Paths.get("src/aggr_data/IDS60901.json");
            assertTrue("The JSON file should be created", Files.exists(filePath));

            // Check if the content matches the expected content
            String fileContent = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8).trim(); // Trim whitespace
            Path correctPath = Paths.get("tests/weather0check.txt");
            String expectedJson = new String(Files.readAllBytes(correctPath), StandardCharsets.UTF_8).trim(); // Trim whitespace

            // Assert content equality
            assertEquals("The stored data should match the expected JSON", expectedJson, fileContent);

            // Shutdown aggregation server
            AggregationServer.shutdown();
            serverThread.join(); // Wait for server thread to finish

            System.out.println("Test finished successfully.");
        } catch (Exception e) {
            fail("Test failed: " + e.getMessage());
        }


        // Test that the client can recieve the data with a GET request


    }




}
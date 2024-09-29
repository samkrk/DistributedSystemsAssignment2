import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class AggregationServerTest {

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
            Thread.sleep(1000); // Sleep for 1 second
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
            Thread.sleep(1000); // Sleep for 1 second
            int p = AggregationServer.getPort();
            assertEquals("Server should be started on port 1234", 1234, p);
            AggregationServer.shutdown(); // Shut down the server
            serverThread.join(); // Wait for the server thread to finish
            System.out.println("Test finished successfully.");
        } catch (Exception e) {
            fail("Server startup failed: " + e.getMessage());
        }
    }

    // Test aggregation server can recieve a PUT from content server
    @Test
    public void testPut() {
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"1235"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread contentThread = new Thread(() -> {
            try {
                ContentServer.main(new String[]{"localhost:1235", "src/content/weather0.txt"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            serverThread.start(); // Start the server in a separate thread
            Thread.sleep(1000); // Sleep for 1 second

            contentThread.start();
            contentThread.join();

            Path filePath = Paths.get("src/aggr_data/IDS60901.json");
            String fileContent = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);

            // Expected JSON content
            Path correctPath = Paths.get("tests/weather0check.txt");
            String expectedJson = new String(Files.readAllBytes(correctPath), StandardCharsets.UTF_8);

            assertEquals("The stored data should match the expected JSON", expectedJson, fileContent);

            AggregationServer.shutdown(); // Shut down the server
            serverThread.join(); // Wait for the server thread to finish
            System.out.println("Test finished successfully.");
        } catch (Exception e) {
            fail("Server startup failed: " + e.getMessage());
        }
    }

    // Test to check if the GET client can succesfully recieve the correct json file from the aggregation server
    @Test
    public void testGet() {
        Thread serverThread = new Thread(() -> {
            try {
                AggregationServer.main(new String[]{"1235"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Thread contentThread = new Thread(() -> {
            try {
                ContentServer.main(new String[]{"localhost:1235", "src/content/weather0.txt"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            serverThread.start(); // Start the server in a separate thread
            Thread.sleep(1000); // Sleep for 1 second

            contentThread.start();
            contentThread.join();

            Path filePath = Paths.get("src/aggr_data/IDS60901.json");
            String fileContent = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);

            // Expected JSON content
            Path correctPath = Paths.get("tests/weather0check.txt");
            String expectedJson = new String(Files.readAllBytes(correctPath), StandardCharsets.UTF_8);

            assertEquals("The stored data should match the expected JSON", expectedJson, fileContent);

            AggregationServer.shutdown(); // Shut down the server
            serverThread.join(); // Wait for the server thread to finish
            System.out.println("Test finished successfully.");
        } catch (Exception e) {
            fail("Server startup failed: " + e.getMessage());
        }
    }

}

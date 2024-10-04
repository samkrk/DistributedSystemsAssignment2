import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.PrintStream;

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
        assertEquals("The stored data should match the expected JSON", expectedContent, fileContent);
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
        Files.write(filePath, newData.getBytes(StandardCharsets.UTF_8), StandardOpenOption.WRITE);
    }

    @Test
    public void testOverwriteData() {
        String port = "1234";
        String contentFilePath = "src/content/IDS60903.txt"; // Change to your actual file path
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

        String OLD_DATA = ""; // To store old data
        try {
            Thread.sleep(5000); // Wait for servers to start

            // Save old data
            OLD_DATA = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            System.out.println("Old Data: " + OLD_DATA); // Debug statement for old data

            // New data to write
            String NEW_DATA = new String(Files.readAllBytes(Paths.get("src/content/IDS60904.txt")), StandardCharsets.UTF_8);
            System.out.println("New Data: " + NEW_DATA); // Debug statement for new data

            overwriteFile(filePath, NEW_DATA); // Overwrite the file with new data
            System.out.println("File overwritten with new data."); // Debug statement for file overwrite

            Thread.sleep(5000); // Give the server more time to process the new data

            // Capture the client output
            String clientResponse = captureClientOutput(port, "null");
            String expectedJson = new String(Files.readAllBytes(Paths.get("tests/weather4check.txt")), StandardCharsets.UTF_8);

            System.out.println("Expected JSON: " + expectedJson); // Debug statement for expected JSON
            System.out.println("Client Response: " + clientResponse); // Debug statement for client response

            // Assert that the received data matches the new data
            assertTrue("Client did not receive the expected JSON data", clientResponse.contains(expectedJson));

        } catch (Exception e) {
            fail("Test failed: " + e.getMessage());
        } finally {
            // Clean up
            contentServer.shutdown(); // Make sure to shutdown the content server
            AggregationServer.shutdown(); // Make sure to shutdown the aggregation server
            try {
                serverThread.join(); // Wait for server thread to finish
                // Reset IDS60903.txt back to what it was.
                overwriteFile(filePath, OLD_DATA); // Overwrite the file with old data
                System.out.println("File reset to old data."); // Debug statement for file reset
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}
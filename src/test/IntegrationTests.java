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

public class IntegrationTests {

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

    // Helper method to run a content server
    private Thread runContentServer(String port, String filePath) {
        return startServer(() -> {
            try {
                ContentServer.main(new String[]{"localhost:" + port, filePath});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // Helper method to run a GET client and capture output
    private String captureClientOutput(String port, String id) {
        final StringBuilder clientResponse = new StringBuilder();
        Thread clientThread = new Thread(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos);
                System.setOut(ps);

                GETClient.main(new String[]{"localhost:" + port, id});

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

    // Basic start up and shut-down test
    @Test
    public void testServerStartup() {
        Thread serverThread = startServer(() -> {
            try {
                AggregationServer.main(new String[]{});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            Thread.sleep(100);
            Assert.assertEquals("Server should be started on port 4567", 4567, AggregationServer.getPort());
            AggregationServer.shutdown();
            serverThread.join();
        } catch (Exception e) {
            Assert.fail("Server startup failed: " + e.getMessage());
        }
    }

    // Test that the Aggregation Server can receive a PUT from Content Server
    @Test
    public void testPut() {
        Thread serverThread = startServer(() -> {
            try {
                AggregationServer.main(new String[]{"1236"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            Thread.sleep(1000);
            Thread contentThread = runContentServer("1236", "src/content/IDS60901.txt");
            Thread.sleep(1000);
            ContentServer.shutdown();
            contentThread.join();

            Assert.assertTrue("The JSON file should be created", Files.exists(Paths.get("src/aggr_data/IDS60901.json")));
            assertFileContent(Paths.get("src/aggr_data/IDS60901.json"), Paths.get("tests/weather0check.txt"));

            AggregationServer.shutdown();
            serverThread.join();
        } catch (Exception e) {
            Assert.fail("Test failed: " + e.getMessage());
        }
    }

    // Test that the client can receive the data with a GET request
    @Test
    public void testGet() {
        Thread serverThread = startServer(() -> {
            try {
                AggregationServer.main(new String[]{"1234"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            Thread.sleep(500);
            Thread contentThread = runContentServer("1234", "src/content/IDS60901.txt");
            Thread.sleep(500);
            ContentServer.shutdown();
            contentThread.join();

            String clientResponse = captureClientOutput("1234", "IDS60901");
            String expectedJson = new String(Files.readAllBytes(Paths.get("tests/weather0check.txt")), StandardCharsets.UTF_8);
            Assert.assertTrue("Client did not receive correct JSON", clientResponse.contains(expectedJson));

            AggregationServer.shutdown();
            serverThread.join();
        } catch (Exception e) {
            Assert.fail("Test failed: " + e.getMessage());
        }
    }

    // Test multiple concurrent GET requests
    /*
    @Test
    public void testMultipleGets() {
        Thread serverThread = startServer(() -> {
            try {
                AggregationServer.main(new String[]{"1233"});
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        try {
            Thread.sleep(500); // Wait for server to start

            // Start content server to seed data
            Thread contentThread = runContentServer("1233", "src/content/IDS60901.txt");
            Thread.sleep(500); // Wait for content server to finish seeding data
            ContentServer.shutdown();
            contentThread.join(); // Wait for content server to finish

            // Prepare for multiple client requests
            int numClients = 1; // Number of concurrent clients
            List<Thread> clientThreads = new ArrayList<>();
            List<String> clientResponses = Collections.synchronizedList(new ArrayList<>());

            // Start multiple client threads
            for (int i = 0; i < numClients; i++) {
                Thread clientThread = new Thread(() -> {
                    try {
                        String clientResponse = captureClientOutput("1233", "IDS60901");
                        clientResponses.add(clientResponse); // Store response
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                clientThreads.add(clientThread);
                clientThread.start();
            }

            // Wait for all client threads to complete
            for (Thread clientThread : clientThreads) {
                clientThread.join();
            }

            // Expected JSON content
            String expectedJson = new String(Files.readAllBytes(Paths.get("tests/weather0check.txt")), StandardCharsets.UTF_8);

            // Verify each client's response contains the correct JSON
            for (String response : clientResponses) {
                assertTrue("Client did not receive correct JSON", response.contains(expectedJson));
            }

            // Shutdown the aggregation server
            AggregationServer.shutdown();
            serverThread.join(); // Wait for the server to finish
        } catch (Exception e) {
            fail("Test failed: " + e.getMessage());
        }
    }

     */


}


import org.junit.Assert;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    // Helper method to run a GET client and capture output in a thread-safe manner
    private String captureClientOutputThreadSafe(String port, String id) {
        try {
            PipedOutputStream pipedOut = new PipedOutputStream();
            PipedInputStream pipedIn = new PipedInputStream(pipedOut);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();  // Stream to capture output

            Thread clientThread = new Thread(() -> {
                try {
                    PrintStream originalOut = System.out;
                    PrintStream ps = new PrintStream(pipedOut);  // Custom output stream
                    System.setOut(ps);  // Redirect System.out to this thread's output stream

                    // Run the GET client
                    GETClient.main(new String[]{"localhost:" + port, id});

                    // Close custom PrintStream
                    ps.flush();
                    ps.close();

                    // Reset System.out
                    System.setOut(originalOut);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            clientThread.start();
            clientThread.join();  // Ensure the client thread finishes

            // Read captured output into baos
            int data;
            while ((data = pipedIn.read()) != -1) {
                baos.write(data);
            }
            pipedIn.close();

            // Convert the captured output to a string and return it
            return baos.toString().trim();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



    // Helper function for starting the aggregation server and ensuring no errors.
    private void waitForServerToStart() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
            Thread contentThread = runContentServer("1236", "src/main/content/IDS60901.txt");
            Thread.sleep(1000);
            ContentServer.shutdown();
            contentThread.join();

            Assert.assertTrue("The JSON file should be created", Files.exists(Paths.get("src/main/aggr_data/IDS60901.json")));
            assertFileContent(Paths.get("src/main/aggr_data/IDS60901.json"), Paths.get("src/test/weather0check.txt"));

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
            Thread contentThread = runContentServer("1234", "src/main/content/IDS60901.txt");
            Thread.sleep(500);
            ContentServer.shutdown();
            contentThread.join();

            String clientResponse = captureClientOutput("1234", "IDS60901");
            String expectedJson = new String(Files.readAllBytes(Paths.get("src/test/weather0check.txt")), StandardCharsets.UTF_8);
            Assert.assertTrue("Client did not receive correct JSON", clientResponse.contains(expectedJson));

            AggregationServer.shutdown();
            serverThread.join();
        } catch (Exception e) {
            Assert.fail("Test failed: " + e.getMessage());
        }
    }

    // Test concurrent GET requests
    @Test
    public void testConcurrentGets() throws InterruptedException, IOException {
        String port = "1235";

        // Start the aggregation server
        Thread serverThread = new Thread(() -> AggregationServer.main(new String[]{port}));
        serverThread.start();
        waitForServerToStart();  // Wait for the server to be fully up and running

        // List to store client results
        List<String> clientResponses = Collections.synchronizedList(new ArrayList<>());

        // Define threads for multiple clients
        Thread client1 = new Thread(() -> {
            String response = captureClientOutputThreadSafe(port, "IDS60901");
            clientResponses.add(response);
        });

        Thread client2 = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String response = captureClientOutputThreadSafe(port, "IDS60901");
            clientResponses.add(response);
        });

        Thread client3 = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String response = captureClientOutputThreadSafe(port, "IDS60901");
            clientResponses.add(response);
        });

        // Start the client threads
        client1.start();
        client2.start();
        client3.start();

        // Wait for all clients to finish
        client1.join(5000);
        client2.join(5000);
        client3.join(5000);

        // Shutdown the server
        AggregationServer.shutdown();
        serverThread.join();

        // Check that the responses are as expected
        String expectedJson = Files.readString(Paths.get("src/test/weather0check.txt")).trim();

        // Assert that all client responses contain the expected JSON
        for (String response : clientResponses) {
            System.err.println("Response: " + response);
            Assert.assertTrue("Client did not receive the correct JSON", response.contains(expectedJson));
        }
    }




}

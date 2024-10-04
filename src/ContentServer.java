import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class ContentServer {
    private static LamportClock lamportClock;
    public static String serverName = "localhost"; // Aggregation server address
    public static int port = 4567; // Aggregation server port
    public static String file;
    private static long lastModified; // Store last modified time
    private static boolean running = true; // Track server state

    public static void main(String[] args) throws IOException {
        lamportClock = new LamportClock();

        // Get file from input
        initVariables(args);

        // Initialize last modified time
        lastModified = new File(file).lastModified();

        // Start file monitoring and shutdown command thread
        startFileMonitoring();
        startShutdownListener();
        startHeartbeat();

        sendJsonData();

        System.out.println("Server initialized and monitoring " + file);
    }

    private static void initVariables(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: <servername>:<port> <file>");
            throw new IOException();
        }

        // Get the first argument and split by ':'
        String serverAndPort = args[0];
        String[] parts = serverAndPort.split(":");

        if (parts.length != 2) {
            System.out.println("Invalid format for server and port. Expected format: <servername>:<port>");
            return;
        }

        // Extract the server name and port
        serverName = parts[0];
        port = Integer.parseInt(parts[1]);
        file = args[1];

        // Print to verify the extracted values
        System.out.println("Server Name: " + serverName);
        System.out.println("Port: " + port);
        System.out.println("File: " + file);
    }

    private static void startFileMonitoring() {
        // Start a new thread for file monitoring
        new Thread(() -> {
            while (running) {
                try {
                    // Check the last modified time periodically (every 5 seconds)
                    Thread.sleep(5000); // Adjust the sleep duration as needed
                    long currentModified = new File(file).lastModified();

                    if (currentModified > lastModified) {
                        System.out.println("File modified, resending data...");
                        lastModified = currentModified; // Update last modified time
                        sendJsonData();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    private static void startShutdownListener() {
        // Start a new thread to listen for shutdown command
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (running) {
                String input = scanner.nextLine();
                if (input.equalsIgnoreCase("shutdown")) {
                    running = false;
                    System.out.println("Shutting down the server...");
                }
            }
            scanner.close();
        }).start();
    }

    public static void shutdown() {
        running = false; // Stop the server loop
        System.out.println("Shutting down the server...");
    }

    private static void sendJsonData() {
        // Read and convert text file to JSON
        String jsonData = JSONParser.convertFileToJSON(file);
        if (jsonData == null) {
            System.out.println("Invalid input");
            return;
        }

        int maxTries = 3; // Max number of retry attempts
        int attempts = 0; // Track the number of attempts
        boolean success = false; // Track whether the operation succeeded

        // Retry loop for sending data
        while (attempts < maxTries && !success) {
            attempts++; // Increment attempt count

            try (Socket socket = new Socket(serverName, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Send data to the Aggregation Server
                sendData(in, out, jsonData);

                // Check if sending succeeded
                success = true; // If no exception, assume success

            } catch (IOException e) {
                System.out.println("Error in socket, attempt " + attempts);
                e.printStackTrace();
                if (attempts >= maxTries) {
                    System.out.println("Max retries reached. Unable to send data.");
                    return; // Exit or handle failure
                } else {
                    System.out.println("Retrying...");
                    try {
                        Thread.sleep(1000); // Wait 1 second before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        System.out.println("Data sent successfully.");
    }

    private static void sendData(BufferedReader in, PrintWriter out, String jsonData) throws IOException {
        lamportClock.increment(); // Increment before sending

        // Send PUT request with JSON data
        out.println("PUT");
        out.println(lamportClock.getClock()); // Send clock value
        out.println("User-Agent: ATOMClient/1/0");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + jsonData.length());
        out.println(); // End of headers
        out.println(jsonData); // Send the json data
        out.println(); // End of message
        out.flush();

        // Read response headers
        StringBuilder responseHeaders = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            responseHeaders.append(line).append("\n");
        }

        // Print headers
        System.out.println(responseHeaders);

        // Read response body
        StringBuilder responseBody = new StringBuilder();
        char[] buffer = new char[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            responseBody.append(buffer, 0, bytesRead);
        }
        System.out.println(responseBody);
    }

    private static void startHeartbeat() {
        new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(10000); // Send heartbeat every 10 seconds
                    sendHeartbeat(); // Call method to send heartbeat message
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();
    }

    private static void sendHeartbeat() {
        // Implement the logic to send a heartbeat message to the aggregation server
        try (Socket socket = new Socket(serverName, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            // Send a simple heartbeat message
            out.println("HEARTBEAT");
            out.println(file);
        } catch (IOException e) {
            System.out.println("Error sending heartbeat");
            e.printStackTrace();
        }
    }

}

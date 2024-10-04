import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

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

        // Start separate threads for monitoring file changes,
        // listening for shutdown command, and sending heartbeat messages
        startFileMonitoring();
        startShutdownListener();
        startHeartbeat();

        // Send the initial JSON data to the aggregation server
        sendJsonData();

        System.out.println("Server initialized and monitoring " + file);
    }

    /**
     * Initializes server and file details from command-line arguments.
     *
     * @param args An array containing server address and port as <servername>:<port> and the file path.
     * @throws IOException If the arguments are missing or improperly formatted.
     *
     * Expected Input: args[0] should be <servername>:<port> and args[1] should be the file path to monitor.
     * Special Case: If args are missing, the server fails to start.
     */
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

    /**
     * Monitors the file for any changes. If the file is modified, resends the updated data to the aggregation server.
     *
     * This method runs in a separate thread, checking the file's last modified time periodically.
     * Special Case: The thread runs indefinitely while the server is running, and the file is monitored every 5 seconds.
     */
    private static void startFileMonitoring() {
        new Thread(() -> {
            while (running) {
                try {
                    // Sleep for 5 seconds before checking the file's last modified time
                    Thread.sleep(5000);
                    long currentModified = new File(file).lastModified();

                    // If the file has been modified, resend the data
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

    /**
     * Listens for a "shutdown" command from the terminal to gracefully stop the server.
     *
     * This method runs in a separate thread and continuously checks user input for the "shutdown" command.
     * Special Case: Once "shutdown" is entered, the running state is set to false, terminating the server.
     */
    private static void startShutdownListener() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (running) {
                String input = scanner.nextLine();
                if (input.equalsIgnoreCase("shutdown")) {
                    running = false; // Stop the server loop
                    System.out.println("Shutting down the server...");
                }
            }
            scanner.close();
        }).start();
    }

    /**
     * Sends JSON data to the aggregation server by establishing a socket connection.
     *
     * This method reads the file contents, converts them to JSON, and retries sending the data up to 3 times
     * if the connection fails. It includes a retry mechanism for handling socket connection errors.
     * Special Case: If the maximum number of retries is reached, the server shuts down.
     */
    private static void sendJsonData() {
        String jsonData = JSONParser.convertFileToJSON(file);
        if (jsonData == null) {
            System.out.println("Invalid input");
            return;
        }

        int maxTries = 3; // Retry attempts allowed
        int attempts = 0;
        boolean success = false;

        while (attempts < maxTries && !success) {
            attempts++;
            try (Socket socket = new Socket(serverName, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Send data to the Aggregation Server
                sendData(in, out, jsonData);
                success = true; // Data sent successfully

            } catch (IOException e) {
                System.out.println("Error in socket, attempt " + attempts);
                e.printStackTrace();
                if (attempts >= maxTries) {
                    System.out.println("Max retries reached. Unable to send data.");
                    running = false; // Stop server on failure
                    return;
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

    /**
     * Sends a PUT request containing JSON data and the Lamport clock value to the aggregation server.
     *
     * @param in BufferedReader to read responses from the server.
     * @param out PrintWriter to send data to the server.
     * @param jsonData The JSON-formatted weather data to be sent.
     * @throws IOException If an I/O error occurs while sending or receiving data.
     *
     * Special Case: The method includes sending HTTP-like headers such as User-Agent and Content-Length,
     * along with the JSON data and Lamport clock value. The response from the server is printed.
     */
    private static void sendData(BufferedReader in, PrintWriter out, String jsonData) throws IOException {
        lamportClock.increment(); // Increment before sending

        // Send PUT request with headers and JSON data
        out.println("PUT");
        out.println(lamportClock.getClock()); // Send clock value
        out.println("User-Agent: ATOMClient/1/0");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + jsonData.length());
        out.println(); // End of headers
        out.println(jsonData); // Send the json data
        out.println(); // End of message
        out.flush();

        // Read and print the response from the server
        StringBuilder responseHeaders = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            responseHeaders.append(line).append("\n");
        }
        System.out.println(responseHeaders);
    }

    /**
     * Sends periodic heartbeat messages to the aggregation server to indicate that the content server is still alive.
     *
     * This method runs in a separate thread and sends a "HEARTBEAT" message every 10 seconds.
     * Special Case: If two consecutive attempts to send the heartbeat fail, the server shuts down.
     */
    private static void startHeartbeat() {
        AtomicInteger failed_attempts = new AtomicInteger();
        new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(10000); // Send heartbeat every 10 seconds
                    sendHeartbeat(); // Call method to send heartbeat message
                } catch (InterruptedException e) {
                    failed_attempts.getAndIncrement();
                    if (failed_attempts.get() >= 2) {
                        running = false; // Stop the server loop
                        System.out.println("Shutting down the server...");
                    }
                }
            }
        }).start();
    }

    /**
     * Sends a simple heartbeat message to the aggregation server to signal that the server is still running.
     *
     * Special Case: If the connection fails, an error is logged.
     */
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

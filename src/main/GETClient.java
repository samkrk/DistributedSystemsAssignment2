package main;

import java.io.*;
import java.net.*;

public class GETClient {
    private static LamportClock lamportClock;
    public static String serverName = "localhost"; // Aggregation server address
    public static int port = 4567; // Aggregation server port
    public static String fileID;
    public static String receivedData = "EMPTY";

    public static void main(String[] args) throws IOException {
        lamportClock = new LamportClock();

        // Initialize server name and port based on user input arguments.
        // This method validates the arguments and sets defaults if necessary.
        initVariables(args);

        int maxTries = 3;  // Max number of retry attempts
        int attempts = 0;  // Track the number of attempts
        boolean success = false;  // Track whether the operation succeeded

        // Retry loop for getting data from the server with up to 'maxTries' retries in case of failure
        while (attempts < maxTries && !success) {
            attempts++;  // Increment attempt count

            // Establish a connection with the server, send a GET request, and read the response
            try (Socket socket = new Socket(serverName, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Attempt to retrieve data from the server using helper method
                getData(in, out);

                // If no exception occurs, assume the operation was successful
                success = true;

            } catch (IOException e) {
                // Handle socket errors and retry until maxTries is reached
                System.out.println("Error in socket, attempt " + attempts);
                e.printStackTrace();

                if (attempts >= maxTries) {
                    // If maximum retries reached, exit with failure
                    System.out.println("Max retries reached. Unable to get data.");
                    return;
                } else {
                    // Optionally wait before retrying
                    System.out.println("Retrying...");
                    try {
                        Thread.sleep(1000);  // Wait 1 second before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();  // Restore interrupted thread status
                    }
                }
            }
        }
        // Output message on successful data retrieval
        System.out.println("Data retrieved successfully.");
    }

    /**
     * Initializes server name, port number, and file ID from the command line arguments.
     * If no file ID is provided, a default value ("MOST_RECENT") is used.
     *
     * @param args Command line arguments in the format: <servername>:<port> <fileID>
     * @throws IOException If insufficient arguments are provided or the format is incorrect.
     */
    private static void initVariables(String[] args) throws IOException {
        int no_id_flag = 0;

        // Check if enough arguments are provided, otherwise set defaults
        if (args.length < 2) {
            if (args.length < 1) {
                // If no arguments provided, output usage instructions and throw an exception
                System.out.println("Usage: <servername>:<port> <file>");
                throw new IOException();
            } else {
                // If only server and port provided, set a flag to use default file ID
                no_id_flag = 1;
            }
        }

        // Split the server name and port number
        String serverAndPort = args[0];
        String[] parts = serverAndPort.split(":");

        if (parts.length != 2) {
            // If the format of server:port is incorrect, output error and exit
            System.out.println("Invalid format for server and port. Expected format: <servername>:<port>");
            return;
        }

        // Extract server name and port number
        serverName = parts[0];
        port = Integer.parseInt(parts[1]);

        // If no file ID is provided, set it to "MOST_RECENT"; otherwise use the provided file ID
        if (no_id_flag == 0) {
            fileID = args[1];
        } else {
            fileID = "MOST_RECENT";
        }

        // Print the extracted values for debugging purposes
        System.out.println("Server Name: " + serverName);
        System.out.println("Port: " + port);
        System.out.println("File: " + fileID);
    }

    /**
     * Sends a GET request to the server with the specified file ID and Lamport clock value.
     * The method reads the server's response, including headers and body, and stores the data.
     *
     * @param in  BufferedReader to read the server's response
     * @param out PrintWriter to send the GET request to the server
     * @throws IOException If any I/O error occurs while reading from or writing to the server
     */
    private static void getData(BufferedReader in, PrintWriter out) throws IOException {
        // Increment the Lamport clock before sending the request
        lamportClock.increment();

        // Send a GET request, followed by the current Lamport clock value and the file ID
        out.println("GET");
        out.println(lamportClock.getClock());
        out.println(fileID);  // Either a valid file ID or "MOST_RECENT"

        // Read and accumulate the response headers from the server
        StringBuilder responseHeaders = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            responseHeaders.append(line).append("\n");
        }

        // Print the headers for debugging purposes (optional)
        System.out.println("Received Headers: \n" + responseHeaders);

        // Read the response body from the server
        StringBuilder responseBody = new StringBuilder();
        char[] buffer = new char[1024];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            responseBody.append(buffer, 0, bytesRead);
        }

        // Store the received data and print it
        receivedData = responseBody.toString();
        System.out.println(receivedData);
    }
}

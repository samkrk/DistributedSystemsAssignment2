package main;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AggregationServer {
    private static volatile boolean running = false; // Flag to control server running state
    public static LamportClock lamportClock;
    private static ServerSocket serverSocket;
    private static ExecutorService threadPool;
    public static int port;
    public static final Map<String, Long> lastContactMap = new ConcurrentHashMap<>();

    /**
     * Main method to start the Aggregation Server.
     *
     * This method initializes the server by setting up the Lamport clock,
     * starting the shutdown listener, and setting up the server socket.
     * It also initiates the cleanup thread for old files and listens for client connections.
     *
     * @param args Command-line arguments, where the first argument is the server port (optional).
     */
    public static void main(String[] args){
        startUp(args);
        startFileCleanupThread(); // start clean up
        running = true;
        listen(serverSocket);
    }

    /**
     * Initializes the server, setting up the Lamport clock,
     * fetching the port number, starting the shutdown listener,
     * and initializing the server socket and thread pool.
     *
     * @param args Command-line arguments containing the server port number.
     */
    public static void startUp(String[] args){
        System.out.println("Server is starting up...");
        lamportClock = new LamportClock();  // Starts with clock = 0
        port = getPortNumber(args); // get port number from input
        startShutdownListener();
        startSocket(port); // start socket on given port number
        threadPool = Executors.newCachedThreadPool();  // Use a thread pool to manage clients
    }

    /**
     * Retrieves the port number from the command-line arguments.
     *
     * If no port is provided or an invalid port is specified, it defaults to 4567.
     *
     * @param args Command-line arguments.
     * @return The port number to be used by the server.
     */
    public static int getPortNumber(String[] args) {
        if (args.length > 0) {
            try {
                return Integer.parseInt(args[0]); // Return the port number from command line
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number provided, using default: 4567");
            }
        }
        return 4567; // Default port number
    }

    /**
     * Returns the current port number of the server.
     * Used in testing.
     *
     * @return The port number the server is running on.
     */
    public static int getPort(){
        return port;
    }

    /**
     * Deletes old files that have not received a heartbeat signal within the specified time limit.
     *
     * This method checks the files in the "aggr_data" directory and removes those
     * that haven't been updated within the given threshold.
     *
     * @param timeLimitInSeconds The inactivity time limit in seconds.
     * @throws IOException If an I/O error occurs during file deletion.
     */
    public static void cleanUpFiles(long timeLimitInSeconds) throws IOException {
        Path directory = Paths.get("src/main/aggr_data");
        long thresholdTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(timeLimitInSeconds);

        Files.list(directory).forEach(file -> {
            String fileName = file.getFileName().toString();
            String fileId = fileName.substring(0, fileName.indexOf('.')); // Assuming fileId is part of the file name

            Long lastContactTime = lastContactMap.get(fileId);
            if (lastContactTime == null || lastContactTime < thresholdTime) {
                // If no heartbeat has been received within the time limit, delete the file
                try {
                    Files.delete(file);
                    System.out.println("Deleted inactive server file: " + file.getFileName());
                    lastContactMap.remove(fileId); // Also remove the file from the map
                } catch (IOException e) {
                    System.err.println("Error deleting file: " + file.getFileName());
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Starts a background thread to periodically clean up inactive files.
     *
     * The cleanup thread runs every 30 seconds to delete files that have not been
     * updated within the inactivity threshold.
     */
    public static void startFileCleanupThread() {
        new Thread(() -> {
            while (running) {
                try {
                    cleanUpFiles(30); // Clean up files not accessed for 30 seconds
                    TimeUnit.SECONDS.sleep(30); // Sleep for 30 seconds before running again
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Initializes the server socket and starts listening for client connections on the specified port.
     *
     * @param port The port number to bind the server socket to.
     */
    public static void startSocket(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Aggregation Server started on port " + port);
        } catch (IOException e) {
            System.out.println("Error while creating server socket on port " + port);
            e.printStackTrace();
        }
    }

    /**
     * Listens for incoming client connections and assigns each connection to a new thread.
     *
     * This method runs in an infinite loop, accepting client connections and
     * delegating the handling of each client to a thread from the thread pool.
     *
     * @param serverSocket The server socket on which to listen for connections.
     */
    public static void listen(ServerSocket serverSocket) {
        if (serverSocket == null) {
            return; // Don't proceed if serverSocket wasn't created
        }

        while (running) {
            try {
                // Accept will block, but when serverSocket is closed, it throws an exception.
                Socket clientSocket = serverSocket.accept();
                if (!running) {
                    break;
                }
                threadPool.submit(new ClientHandler(clientSocket));
            } catch (IOException e) {
                if (!running) {
                    break; // Exit the loop if the server is shutting down
                }
                e.printStackTrace(); // Handle other IOExceptions
            }
        }
    }

    /**
     * Starts a background thread to listen for the "shutdown" command from the terminal.
     *
     * This method monitors the terminal for a "shutdown" command and, once detected,
     * triggers the graceful shutdown of the server.
     */
    private static void startShutdownListener() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (running) {
                String input = scanner.nextLine();
                if (input.equalsIgnoreCase("shutdown")) {
                    running = false;
                    shutdown();
                }
            }
            scanner.close();
        }).start();
    }

    /**
     * Gracefully shuts down the server by stopping new client connections and terminating all threads.
     *
     * This method closes the server socket and shuts down the thread pool, ensuring
     * that all active client handler threads are terminated.
     */
    public static void shutdown() {
        System.out.println("Shutting down the server...");
        running = false;

        // Stop accepting new clients
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.out.println("Error while closing the server socket.");
            e.printStackTrace();
        }

        // Shutdown the client handler threads
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();  // Immediately stop all threads
        }

        System.out.println("Server has been shut down.");
    }

    /**
     * Returns the current value of the Lamport clock.
     * Used in testing.
     *
     * @return The current clock value.
     */
    public static int getClock(){
        return lamportClock.getClock();
    }

    /**
     * Clears all the data stored in the Aggregation Server.
     * Used in testing.
     */
    public static void RemoveTextFiles() {
        // Specify the directory path
        String directoryPath = "src/main/aggr_data";

        // Create a File object for the directory
        File folder = new File(directoryPath);

        // List all files in the directory
        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                // Check if the file ends with .txt
                if (file.isFile() && file.getName().endsWith(".json")) {
                    // Delete the file
                    boolean deleted = file.delete();
                    if (deleted) {
                        System.out.println(file.getName() + " was deleted.");
                    } else {
                        System.out.println("Failed to delete " + file.getName());
                    }
                }
            }
        } else {
            System.out.println("The directory is empty or does not exist.");
        }
    }


} // Aggregation Server


class ClientHandler extends AggregationServer implements Runnable{
    private final Socket clientSocket;

    /**
     * ClientHandler handles client connections and processes their requests
     * (PUT, GET, HEARTBEAT). It runs in its own thread, handling input and
     * output streams from the client socket.
     *
     * @param clientSocket The socket connected to the client.
     */
    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    /**
     * This method runs the client handler. It reads the type of request
     * from the client (PUT, GET, or HEARTBEAT), processes the request,
     * and ensures proper synchronization with the server's Lamport clock.
     *
     * If the request type is invalid or empty, it closes the connection
     * gracefully. It also ensures that the client socket is closed at the
     * end of the request processing.
     */
    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // Increment clock on request receipt
            AggregationServer.lamportClock.increment();

            // get request type
            String requestType = in.readLine();

            // Check if the input is null or invalid (this happens when we trigger shutdown)
            if (requestType == null || requestType.trim().isEmpty()) {
                System.out.println("Received invalid or null input. Closing client connection.");
                return; // Exit the handler gracefully
            }

            switch (requestType) {
                case "PUT" -> {
                    System.out.println("Request type: PUT");
                    processPut(in, out);
                }
                case "GET" -> {
                    System.out.println("Request type: GET");
                    processGet(in, out);
                }
                case "HEARTBEAT" -> processHeartbeat(in, out);
                default -> out.println("HTTP/1.1 400 Invalid request type");
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close(); // Ensure the client socket is closed
                }
            } catch (IOException e) {
                System.out.println("Error closing client socket.");
                e.printStackTrace();
            }
        }
    }

    /**
     * Processes the PUT request sent by the client, which includes weather
     * data to be stored on the server. It reads the client's Lamport clock
     * value, updates the server's clock, and stores the weather data in a
     * file named after the weather ID.
     *
     * @param in BufferedReader to read input from the client.
     * @param out PrintWriter to send output back to the client.
     * @throws IOException If an error occurs while reading input or writing output.
     */
    public void processPut(BufferedReader in, PrintWriter out) throws IOException {
        // Read lamport clock value from content server
        String receivedClockString = in.readLine();
        int receivedClock = Integer.parseInt(receivedClockString);
        AggregationServer.lamportClock.update(receivedClock);

        // Read & print headers
        StringBuilder responseHeaders = readHeaders(in);
        System.out.println(responseHeaders);

        // read & print json data
        String jsonString = readJson(in);
        System.out.println("Received JSON data: " + jsonString);
        System.out.println("Lamport Clock before processing PUT: " + AggregationServer.lamportClock.getClock());

        // if json data is empty
        if (jsonString.isEmpty()){
            System.out.println("Empty JSON message");
            out.println("HTTP/1.1 204 No Content");
            return;
        }

        // get weather ID
        String weatherID = getWeatherID(jsonString);
        if (weatherID == null) {
            throw new IllegalArgumentException("ID not found in JSON data");
        }

        try {
            // Store the data in a file named after the weather ID
            Path filePath = Paths.get("src/main/aggr_data/" + weatherID + ".json");

            long timestamp = System.currentTimeMillis(); // Use current time as the timestamp
            lastContactMap.put(weatherID, timestamp);

            // Send success response (HTTP 201 for new, HTTP 200 for update)
            if (!Files.exists(filePath)) {
                // Create the file and write content
                Files.writeString(filePath, jsonString, StandardOpenOption.CREATE);
                out.println("HTTP/1.1 201 Created");
            } else {
                // File already exists, update its content
                Files.writeString(filePath, jsonString, StandardOpenOption.TRUNCATE_EXISTING);
                out.println("HTTP/1.1 200 OK");
            }

            out.println(); // End of headers
            out.println(jsonString); // Send back the JSON data
            out.flush();  // Ensure all the output is flushed
        } catch (Exception e) {
            // Handle JSON parsing error or invalid format
            System.out.println("Invalid JSON format");
            out.println("HTTP/1.1 500 Internal Server Error");
        }
        AggregationServer.lamportClock.increment(); // Increment clock after processing PUT
    }

    /**
     * Processes the GET request sent by the client, either retrieving the
     * most recent weather data or data for a specific weather ID. It reads
     * the client's Lamport clock value, updates the server's clock, and
     * sends the appropriate weather data back to the client.
     *
     * @param in BufferedReader to read input from the client.
     * @param out PrintWriter to send output back to the client.
     * @throws IOException If an error occurs while reading input or writing output.
     */
    public void processGet(BufferedReader in, PrintWriter out) throws IOException {
        // Read lamport clock value from content server
        String receivedClockString = in.readLine();
        int receivedClock = Integer.parseInt(receivedClockString);
        AggregationServer.lamportClock.update(receivedClock);

        // if no ID specified, return latest data
        String id = in.readLine();
        System.out.println("ID: " + id);
        System.out.println("Lamport Clock before processing GET: " + AggregationServer.lamportClock.getClock());

        if (id.equals("MOST_RECENT")){ // Send the most recently updated weather file
            String most_recent_file = getMostRecentFileId();
            Path filePath = Paths.get("src/main/aggr_data/" + most_recent_file + ".json");
            System.out.println(most_recent_file);

            String jsonResponse;

            if (most_recent_file.equals("empty") || !Files.exists(filePath)) {
                // If the aggregation server is empty, send a 404 Not Found response
                jsonResponse = "{\"error\": \"No data in aggregation server\"}";
                out.println("HTTP/1.1 404 Not Found");
                out.println("Content-Type: application/json");

            } else {
                jsonResponse = Files.readString(filePath);
                System.out.println("Sending JSON Data associated with ID :" + id);
                // Send the JSON data to the client
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: application/json");
                out.println("Content-Length: " + jsonResponse.length());
                out.println("No ID Specified. Sending Most Recent Data");
            }
            out.println(); // End of headers
            out.println(jsonResponse);
            out.println(); // End of message
        } else {
            // Retrieve stored JSON data WITH ID
            System.out.println("Searching for ID: " + id);

            Path filePath = Paths.get("src/main/aggr_data/" + id + ".json");
            String jsonResponse;

            if (!Files.exists(filePath)){ // if we cant find this file
                // If the file does not exist, send a 404 Not Found response
                jsonResponse = "{\"error\": \"Not Found\"}";
                out.println("HTTP/1.1 404 Not Found");
                out.println("Content-Type: application/json");
            } else {
                // Read the contents of the JSON file
                jsonResponse = Files.readString(filePath);
                System.out.println("Sending JSON Data associated with ID :" + id);
                // Send the JSON data to the client
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: application/json");
                out.println("Content-Length: " + jsonResponse.length());
            }
            out.println(); // End of headers
            out.println(jsonResponse);
            out.println(); // End of message
        }
        AggregationServer.lamportClock.increment(); // Increment clock after processing GET
    }

    /**
     * Processes the HEARTBEAT request sent by the content server to update
     * the last contact time of a weather station. It reads the weather
     * station ID from the request and updates its timestamp in the
     * lastContactMap.
     *
     * @param in BufferedReader to read input from the client.
     * @param out PrintWriter to send output back to the client.
     * @throws IOException If an error occurs while reading input or writing output.
     */
    private void processHeartbeat(BufferedReader in, PrintWriter out) throws IOException {
        String filePath = in.readLine(); // Read the attached file ID
        int startIndex = filePath.lastIndexOf('/') + 1; // Start after the last '/'
        int endIndex = filePath.lastIndexOf('.'); // End before the '.txt'
        String fileId = filePath.substring(startIndex, endIndex);

        long timestamp = System.currentTimeMillis(); // Use current time as the timestamp

        lastContactMap.put(fileId, timestamp);

        // Respond with acknowledgment
        out.println("HTTP/1.1 200 OK");
        System.out.println("Received heartbeat from " + fileId);
    }

    /**
     * Reads and returns the HTTP headers sent by the client in a request.
     *
     * @param in BufferedReader to read input from the client.
     * @return A StringBuilder containing the request headers.
     * @throws IOException If an error occurs while reading input.
     */
    private StringBuilder readHeaders(BufferedReader in) throws IOException {
        StringBuilder responseHeaders = new StringBuilder();
        String line;
        // Read headers line by line until an empty line is encountered
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            responseHeaders.append(line).append("\n");
        }
        return responseHeaders;
    }

    /**
     * Reads and returns the JSON data sent by the client in a request.
     *
     * @param in BufferedReader to read input from the client.
     * @return A string containing the JSON data.
     * @throws IOException If an error occurs while reading input.
     */
    private String readJson(BufferedReader in) throws IOException {
        StringBuilder jsonData = new StringBuilder();
        String line;
        // Read the entire JSON data from the BufferedReader
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            jsonData.append(line).append("\n"); // Append each line to the JSON data
        }

        // Convert the JSON data to a single string
        return jsonData.toString();
    }

    /**
     * Extracts the weather ID from the JSON data sent by the client.
     *
     * @param jsonString The JSON string containing the weather data.
     * @return The extracted weather ID, or null if the ID is not found.
     */
    private String getWeatherID(String jsonString){
        String weatherID = null;
        // Extract the weather ID from the JSON string
        int idIndex = jsonString.indexOf("\"id\":");
        if (idIndex != -1) {
            int startIndex = jsonString.indexOf("\"", idIndex + 5) + 1; // Skip past "id": "
            int endIndex = jsonString.indexOf("\"", startIndex); // Find the closing quote
            if (endIndex != -1) {
                weatherID = jsonString.substring(startIndex, endIndex); // Extract the ID value
            }
        }
        return weatherID;
    }

    /**
     * Retrieves the ID of the most recent weather data file stored in the
     * aggregation server, based on the last contact timestamp.
     *
     * @return The ID of the most recent weather file, or "empty" if no data is present.
     */
    public static String getMostRecentFileId() {
        if (lastContactMap.isEmpty()){
            System.out.println("No files currently in aggregation server");
            return "empty";
        }
        return lastContactMap.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue()) // Get the entry with the highest contact time (most recent)
                .map(Map.Entry::getKey) // Extract the key (fileId)
                .orElse("empty"); // Return empty if the map is empty
    }
}


import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class AggregationServer {
    private static volatile boolean running = false; // Flag to control server running state
    private static LamportClock lamportClock;
    private static ServerSocket serverSocket;
    public static int port;

    public static void main(String[] args){
        startUp(args);
        running = true;
        listen(serverSocket);
    }

    public static void startUp(String[] args){
        System.out.println("Server is starting up...");
        lamportClock = new LamportClock();  // Starts with clock = 0
        port = getPortNumber(args); // get port number from input
        startFileCleanupThread(); // start clean up
        startSocket(port); // start socket on given port number
        return;
    }

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

    public static int getPort(){
        return port;
    }

    public static void cleanUpFiles(long timeLimitInSeconds) throws IOException {
        Path directory = Paths.get("src/aggr_data");
        long thresholdTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(timeLimitInSeconds);

        Files.list(directory).forEach(file -> {
            try {
                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                Instant lastAccessTime = attrs.lastAccessTime().toInstant();

                if (lastAccessTime.toEpochMilli() < thresholdTime) {
                    Files.delete(file);
                    System.out.println("Deleted file: " + file.getFileName());
                }
            } catch (IOException e) {
                System.err.println("Error processing file: " + file.getFileName());
                e.printStackTrace();
            }
        });
    }

    public static void startFileCleanupThread() {
        new Thread(() -> {
            while (running) {
                try {
                    cleanUpFiles(30); // Clean files not accessed for 30 seconds
                    TimeUnit.SECONDS.sleep(30); // Sleep for 30 seconds before running again
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static void startSocket(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Aggregation Server started on port " + port);
            return; // Return the created server socket
        } catch (IOException e) {
            System.out.println("Error while creating server socket on port " + port);
            e.printStackTrace();
            return; // Return null if socket creation fails
        }
    }

    public static void listen(ServerSocket serverSocket) {
        if (serverSocket == null) {
            return; // Don't proceed if serverSocket wasn't created
        }

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected");
                new Thread(new ClientHandler(clientSocket)).start();
            } catch (IOException e) {
                if (!running) {
                    break; // Exit the loop if server is shutting down
                }
                e.printStackTrace();
            }
        }
    }

    public static void shutdown(){
        running = false;
        shutdown(serverSocket);
    }

    public static void shutdown(ServerSocket serverSocket){
        System.out.println("Shutting down the server...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close(); // Close the server socket
            }
            System.out.println("Server has been shut down.");
        } catch (IOException e) {
            System.out.println("Error while shutting down the server.");
            e.printStackTrace();
        }
    }

    public static int getClock(){
        return lamportClock.getClock();
    }

} // Aggregation Server


class ClientHandler implements Runnable{
    private Socket clientSocket;
    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // get request type
            String requestType = in.readLine();
            System.out.println("Request type: " + requestType);

            if (requestType.equals("PUT")) {
                processPut(in, out);
            } else if (requestType.equals("GET")) {
                processGet(in, out);
            }
            else {
                out.println("HTTP/1.1 400 Invalid request type");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void processPut(BufferedReader in, PrintWriter out) throws IOException {
        // Read & print headers
        StringBuilder responseHeaders = readHeaders(in);
        System.out.println(responseHeaders.toString());

        // read & print json data
        String jsonString = readJson(in);
        System.out.println("Received JSON data: " + jsonString);

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
            Path filePath = Paths.get("src/aggr_data/" + weatherID + ".json");

            // Send success response (HTTP 201 for new, HTTP 200 for update)
            if (!Files.exists(filePath)) {
                // File does not exist yet, so create it and return 201 Created
                Files.write(filePath, jsonString.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
                out.println("HTTP/1.1 201 Created");
                out.println(); // End of headers
                out.println(jsonString);
                out.println(); // End of message
            } else {
                // File already exists, update its contents and return 200 OK
                Files.write(filePath, jsonString.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
                out.println("HTTP/1.1 200 OK");
                out.println(); // End of headers
                out.println(jsonString);
                out.println(); // End of message
            }
        } catch (Exception e) {
            // Handle JSON parsing error or invalid format
            System.out.println("Invalid JSON format");
            out.println("HTTP/1.1 500 Internal Server Error");
        }
        // Handle Lamport clock logic here
    }

    public void processGet(BufferedReader in, PrintWriter out) throws IOException {
        // if no ID specified, return latest data
        String id = in.readLine();
        System.out.println("ID: " + id);
        if (id.equals("MOST_RECENT")){ // Send all weather data?
            String jsonResponse = "{ \"weather\": \"Sunny\" }"; // Example response
            // Send the JSON data to the client
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + jsonResponse.length());
            out.println("No ID Specified. Sending Most Recent Data");
            out.println(); // End of headers
            out.println(jsonResponse); // Send the JSON data
            out.println(); // End of message
        } else {
            // Retrieve stored JSON data WITH ID
            System.out.println("Searching for ID: " + id);

            Path filePath = Paths.get("src/aggr_data/" + id + ".json");
            String jsonResponse;

            if (!Files.exists(filePath)){ // if we cant find this file
                // If the file does not exist, send a 404 Not Found response
                jsonResponse = "{\"error\": \"Not Found\"}";
                out.println("HTTP/1.1 404 Not Found");
                out.println("Content-Type: application/json");
                out.println(); // End of headers
                out.println(jsonResponse); // Send the error message
                out.println(); // End of message
            } else {
                // Read the contents of the JSON file
                jsonResponse = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
                System.out.println("Sending JSON Data:" + jsonResponse);
                // Send the JSON data to the client
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: application/json");
                out.println("Content-Length: " + jsonResponse.length());
                out.println(); // End of headers
                out.println(jsonResponse); // Send the JSON data
                out.println(); // End of message
            }
        }
    }

    private StringBuilder readHeaders(BufferedReader in) throws IOException {
        StringBuilder responseHeaders = new StringBuilder();
        String line;
        // Read headers line by line until an empty line is encountered
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            responseHeaders.append(line).append("\n");
        }
        return responseHeaders;
    }

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

    private String getWeatherID(String jsonString){
        String weatherID = null;
        // Extract the weather ID from the JSON string
        int idIndex = jsonString.indexOf("\"id\":");
        if (idIndex != -1) {
            int startIndex = jsonString.indexOf("\"", idIndex + 5) + 1; // Skip past "id": "
            int endIndex = jsonString.indexOf("\"", startIndex); // Find the closing quote
            if (startIndex != -1 && endIndex != -1) {
                weatherID = jsonString.substring(startIndex, endIndex); // Extract the ID value
            }
        }
        return weatherID;
    }
}


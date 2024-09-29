import java.io.*;
import java.net.Socket;

public class ContentServer {
    private static LamportClock lamportClock;
    public static String serverName = "localhost"; // Aggregation server address
    public static int port = 4567; // Aggregation server port
    public static String file;

    public static void main(String[] args) throws IOException {
        lamportClock = new LamportClock();
        // Get variables from input
        initVariables(args);

        // Read and convert text file to JSON
        String jsonData = JSONParser.convertFileToJSON(file);
        if (jsonData == null){
            System.out.println("Invalid input ");
            return;
        }

        int maxTries = 3;  // Max number of retry attempts
        int attempts = 0;  // Track the number of attempts
        boolean success = false;  // Track whether the operation succeeded

        // Retry loop for sending data
        while (attempts < maxTries && !success) {
            attempts++;  // Increment attempt count

            try (Socket socket = new Socket(serverName, port);
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                // Send data to the Aggregation Server
                sendData(in, out, jsonData);

                // Check if sending succeeded (you can include more checks here)
                success = true;  // If no exception, assume success

            } catch (IOException e) {
                System.out.println("Error in socket, attempt " + attempts);
                e.printStackTrace();
                if (attempts >= maxTries) {
                    System.out.println("Max retries reached. Unable to send data.");
                    return;  // Exit or handle failure
                } else {
                    System.out.println("Retrying...");
                    // wait before retrying
                    try {
                        Thread.sleep(1000);  // Wait 1 second before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();  // Restore interrupt status
                    }
                }
            }
        }

        if (success) {
            System.out.println("Data sent successfully.");
        }
    }

    private static void initVariables(String[] args)throws IOException{
        if (args.length < 2) {
            System.out.println("Usage: <servername>:<port> <file>");
            return;
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

    private static void sendData(BufferedReader in, PrintWriter out, String jsonData) throws IOException {
        lamportClock.increment();  // Increment before sending
        // out.println("LamportClock: " + lamportClock.getClock());  // Send clock value

        // Send PUT request with JSON data
        out.println("PUT");
        out.println("User-Agent: ATOMClient/1/0");
        out.println("Content-Type: application/json");
        out.println("Content-Length: " + jsonData.length());
        out.println(); // End of headers
        out.println(jsonData); // Send the json data
        out.println(); // End of message

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
        // print response body
        System.out.println(responseBody);
    }

}

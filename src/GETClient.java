import java.io.*;
import java.net.*;

/*
Extract Server Name and Port Number, Optionally Station ID:
    Read the server name and port number, and optionally the station ID from input arguments.

*/

public class GETClient {
    public static void main(String[] args) throws IOException {
        LamportClock lamportClock = new LamportClock();  // Starts with clock = 0

        String serverAddress = "localhost";
        int port = 4567;
        if (args.length > 0) {
            String serverInfo = args[0]; // Get the first argument
            String[] parts = serverInfo.split(":"); // Split by ':'

            if (parts.length == 2) {
                serverAddress = parts[0]; // server name
                String p = parts[1]; // port number

                try {
                    int portNumber = Integer.parseInt(p); // Convert to int
                    System.out.println("Server Name: " + serverAddress);
                    System.out.println("Port Number: " + portNumber);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid port number. Please provide a valid integer.");
                }
            } else {
                System.out.println("Invalid format. Please use 'servername:portnumber'.");
                return;
            }
        } else {
            System.out.println("No arguments provided.");
            return;
        }

        String requested_ID = null;
        if (args.length > 1) {
            requested_ID = args[1];
        }

        Socket socket = new Socket(serverAddress, port);
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // lamportClock.increment();  // Increment before sending
            // out.println("LamportClock: " + lamportClock.getClock());  // Send clock value

            // Send GET request
            out.println("GET");
            // Send ID only if it exists
            if (requested_ID != null && !requested_ID.isEmpty()) {
                out.println(requested_ID);
            } else {
                // Send an empty line or some marker to indicate no ID
                out.println("");
            }

            // Read response headers
            StringBuilder responseHeaders = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                responseHeaders.append(line).append("\n");
            }

            // Print headers (optional)
            // System.out.println("Received Headers: \n" + responseHeaders.toString());

            StringBuilder responseBody = new StringBuilder();
            char[] buffer = new char[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                responseBody.append(buffer, 0, bytesRead);
            }

            // Print body
            System.out.println(responseBody.toString());
        } finally {
            socket.close();
        }
    }
}

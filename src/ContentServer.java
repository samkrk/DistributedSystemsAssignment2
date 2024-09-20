import java.io.*;
import java.net.*;

/*
Extract Server Name and Port Number, File to PUT:
    Read server name and port number from input arguments.
    Identify the file to be sent to the aggregation server.

*/


public class ContentServer {
    public static void main(String[] args) throws IOException {
        LamportClock lamportClock = new LamportClock();  // Starts with clock = 0
        // use command line inputs for final thing
        /*
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        String serverAddress = in.readLine();
        String port = in.readLine();
        String file_name = in.readline();
         */
        String serverAddress = "localhost"; // Aggregation server address
        int port = 4567; // Aggregation server port
        String file;
        if (args.length > 0){
            file = args[0];
        } else {
            System.out.println("Please specify name of file to send");
            return;
        }

        Socket socket = new Socket(serverAddress, port);
        try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            // MESSAGE
            // Read and convert text file to JSON
            String jsonData = JSONParser.convertFileToJSON(file);
            if (jsonData == null){
                System.out.println("Invalid input ");
                socket.close();
            }

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

            // RESPONSE
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

        } finally {
            socket.close();
        }
    }
}





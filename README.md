
# Distributed Systems Assignment 2 
## Sam Kirk, a1851921

---
## Overview 
This assignment uses a RESTful API which aggregates weather data from content servers and serves the data to clients who request it. There are three main components to the system, the Aggregation Server, the Content Servers, and the GET Clients.

### Aggregation Server
___
The Aggregation server starts up, receives weather data in a JSON format from the content servers, and waits to serve client requests and content server updates. Upon initialisation, a port number can be specified for the server to listen on. 

Key features:
* Supports an arbitrary port number.
* Removes content servers that have not contacted the aggregation server in 30 seconds. 
* Uses threads to process GET and PUT requests concurrently.
* Makes use of lamport clocks to maintain ordering of events.
* Robust error handling for network errors and invalid input. 
* Can be shutdown by entering 'shutdown' in the terminal. 

### Content Servers 

---
Each content server corresponds to a unique station ID. Upon initialisation, the content server is given the aggregation server and port number, along with the file name which will be converted to JSON format and sent to the aggregation server. 

Key features: 
* Uses the manual JSON parser, 'JSONParser' class to convert the txt file into JSON. 
* Sends 'heartbeat' messages to the aggregation server to ensure constant connection. 
* Robust error handling for invalid arguments, parsing errors and network errors. 
* Retries sending data to the aggregation server 3 times before giving up. 
* Implements lamport clocks.
* Can be shutdown gracefully by typing 'shutdown' into terminal. 

Note that each content server should be run in its own terminal. 
### GET Clients 

--- 
The GET Clients take either one or two parameters. The first parameter is the aggregation server name and port number, so the same as the Content Servers, and the second optional parameter is the file ID which corresponds to the weather data they want to view. A successful request will result in the weather data being printed in the terminal. 

### Look at this line 
In the case where no ID is specified, the client will receive all of the data currently cached in the Aggregation Server.

Key features: 
* Retries 3 times on failures.
* Error handling for socket/network failures and argument errors. 
* Prints JSON data directly to terminal. 
 
--- 
## Build Instructions 
 ___For everything to run smoothly, please use IntelliJ IDEA.___

To run each component individually; 
* Compile 
> make compile 
* Start the Aggregation server
> make run-server 
* Start the Content server
> make run-client SERVER_ADDR=localhost PORT=4567 ID=IDS60901
* Request this file with the GET client (with ID corresponding to what you just uploaded)
> make run-client SERVER_ADDR=localhost PORT=4567 ID=IDS60901 

## Test Instructions 
To compile and run all the tests, run 

> make run-tests

See Makefile for more details.

Listed below is an outline of the suite of tests for the Aggregation Server, Content Server, and Client.
These tests cover basic functionality, error handling, edge cases, and performance, ensuring that the system behaves as expected under various conditions.
The details for each test case can be found in the files in the tests/ directory.

Aggregation Server Tests:
    - Server Startup & Shutdown
    - Basic Functionality
    - Error Handling
    - Concurrency
    - Consistency Management
    - Persistence and Crash Recovery
    - Performance

Content Server Tests:
    - File Handling
    - Request Processing
    - Error Handling
    - Concurrency
    - Performance

Get Client Tests:
    - Basic Functionality
    - Error Handling
    - Input Validation
    - Performance

Additional Tests:
    - Integration Testing
    - Edge Cases

    Aggregation Server Test Cases
    Server Startup & Shutdown

    Test server starts on the correct port: Ensure the server binds to the expected port and is ready to accept connections.
    Test server handles shutdown gracefully: Verify that the server shuts down properly and releases resources when stopped.
    Basic API Functionality

    Test valid GET request for weather data: Send a valid request with a valid weather station ID and check that the server returns correct weather data in JSON format.
    Test valid POST request to update weather data: Send a valid request to update weather data (if applicable), and verify that the data is updated correctly.
    Test GET request without station ID: Check if the server returns an appropriate error message (e.g., 400 Bad Request) or a list of available weather stations.
    Error Handling

    Test invalid URL path: Send an invalid request (e.g., /invalidPath) and verify that the server returns a 404 Not Found.
    Test missing required parameters: Send requests missing required parameters (e.g., station ID) and check if the server returns an appropriate error (e.g., 400 Bad Request).
    Test unsupported HTTP methods: Send unsupported methods (e.g., DELETE or PUT if they aren’t implemented) and verify that the server returns a 405 Method Not Allowed.
    Concurrency

    Test multiple concurrent GET requests: Simulate multiple clients sending simultaneous requests and ensure the server handles them correctly without crashes.
    Test concurrent updates to weather data: If your server allows updates, simulate multiple clients updating data at the same time and verify data consistency.
    Consistency Management (if applicable)

    Test Lamport clock updates: Ensure that updates to weather data maintain consistency and that the Lamport clock values are updated correctly across servers.
    Test synchronization of weather data between multiple aggregation servers: If you have multiple aggregation servers, test that they stay in sync and handle conflict resolution properly.
    Persistence & Crash Recovery

    Test data persistence across server restarts: Ensure that weather data persists in a file and is correctly loaded after the server restarts.
    Test server recovery from a crash: Simulate a server crash and verify that the server recovers gracefully and restores its state from persistent storage.
    Performance

    Test server response time for large requests: Simulate a large dataset or many clients and verify that the server can respond within an acceptable time limit.
    Test server scalability: Measure how the server performs under heavy load (many requests per second).
    Content Server Test Cases
    File Handling

    Test loading valid weather files: Ensure that the content server can read and parse valid weather data files correctly.
    Test handling of missing or invalid files: Verify that the server returns an appropriate error (e.g., 404 Not Found or 400 Bad Request) if a file is missing or the path is invalid.
    Request Processing

    Test valid requests for weather data: Send valid requests for specific weather stations and check if the server returns the correct data.
    Test invalid weather station ID: Send requests with invalid or non-existent weather station IDs and verify that the server handles them gracefully (e.g., 404 Not Found).
    Test weather data format consistency: Ensure that the server returns weather data in the correct JSON format for all valid requests.
    Error Handling

    Test unsupported requests: Send unsupported requests to the content server (e.g., POST or DELETE) and verify that it returns the appropriate error codes.
    Test invalid URL paths: Send requests to invalid paths and ensure the server responds with a 404 Not Found error.
    Concurrency

    Test multiple clients requesting different files: Simulate multiple clients requesting different weather data files at the same time and ensure the server handles them without errors.
    Test multiple clients requesting the same file: Ensure that multiple requests for the same file don’t cause conflicts or race conditions.
    Performance

    Test server performance with large weather files: Measure how long it takes the server to process and return weather data from large files.
    Test scalability under high load: Simulate a large number of clients requesting data and measure how the server scales.
    Client Test Cases
    Basic Functionality

    Test client-server connection: Ensure that the client can establish a connection to the server using the correct URL and port.
    Test valid request for weather data: Send a valid request from the client to the aggregation server and verify that the response is correct.
    Test invalid request: Send an invalid request (e.g., missing station ID) and verify that the client properly handles the server’s error response.
    Test client disconnection: Ensure the client properly disconnects from the server after receiving a response.
    Error Handling

    Test client behavior on server down: Simulate the aggregation server being down and verify that the client handles connection errors gracefully.
    Test handling of server timeouts: Simulate a server timeout and ensure the client doesn’t hang indefinitely.
    Test client with invalid server URL: Ensure the client handles invalid URLs and ports gracefully, returning meaningful error messages.
    Input Validation

    Test invalid weather station ID input: Ensure the client validates user input (if applicable) before sending it to the server.
    Test invalid format of user input: Simulate user input errors (e.g., non-numeric station IDs) and ensure the client properly sanitizes the input.
    Performance

    Test client response time for large data: Measure how quickly the client can process and display large amounts of weather data returned from the server.
    Test client handling of high server load: Simulate the server under heavy load and measure how the client behaves under these conditions.
    Additional Test Categories
    Integration Testing

    Test full interaction between client, aggregation server, and content server: Ensure that the client can request weather data from the aggregation server, which in turn fetches data from the content server, and the whole flow works seamlessly.
    Test aggregation of data from multiple content servers: If your aggregation server collects data from multiple content servers, ensure that the aggregation logic works correctly.
    Edge Cases

    Test missing weather station data: Simulate cases where a weather station has incomplete or missing data and ensure the server handles this appropriately.
    Test malformed JSON response: Simulate a content server sending a malformed JSON and ensure that the aggregation server and client handle this error gracefully.
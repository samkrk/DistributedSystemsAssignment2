
# Distributed Systems Assignment 2 
## Sam Kirk, a1851921

---
## Overview 
This assignment uses a RESTful API which aggregates weather data from content servers and serves the data to clients who request it. There are three main components to the system, the Aggregation Server, the Content Servers, and the GET Clients.
___

### Aggregation Server
The Aggregation server starts up, receives weather data in a JSON format from the content servers, and waits to serve client requests and content server updates. Upon initialisation, a port number can be specified for the server to listen on. 

Key features:
* Supports an arbitrary port number.
* Removes content servers that have not contacted the aggregation server in 30 seconds. 
* Uses threads to process GET and PUT requests concurrently.
* Makes use of lamport clocks to maintain ordering of events.
* Robust error handling for network errors and invalid input. 
* Can be shutdown by entering 'shutdown' in the terminal. 

---
### Content Servers 

Each content server corresponds to a unique station ID. Upon initialisation, the content server is given the aggregation server and port number, along with the file name which will be converted to JSON format and sent to the aggregation server. 

Key features: 
* Uses the manual JSON parser, 'JSONParser' class to convert the txt file into JSON. 
* Sends 'heartbeat' messages to the aggregation server to ensure constant connection. 
* Robust error handling for invalid arguments, parsing errors and network errors. 
* Retries sending data to the aggregation server 3 times before giving up. 
* Implements lamport clocks.
* Can be shutdown gracefully by typing 'shutdown' into terminal. 

Note that each content server should be run in its own terminal. 

--- 
### GET Clients 


The GET Clients take either one or two parameters. The first parameter is the aggregation server name and port number, so the same as the Content Servers, and the second optional parameter is the file ID which corresponds to the weather data they want to view. A successful request will result in the weather data being printed in the terminal. In the case where no ID is specified, the client will receive the most recently added or updated data on the aggregation server. 

Key features: 
* Retries 3 times on failures.
* Error handling for socket/network failures and argument errors. 
* Prints JSON data directly to terminal. 
 
--- 
## Build Instructions 
 ___For everything to run smoothly, please use IntelliJ IDEA.___

To run each component individually; 
* Compile 
``` bash 
make compile
``` 
* Start the Aggregation server
``` bash
make run-server 
``` 
* Start the Content server
``` bash
make run-client SERVER_ADDR=localhost PORT=4567 ID=IDS60901
```
* Request this file with the GET client (with ID corresponding to what you just uploaded)
``` bash
make run-client SERVER_ADDR=localhost PORT=4567 ID=IDS60901 
```
--- 
## Automated Testing 
The automated testing is done using JUnit. There are multiple test files which test separate parts/features of the design. 
* The _IntegrationTests.java_ file tests the basic functionality of the application, ensuring the Aggregation server can start up and shut down, receive files from Content Servers, and process GET requests from clients. 
* The _ErrorHandlingTests.java_ file looks at how the application handles different types of errors, ensuring that all important errors are handled gracefully, and the servers can still operate despite failures. 
* The _EdgeCaseTests.java_ file looks at some interesting edge cases that the servers may have to deal with, and makes sure that everything runs smoothly. 
* Finally, the _MiscellaneousTests.java_ file tests some of the miscellaneous features implemented in the design, such as what happens when a content servers text file is edited, or what happens when a client sends a GET request with no ID. 

See the TestsREADME.md file for more information regarding all the tests and their details. 

To run each of these test files, run the commands;
``` bash
make run-integration-tests
```
``` bash
make run-edge-tests
```
``` bash
make run-error-tests
```
``` bash
make run-misc-tests
``` 


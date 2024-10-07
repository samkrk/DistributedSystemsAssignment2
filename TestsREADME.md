This document outlines and explains the implemented tests.

## Integration Tests 
1. testServerStartup() - Tests if the aggregation server can start up and shut-down on the default port 4567. 
2. testPut() - Tests if the content server can successfully convert plain text to json, and then send it to the aggregation server to be stored in the aggr_data folder. Asserts that the received data is correct.
3. testGet() - Test if the GET Client can successfully receive the correct weather data as specified by the given ID.
4. testMultipleGets() - Test that the Aggregation Server can handle multiple requests at the same time


## Error Handling Tests 
1. testInvalidGet() - Tests that when a client requests a file that doesn't exist, a 404 error is thrown. 
2. 

## Edge Case Tests 


## Miscellaneous Tests 
1. testPortNumber() - Checks that the aggregation server can accept an input for a port number and start a socket on the given port.
2. testMostRecent() - When a client sends a GET request with no ID, they correctly receive the most recent data. Two content servers send their data to the aggregation server. The second server sends its data half a second later, so the client should receive this content servers data. 
3. testFileEdit() - Editing a content servers file automatically re-uploads the data to the aggregation server
   NOT WORKING 
4. testNoFileID() - Ensures that when a content server tries to send a file with no ID, the server does not accept the file. 




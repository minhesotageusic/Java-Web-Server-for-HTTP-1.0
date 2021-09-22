import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.TimeZone;

public class HTTP1RequestHandler implements Runnable {
	// HTTP Status Codes	
	private Dictionary<Integer, String> responseStatusCodes;
	
	// File communication
    private File dir;
    private FileInputStream fileInStream;
    
    // Socket communication
    private final Socket clientSocket;
    private OutputStream outStream;
    private BufferedReader inStream;

    // HTTP requests hash table
    public HTTP1RequestHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
        responseStatusCodes = new Hashtable<Integer, String>();
		responseStatusCodes.put(200, "HTTP/1.0 200 OK");
		responseStatusCodes.put(304, "HTTP/1.0 304 Not Modified");
		responseStatusCodes.put(400, "HTTP/1.0 400 Bad Request");
		responseStatusCodes.put(403, "HTTP/1.0 403 Forbidden");
		responseStatusCodes.put(404, "HTTP/1.0 404 Not Found");
		responseStatusCodes.put(408, "HTTP/1.0 408 Request Timeout");
		responseStatusCodes.put(500, "HTTP/1.0 500 Internal Server Error");
		responseStatusCodes.put(501, "HTTP/1.0 501 Not Implemented");
		responseStatusCodes.put(503, "HTTP/1.0 503 Service Unavailable");
		responseStatusCodes.put(505, "HTTP/1.0 505 HTTP Version Not Supported");
    }

    @Override
    public void run() {
        try {
        	HTTP1Request clientRequest = null;
        	
        	// Create an output stream and an input stream for server to talk to client
        	inStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        	outStream = clientSocket.getOutputStream();

            // Retrieve client request
        	clientRequest = parseRequest();

			// Find out the appropriate function based on request
            if (clientRequest != null) {
	            switch(clientRequest.httpFunction) {
		            case PartialHTTP1Server.HTTP_GET_FUNC:
		            	handleGET(clientRequest);
		            	break;
		            case PartialHTTP1Server.HTTP_HEAD_FUNC:
		            	handleHEAD(clientRequest);
		            	break;
		            case PartialHTTP1Server.HTTP_POST_FUNC:
		            	handleGET(clientRequest);
		            	break;
		            case PartialHTTP1Server.HTTP_PUT_FUNC:
		            	SendMsgToClient(responseStatusCodes.get(501));
		            	break;
		            case PartialHTTP1Server.HTTP_DELETE_FUNC:
		            	SendMsgToClient(responseStatusCodes.get(501));
		            	break;
		            case PartialHTTP1Server.HTTP_LINK_FUNC:
		            	SendMsgToClient(responseStatusCodes.get(501));
		            	break;
		            case PartialHTTP1Server.HTTP_UNLINK_FUNC:
		            	SendMsgToClient(responseStatusCodes.get(501));
		            	break;
		            default:
		            	SendMsgToClient(responseStatusCodes.get(400));
		            	break;
	            }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Close the client's socket and clean up
            try {
            	inStream.close();
            	outStream.close();
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

	/**
	 * Handles HEAD Requests (Like GET but ignore if-last-modify request)
	 * @param clientRequest  The client's HTTP request object
	 */
	private void handleHEAD(HTTP1Request clientRequest) {
    	if (clientRequest == null) return;
    	// Attempt to check file preliminary
    	if ( !CheckResource(clientRequest.resourcePath) ) return;
    	try {
    		fileInStream = new FileInputStream(dir);
	    	//get header
    		String ret = CraftHeader (clientRequest);
	    	//close the stream
	    	fileInStream.close();
	    	//send respond to client
	    	SendMsgToClient (ret);
    	} catch (FileNotFoundException e) {
    		SendMsgToClient(responseStatusCodes.get(403));
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    }

	/**
	 * Handles GET (and POST) Requests
	 * @param clientRequest  The client's HTTP request object
	 */
	private void handleGET(HTTP1Request clientRequest) {
    	if ( clientRequest == null ) return;
    	if ( !CheckResource(clientRequest.resourcePath)) return;
    	//check if mod since the given day against the file
    	if ( clientRequest.ifModSince && dir.lastModified() <= clientRequest.ifModSinceDay ) {
    		//for some reason the tester require
    		//you to send an expire date, so this
    		//us setting up those expire date
    		Date currDate = null;
        	Calendar calendar = null;
        	SimpleDateFormat sdf = null;
        	
        	//set the expire time to be 1 year from access time
        	calendar = Calendar.getInstance();
        	calendar.add(Calendar.YEAR, 1);
        	
        	currDate = calendar.getTime();
        	sdf = new SimpleDateFormat("E, dd MMM yyy HH:mm:ss z");
        	sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        	
    		SendMsgToClient(responseStatusCodes.get(304) + "\r\n" + "Expires: " + sdf.format(currDate) + "\r\n");
			return;
		}
    	try {
    		//create a byte version of the file
    		byte[] byteBody = new byte[(int) dir.length()];
    		FileInputStream dis = new FileInputStream(dir);	
    		//get header
    		String ret = CraftHeader(clientRequest);
    		
    		//read in the file and set it in the byteBody as byte
    		dis.read(byteBody);
    		//add return and carriage
    		ret += "\r\n";
    		//close the file stream
    		dis.close();
    		//send the header to the client
	    	SendMsgToClient (ret);
	    	//send the file content to the client
	    	SendMsgToClient (byteBody);
    	} catch (FileNotFoundException e) {
    		//access denied
    		SendMsgToClient(responseStatusCodes.get(403));
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    }

	/**
	 * Send message to client using byte representation of a file.
	 * @param b  Byte representation of a file
	 */
	private void SendMsgToClient(byte[] b) {
    	if (clientSocket == null || b == null || outStream == null ) return;
		try {
			outStream.write(b);
			outStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

	/**
	 * Send String message to client.
	 * @param msg  String that is going to be sent
	 */
	private void SendMsgToClient (String msg) {
		if (clientSocket == null || msg == null || outStream == null ) return;
		try {
			outStream.write(msg.getBytes());
			outStream.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Get the MIME of the resource
	 * @param resPath  Path of the resource
	 * @return  The resource's MIME type as a String
	 */
	private String GetMIME(String resPath) {
    	if ( resPath == null ) return null;
    	String[] partition = resPath.split("\\.");
    	String ext = null;
    	if (partition == null || partition.length == 0) return null;
    	//if there is no extention, it is most likely octet-stream
    	if ( partition.length == 1 ) return PartialHTTP1Server.MIME_APPLICATION_EXT + "octet-stream";
    	
    	//the extention should be the last entry
    	ext = partition[partition.length - 1];
    	
    	//find the correct extentions
    	if ( ext.equals("txt") || ext.equals("html") )
    		return PartialHTTP1Server.MIME_TEXT_EXT + ext;
    	else if ( ext.equals("gif") || ext.equals("jpeg") || ext.equals("png") )
    		return PartialHTTP1Server.MIME_IMAGE_EXT + ext;
    	else if ( ext.equals("pdf") || ext.equals("x-gzip") || ext.equals("zip") )
    		return PartialHTTP1Server.MIME_APPLICATION_EXT + ext;
    	else 
    		return PartialHTTP1Server.MIME_APPLICATION_EXT + "octet-stream";
    }

	/**
	 * create a header response given the client's request.
	 * @param clientRequest  Client's request object
	 * @return  The header that should be included in the response message
	 */
	private String CraftHeader(HTTP1Request clientRequest) {
    	if ( clientRequest == null ) return null;
    	if ( dir == null ) return null;
    	//initialize
    	Date d = null;
    	Date currDate = null;
    	Calendar calendar = null;
    	SimpleDateFormat sdf = null;
    	String ret = null;
    	
    	//set the expire time to be 1 hour from access time
    	calendar = Calendar.getInstance();
    	calendar.add(Calendar.YEAR, 1);
    	//set time format
    	d = new Date(dir.lastModified());
    	currDate = calendar.getTime();
    	sdf = new SimpleDateFormat("E, dd MMM yyy HH:mm:ss z");
    	sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
    	
    	//create the header
    	ret = responseStatusCodes.get(200) + "\r\n";
    	ret = ret + "Allow: GET, POST, HEAD\r\n";
    	ret = ret + "Expires: " + sdf.format(currDate) + "\r\n";
    	ret = ret + "Content-Type: " + GetMIME(clientRequest.resourcePath) + "\r\n";
    	ret = ret + "Content-Length: " + dir.length() + "\r\n";
    	ret = ret + "Content-Encoding: identity\r\n";
    	ret = ret + "Last-Modified: " + sdf.format(d) + "\r\n";
    	
    	return ret;
    }

	/**
	 * Check if a given resource exists and create a new File for that resource.
	 * @param res Filename of the resource in question
	 * @return true if no error occurs
	 *         false otherwise
	 */
	private boolean CheckResource(String res) {
    	if (res == null) return false;
    	dir = new File("./"+res);
    	//send 404 missing error if file does not exist
    	if (!dir.exists()) {
    		SendMsgToClient(responseStatusCodes.get(404));
    		dir = null;
    		return false;
    	}
    	//send 403 forbidden error if file cannot be read
    	if (!dir.canRead()||!dir.canWrite()) {	//should it be and?
    		//System.out.println("Line 281");
    		SendMsgToClient(responseStatusCodes.get(403));
    		dir = null;
    		return false;
    	}
    	return true;
    }

	/**
	 * Read in request form the client, parse it, and return an HTTP1Request Object.
	 * @return HTTP1Request object representing the user's request
	 * @throws IOException
	 */
	private HTTP1Request parseRequest() throws IOException {
    	//initialize
    	boolean ifModSinceFlag = false;
    	long ifModSinceDay = 0;
    	long startTime = 0;
    	long endTime = 0;
    	long elapsedTime = 0;
    	float vNum = 0;
    	
    	//date format
    	SimpleDateFormat sdf = new SimpleDateFormat("E, dd MMM yyy HH:mm:ss z");
    	
    	//request
    	HTTP1Request requestObject = null;
    	//there are multiple headers client can send,
    	//we need to keep track of those header, 
    	//they are separated by \r\n or \n or \r
    	ArrayList<String> headers = new ArrayList<String>();
    	String[] requestHeader = null;
    	String[] ifModHeader = null;
    	
    	String[] versionParse = null;
    	//String clientRequest = "";	//remove before submission
    	//request
    	
    	//waiting for stream to open
    	startTime = System.nanoTime();
    	while(!inStream.ready()) {
    		endTime = System.nanoTime();
    		elapsedTime = (long) ((endTime - startTime)/1000000);
    		if ( elapsedTime >= 5000) {
    			//System.out.println(elapsedTime);
    			SendMsgToClient(responseStatusCodes.get(408));
    			return null;
    		}
    	}
    	//stream has been open, we can now
    	//read the whole client message
    	while ( inStream.ready() ) {
    		//store each line the client may send
    		//into a headers array string
    		headers.add(inStream.readLine());
    		
    		//clientRequest += headers.get(headers.size()-1);	//remove before submission
    	}
    	//check if the client send an empty message
    	if ( headers.size() == 0 ) { 
    		//send a 400 error on empty request
    		SendMsgToClient(responseStatusCodes.get(400));
    		return null;
    	}
    	
    	//check if request header is empty 
        if (headers.get(0) == null || headers.get(0).equals("")) {
        	//sebd a 400 error on empty request header
        	SendMsgToClient(responseStatusCodes.get(400));
        	return null; 
        }

    	//find the request header, right now it is the first string in headers
    	requestHeader = headers.get(0).split(" ");
    	
    	//find If-Modified-Since header
    	for(int i = 0; i < headers.size(); i ++) {
    		if ( headers.get(i).contains("If-Modified-Since")) {
    			ifModHeader = headers.get(i).split(" ", 2);
    			break;
    		}
    	}
    	
        // client submitted less than 3 tokens, 
    	// in the request header send 400 error
        if (requestHeader.length < 3) {
        	SendMsgToClient(responseStatusCodes.get(400));
        	return null;
        }
        
        //HTTP version checker
        versionParse = requestHeader[2].split("/");
        if ( versionParse == null || versionParse.length != 2 ) {
        	SendMsgToClient(responseStatusCodes.get(400));
        	return null;
        }
        //try to parse the version into a float value
        try{
        	vNum = Float.parseFloat(versionParse[1]);
        	if (vNum > PartialHTTP1Server.HTTP_VERSION) {
        		//version not supported 505 error
        		SendMsgToClient(responseStatusCodes.get(505));
        		return null;
        	}
        }catch(Exception e) {
        	//an error has occurred, i.e. incorrect number format
        	SendMsgToClient(responseStatusCodes.get(400));
        	return null;
        }
        
        //If-Modified-Since error check
        //make sure the if-mod-since header is correct
        if ( ifModHeader != null ) {
        	if ( ifModHeader.length != 2 ) {
        		//request has a if-mod-since header, but it 
        		//is not in the correct format, so send a 
        		//400 error
        		SendMsgToClient(responseStatusCodes.get(400));
        		return null;
        	}
        	try{
        		Date d = sdf.parse(ifModHeader[1]);
        		ifModSinceDay = d.getTime();
        		ifModSinceFlag = true;
        		//System.out.println("mod since: " + ifModSinceDay);
        		//should we also check if the day enter is greater than 
        		//the day of the system?
        	}catch (ParseException e) {
        		//invalid day we do not implement ifModsince
        		ifModSinceDay = 0;
        		ifModSinceFlag = false;
        	}
        }
        
        // Construct an HTTP1Request Object from the parsed tokens
        requestObject = new HTTP1Request(requestHeader[0], requestHeader[1],
                requestHeader[2], ifModSinceFlag , ifModSinceDay);

        return requestObject;
    }
    
    // Inner Class used to represent an HTTP Request
    private static class HTTP1Request {
        private String httpFunction;
        private String resourcePath;
        private String httpProtocol;
        private boolean ifModSince;
        private long ifModSinceDay;
        
        public HTTP1Request(String httpFunction, String resourcePath, String httpProtocol, boolean ifModSince , long ifModSinceDay ) {
            this.httpFunction = httpFunction;
            this.resourcePath = resourcePath;
            this.httpProtocol = httpProtocol;
            this.ifModSince = ifModSince;
            this.ifModSinceDay = ifModSinceDay;
        }
    }
}

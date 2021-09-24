import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
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
		responseStatusCodes.put(204, "HTTP/1.0 204 No Content");
		responseStatusCodes.put(304, "HTTP/1.0 304 Not Modified");
		responseStatusCodes.put(400, "HTTP/1.0 400 Bad Request");
		responseStatusCodes.put(403, "HTTP/1.0 403 Forbidden");
		responseStatusCodes.put(404, "HTTP/1.0 404 Not Found");
		responseStatusCodes.put(405, "HTTP/1.0 405 Method Not Allowed");
		responseStatusCodes.put(408, "HTTP/1.0 408 Request Timeout");
		responseStatusCodes.put(411, "HTTP/1.0 411 Length Required");
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
		            case HTTP1Server.HTTP_GET_FUNC:
		            	handleGET(clientRequest);
		            	break;
		            case HTTP1Server.HTTP_HEAD_FUNC:
		            	handleHEAD(clientRequest);
		            	break;
		            case HTTP1Server.HTTP_POST_FUNC:
		            	handlePOST(clientRequest);
		            	break;
		            case HTTP1Server.HTTP_PUT_FUNC:
		            	SendMsgToClient(responseStatusCodes.get(501));
		            	break;
		            case HTTP1Server.HTTP_DELETE_FUNC:
		            	SendMsgToClient(responseStatusCodes.get(501));
		            	break;
		            case HTTP1Server.HTTP_LINK_FUNC:
		            	SendMsgToClient(responseStatusCodes.get(501));
		            	break;
		            case HTTP1Server.HTTP_UNLINK_FUNC:
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
	
	private void handlePOST(HTTP1Request clientRequest) {
		if ( clientRequest == null ) return;
		//System.out.println("va: " + !CheckResource(clientRequest.resourcePath));    	
    	
		ProcessBuilder pb = null;
		Process process = null;
		BufferedReader stdin = null;
    	ArrayList<String> msg = clientRequest.msg;
    	String [] query = null;
    	String [] content_length = null;
    	String [] content_type = null;
    	String [] resPartition = null;
    	String [] userAgent = null;
    	String [] from = null;    	
    	String finalQuery = "";
    	String inMsg  = null;
    	int contentLengthNum = 0;
    	
    	
    	//System.out.println("client connected to port: " + clientSocket.getPort());
    	
    	boolean blankLine = false;
    			
    	//check for content-length and content-type
    	//look for content-length header
    	content_length = GetLineInRequest(msg, "Content-Length", " ", 2);
    	//check if content_length is not null and contain exactly 
    	//2 elements (ideally ["Content-Length:", "xx"]
    	if ( content_length == null || content_length.length != 2 ) {
    		SendMsgToClient(responseStatusCodes.get(411));
    		return;
    	}
    	//check to see if the content length is a 
    	//numeric value, if not, then send HTTP message
    	//error 411
    	try {
    		//if the numerical value is the 0th element
    		//than 411 will be send 
    		contentLengthNum = Integer.parseInt(content_length[1]);
    	}catch(Exception e) {
    		SendMsgToClient(responseStatusCodes.get(411));
    		return;
    	}
    	
    	//look for content-type in header
    	content_type = GetLineInRequest(msg, "Content-Type", " ", 2);
    	//send an HTTP 500 error message if no content-type
    	//header exist in request
    	if ( content_type == null ) {
    		SendMsgToClient(responseStatusCodes.get(500));
    		return;
    	}
    	
    	//determine if the resource file is a cgi
    	resPartition = clientRequest.resourcePath.split("\\.");
    	if ( resPartition == null || resPartition.length == 0 ) return;
    	if ( !resPartition[resPartition.length - 1].equals("cgi") ) {
    		SendMsgToClient(responseStatusCodes.get(405));
    		return;
    	}
    	if ( !CheckResource(clientRequest.resourcePath)) return;
    	if( !dir.canExecute() ) {
    		SendMsgToClient(responseStatusCodes.get(403));
    		return;
    	}
    	//get from header field if any
    	from = GetLineInRequest(msg, "From:", " ", 2);
    	
    	//get User agent header field if any
    	userAgent = GetLineInRequest(msg, "User-Agent:", " ", 2);
    	
    	//get query request
    	for (int i = 0 ; i < msg.size() ; i ++) {
    		System.out.println("line " + i + ": " + msg.get(i));
    		//find the blank line so the next line is the query
    		if(msg.get(i) != null && msg.get(i).equals("")) {
    			blankLine = true;
    			continue;
    		}
    		if ( blankLine ) {
    			//separate the query by &
    			query = msg.get(i).split("&");
    			break;
    		}
    	}
    	
    	//decode only if we have a query
    	if (query != null) {
    		String tempt = "";
	    	String [] tempt_arr = null;
	    	char [] arr = HTTP1Server.HTTP_CGI_RESERVED_CHAR;
	    	boolean toggle = false;
	    	boolean t = false;
	    	//loop through the query
	    	for(int i = 0 ; i < query.length; i ++) {
	    		//continue if ith query is null
	    		if ( query[i] == null ) continue;
	    		//split the ith query at '=' so we get
	    		//[<NAME>,<VALUE>]
	    		tempt_arr = query[i].split("=");
	    		//continue if it could not have been done
	    		if ( tempt_arr == null || tempt_arr.length != 2) continue;
	    		//get the <value> of the ith query
	    		//which should be the 2nd element
	    		tempt = tempt_arr[1];
	    		if( tempt == null ) continue;
	    		//start decoding by finding the '!' symbol,
	    		//and the char right after it must be a part
	    		//of the reserved characters in arr
	    		for(int c = 0 ; c < tempt.length(); c++) {
	    			//the current character is '!' and 
	    			//we have another character after it
	    			//so we need to check if the character
	    			//after it is in arr.
	    			if(tempt.charAt(c) == '!' && !toggle && c < tempt.length() - 1) {
	    				t = false;
	    				//check if the next character is a reserved character
	    				//if the current character is an escape character
	    				for(int m = 0; m < arr.length; m++) {
	    					if(tempt.charAt(c+1) == arr[m]) {
	    						t = true;
	    						break;
	    					}
	    				}
	    				//if the next character is not a special character
	    				//then continue
	    				if (!t) continue;
	    				//indicate that the current character is an 
	    				//escape character, and the next character
	    				//is a reserved character
	    				toggle = true;
	    				//cut out the escape character and decrement the
	    				//c index variable
	    				tempt = tempt.substring(0, c) + tempt.substring(c+1);
	    				c--;
	    				continue;
	    			}
	    			//check if either the current char is not an escape character
	    			//or if it is that previous character was the actual escape character
	    			//i.e. <Name> = !! <- and the 1st ! is the actual escape character.
	    			//indicate to the next character, that the current character is not 
	    			//an actual escape character.
	    			if (tempt.charAt(c) != '!' || (tempt.charAt(c) == '!' && toggle)) {
	    				toggle = false;
	    			}
	    		}
	    		//queries.put(tempt_arr[0], tempt);
	    		//reconstruct the ith query with the decoded version
	    		query[i] = tempt_arr[0] + "=" + tempt;
	    		//goto the next query in the query arr
	    	}
	    	
	    	//reconstruct the final query message
	    	for(int i = 0 ; i < query.length; i ++) {
	    		if ( i < query.length - 1 ) finalQuery += query[i] + "&";
	    		else finalQuery += query[i];
	    	}
    	}
    	//turn final query into bytes
    	byte[] finalQueryByte = finalQuery.getBytes();
    	//Run the cgi
    	try {
    		String ret = "", content = "";
    		OutputStream bw = null;
    		pb = new ProcessBuilder(dir.getPath());
    		Map<String, String> env = pb.environment();
			//setup environment
	    	env.put("CONTENT_LENGTH", contentLengthNum + "");
	    	env.put("SCRIPT_NAME", clientRequest.resourcePath);
	    	env.put("HTTP_FROM", ((from == null || from.length != 2) ? "" : from[1]));
	    	env.put("HTTP_USER_AGENT", ((userAgent == null || userAgent.length != 2) ? "": userAgent[1]));
	    	//start the process
    		process = pb.start();
			//write the param if any into the process
    		bw = process.getOutputStream();
    		bw.write(finalQueryByte);
    		bw.flush();
    		bw.close();
    		
			//get the output stream of what the cgi file return
			stdin = new BufferedReader(new InputStreamReader(process.getInputStream()));
			inMsg = null;
			//collect the output from the file			
			while((inMsg = stdin.readLine()) != null) {
				content += inMsg + "\n";
			}
			//wait for the process to terminate
			process.waitFor();
			//System.out.println("\r\ncontent: " + content + "\n");
			stdin.close();
			process.destroy();
			if ( content.length() == 0 ) {
				SendMsgToClient(responseStatusCodes.get(204));
			} else {
				//set up return header
				//initialize
		    	Date d = null;
		    	Date currDate = null;
		    	Calendar calendar = null;
		    	SimpleDateFormat sdf = null;
		    	
		    	//set the expire time to be 1 hour from access time
		    	calendar = Calendar.getInstance();
		    	calendar.add(Calendar.YEAR, 1);
		    	//set time format
		    	d = new Date(dir.lastModified());
		    	currDate = calendar.getTime();
		    	sdf = new SimpleDateFormat("E, dd MMM yyy HH:mm:ss z");
		    	sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
				// craft header
				ret = responseStatusCodes.get(200) + "\r\n";
				ret = ret + "Allow: GET, POST, HEAD\r\n";
				ret = ret + "Expires: " + sdf.format(currDate) + "\r\n";
				ret = ret + "Content-Length: " + (content.length()) + "\r\n";
				ret = ret + "Content-Type: text/html\r\n\r\n";
				ret = ret + content;
				
				//send msg to client
				SendMsgToClient(ret);
			}			
		} catch (IOException | InterruptedException e) {
			// TODO Auto-generated catch block
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
			//System.out.println("message for port: " + clientSocket.getPort());
			//System.out.println(b);
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
			//System.out.println("message for port: " + clientSocket.getPort());
			//System.out.println(msg);
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
    	if ( partition.length == 1 ) return HTTP1Server.MIME_APPLICATION_EXT + "octet-stream";
    	
    	//the extention should be the last entry
    	ext = partition[partition.length - 1];
    	
    	//find the correct extentions
    	if ( ext.equals("txt") || ext.equals("html") )
    		return HTTP1Server.MIME_TEXT_EXT + ext;
    	else if ( ext.equals("gif") || ext.equals("jpeg") || ext.equals("png") )
    		return HTTP1Server.MIME_IMAGE_EXT + ext;
    	else if ( ext.equals("pdf") || ext.equals("x-gzip") || ext.equals("zip") )
    		return HTTP1Server.MIME_APPLICATION_EXT + ext;
    	else 
    		return HTTP1Server.MIME_APPLICATION_EXT + "octet-stream";
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
    	
    	//set the expire time to be 1 year from access time
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
	
	private String [] GetLineInRequest(ArrayList<String> headers, String phrase, String splitPattern, int splitCount) {
		if ( splitPattern == null || splitCount < 0 || headers == null || phrase == null ) return null;
    	for(int i = 0; i < headers.size(); i ++) {
    		if ( headers.get(i).contains(phrase)) {
    			return headers.get(i).split(splitPattern, splitCount);
    		}
    	}
    	return null;
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
    	String[] queryHeader = null;
    	
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

        // client submitted less than 3 tokens
    	// in the request header send 400 error
        if (requestHeader.length < 3) {
        	SendMsgToClient(responseStatusCodes.get(400));
        	return null;
        }
        
    	//find If-Modified-Since header
        ifModHeader = GetLineInRequest(headers, "If-Modified-Since", " ", 2);
        
        //HTTP version checker
        versionParse = requestHeader[2].split("/");
        if ( versionParse == null || versionParse.length != 2 ) {
        	SendMsgToClient(responseStatusCodes.get(400));
        	return null;
        }
        
        //try to parse the version into a float value
        try{
        	vNum = Float.parseFloat(versionParse[1]);
        	if (vNum > HTTP1Server.HTTP_VERSION) {
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
        requestObject = new HTTP1Request(headers, requestHeader[0], requestHeader[1],
                requestHeader[2], ifModSinceFlag , ifModSinceDay);
        System.out.println(headers.toString());
        return requestObject;
    }
    
    // Inner Class used to represent an HTTP Request
    private static class HTTP1Request {
    	//represent the msg that was received,
    	//depending on the HTTP function selected, 
    	//the request will require the original msg to process
    	//what it needs. Rather than having separate variable for each 
    	//headers.
    	private ArrayList<String> msg;
        private String httpFunction;
        private String resourcePath;
        private String httpProtocol;
        private String fromsrc;
        private String userAgent;
        private String queryExpression;
        private boolean ifModSince;
        private long ifModSinceDay;
        
        public HTTP1Request(ArrayList<String> msg, String httpFunction, String resourcePath, String httpProtocol, boolean ifModSince , long ifModSinceDay ) {
            this.httpFunction = httpFunction;
            this.resourcePath = resourcePath;
            this.httpProtocol = httpProtocol;
            this.msg = msg;
            this.ifModSince = ifModSince;
            this.ifModSinceDay = ifModSinceDay;
        }
    }
}

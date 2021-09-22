import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;

public class PartialHTTP1Server {
	public static final float HTTP_VERSION = 1.0f;
	
	// HTTP Functions
    public static final String HTTP_GET_FUNC = "GET";
    public static final String HTTP_HEAD_FUNC = "HEAD";
    public static final String HTTP_POST_FUNC = "POST";
    public static final String HTTP_PUT_FUNC = "PUT";
    public static final String HTTP_DELETE_FUNC = "DELETE";
    public static final String HTTP_LINK_FUNC = "LINK";
    public static final String HTTP_UNLINK_FUNC = "UNLINK";

    public static final String MIME_TEXT_EXT = "text/";
    public static final String MIME_IMAGE_EXT = "image/";
    public static final String MIME_APPLICATION_EXT = "application/";
	
    private static int clientCount;
    
    private static int portNo;
    private static ExecutorService threadPool;
    
    private static long init_time;
    private static long delta_time;
    private static long avg_time;

    /**
     * Server listens for client connections, and passes off client sockets to the request handler.
     *
     * @param args The command line argument is the port number that the server will be listening on
     */
    public static void main(String[] args) {
        portNo = Integer.parseInt(args[0]); // Retrieve port number from command line argument
        clientCount = 0;

        // Create a thread pool where corePoolSize = 5 and maxPoolSize = 50
        threadPool = new ThreadPoolExecutor(5,
                50,
                60000,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>()
        );
        		
        try {
        	init_time = System.nanoTime();
        	
            ServerSocket listenerSocket = new ServerSocket(portNo);
            OutputStream outStream = null;
            while (true) {
                //System.out.println("[SERVER] Waiting for client connection...");
                Socket clientSocket = listenerSocket.accept();
                
                //System.out.println("[SERVER] Client successfully connected!\n\n> ");

                // If we have 50 active threads in ThreadPool, reject client connection
                if ( ( (ThreadPoolExecutor) threadPool).getActiveCount() == 50 ) {
                	String s = "HTTP/"+ HTTP_VERSION + " 503 Service Unavailable\r\n";
                	outStream = clientSocket.getOutputStream();
                	outStream.write(s.getBytes());
                	outStream.flush();
                	outStream.close();
                	clientSocket.close();
                	continue;
            	}
                //calculate the avg_time for arrival of each client
                clientCount++;
                delta_time = System.nanoTime() - init_time;
                avg_time = clientCount / delta_time;

                // ThreadPool expands and contracts commensurate with avg rate of incoming connections
                ((ThreadPoolExecutor)threadPool).setKeepAliveTime(avg_time, TimeUnit.MILLISECONDS);

                // Create and execute handler in ThreadPool
                HTTP1RequestHandler clientHandler = new HTTP1RequestHandler(clientSocket);
                
                threadPool.execute(clientHandler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

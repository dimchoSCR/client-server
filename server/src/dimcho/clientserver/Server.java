package dimcho.clientserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;

import dimcho.clientserver.history.NoOpHistoryLogger;

public class Server {
	private static final int LISTEN_PORT = 8000;
	private static final int BUFFER_SIZE = 4000;

	private Thread serverThread;
	private ServerSocketChannel serverChannel;
	private Selector socketSelector;
	private Worker worker;
	
	public Server(String historyLoggerName,String adminName, String adminPass) throws Exception{
		worker = new Worker(historyLoggerName,adminName,adminPass);
	}
	
	public void start(){		
		log("Server starting...");
		serverThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					// Open a server-socket channel and bind it to a port
					serverChannel = ServerSocketChannel.open();
					serverChannel.configureBlocking(false);
					serverChannel.socket().bind(
							new InetSocketAddress(LISTEN_PORT));

					// Register the channel in a Selector
					socketSelector = Selector.open();
					serverChannel.register(socketSelector,
							SelectionKey.OP_ACCEPT);

					log("Server started, waiting for connection on: "
							+ serverChannel.getLocalAddress());

					while (true) {
						// Block waiting for new connection, new incoming data, capacity to write data, new response
						socketSelector.select();

						// Iterate over the set of keys which have been selected
						Iterator<SelectionKey> selectedKeys = socketSelector.selectedKeys().iterator();
						while (selectedKeys.hasNext()) {
							SelectionKey key = selectedKeys.next();
							selectedKeys.remove();

							// Process the event
							if (key.isAcceptable()) {
								ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
								SocketChannel acceptedChannel = serverSocketChannel
										.accept();
								acceptedChannel.configureBlocking(false);
								
								log("Connection accepted from: " + acceptedChannel.getRemoteAddress());								
								
								// prepare the new channel for reading
								SelectionKey acceptedChannelKey = 
									acceptedChannel.register(socketSelector,
										SelectionKey.OP_READ);
								
								// create sessions
								Worker.Session session = new Worker.Session();
								session.setReadBuffer(ByteBuffer.allocate(BUFFER_SIZE));
								session.setWriteBuffer(ByteBuffer.allocate(BUFFER_SIZE));
								session.setKey(acceptedChannelKey);
								acceptedChannelKey.attach(session);
								
								worker.getRequestQueue().add(
										new Worker.Request("welcome\n",session));
								
							} else if (key.isReadable()) {
								SocketChannel channel = (SocketChannel) key.channel();
								Worker.Session session = (Worker.Session) key.attachment();
								ByteBuffer buffer = session.getReadBuffer();

								while (true) {
									int len = channel.read(buffer);
									if(-1 == len){
										key.cancel();
										log("Connection to: " + channel.getRemoteAddress() + " closed");
										break;
									}
									
									if(0 == len){
										break;
									}

									if(len>0){
										byte currentByte;
										for(int i=0;i<buffer.position();i++){
											currentByte = buffer.get(i);
											if ( 10 == currentByte || 13 == currentByte ) {	
												buffer.flip();
												worker.getRequestQueue().add(
														new Worker.Request(
															new String(Arrays.copyOfRange(
																buffer.array(),0, i+1)),session));
												buffer.position(i+1);
												buffer.compact();
												
												// start searching for \n again from the beginning of buffer
												i=0;
											}
										}								
									}									
									
									
									// ignore empty read events
									/*if (0 == len && buffer.position() != 0) {
										byte lastByte = buffer.get(buffer.position() - 1);
										if ( 10 == lastByte || 13 == lastByte ) {
											buffer.flip();
											worker.getRequestQueue().add(
												new Worker.Request(
													new String(Arrays.copyOfRange(
														buffer.array(),buffer.position(), buffer.limit())),session));
											buffer.clear();
											break;
										}										
									}*/
								}
							} else if (key.isWritable()) {
								SocketChannel channel = (SocketChannel) key.channel();
								Worker.Session session = (Worker.Session) key.attachment();
								ByteBuffer buffer = session.getWriteBuffer();
								
								// put next command into buffer only when previous command is completely sent
								if(buffer.remaining() == buffer.capacity()){
									Worker.Response response = worker.getResponseQueue().poll();
									buffer.put(response.getResult().getBytes());
									buffer.flip();
								}
								
								// push to channel until it catches
								while(true){
									int len = channel.write(buffer);
									if(-1 == len){
										key.cancel();
										log("Connection to: " + channel.getRemoteAddress() + " closed");
										break;
									}
									if(0 == len){
										if(!buffer.hasRemaining()){
											key.interestOps(SelectionKey.OP_READ);
											buffer.clear(); // prepare to put next command bytes into buffer
										}
										break;
									}
								}
							}
						}
						
						// prepare channel for new command
						Worker.Response response = worker.getResponseQueue().peek();
						if(null != response){
							SelectionKey responseKey = response.getSession().getKey();
							responseKey.interestOps(SelectionKey.OP_WRITE);
						}
					}
					
				}catch(ClosedSelectorException err){
					
				}catch(Exception err) {
					log(serverThread.getName() + " unexpected error");
					err.printStackTrace();
				}
				log("Server stopped");
			}
		}, "ServerThread");
		serverThread.start();
	}

	 public void stop() throws Exception { 		 
		 worker.stop();
		 socketSelector.close();
		 serverThread.interrupt();
		 serverThread.join();
	 }
	 

	public static void main(String[] args) {
		
		try {
			if(args.length < 2){
				System.out.println("Specify admin name and pass");
				return;
			}
			
			String historyLogger;
			if(args.length > 2){
				historyLogger = args[2];
			}else{
				historyLogger = NoOpHistoryLogger.class.getName();
			}
			
			Server server = new Server(historyLogger,args[0],args[1]);
			server.start();

			log("Enter \"quit\" to stop the server");
			BufferedReader buffReader = getReader(System.in);

			while (true) {
				String command = buffReader.readLine();
				if (command.equalsIgnoreCase("quit")) {
					server.stop();
					break;
				} else {
					log("Invalid command \"" + command + "\" try again.");
				}
			}

			buffReader.close();
		} catch (Exception err) {
			log("Internal server error:...");
			err.printStackTrace();
		}
	}

	public static void log(String message) {
		System.out.println(message);
	}

	public static BufferedReader getReader(InputStream in) {
		return new BufferedReader(new InputStreamReader(in));
	}

	public static Writer getWriter(OutputStream out) {
		return new BufferedWriter(new OutputStreamWriter(out));
	}

}

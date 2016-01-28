package dimcho.clientserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import dimcho.clientserver.history.HistoryLogger;
import dimcho.clientserver.history.PluginLoader;
import dimcho.clientserver.util.StringEncoder;

public class Worker {
	private static final int QUEUE_SIZE = 10;
	
	public static class Session{
		private SelectionKey key;
		private ByteBuffer readBuffer;
		private ByteBuffer writeBuffer;
		private UserManager.User user;
		private String fileName;
		private int offset;
		
		public Session(){
		}

		public SelectionKey getKey() {
			return key;
		}

		public void setKey(SelectionKey key) {
			this.key = key;
		}

		public ByteBuffer getReadBuffer() {
			return readBuffer;
		}

		public void setReadBuffer(ByteBuffer buffer) {
			this.readBuffer = buffer;
		}

		public ByteBuffer getWriteBuffer() {
			return writeBuffer;
		}

		public void setWriteBuffer(ByteBuffer writeBuffer) {
			this.writeBuffer = writeBuffer;
		}
		
		public UserManager.User getUser() {
			return user;
		}

		public void setUser(UserManager.User user) {
			this.user = user;
		}

		public String getFileName() {
			return fileName;
		}

		public void setFileName(String fileName) {
			this.fileName = fileName;
		}

		public int getOffset() {
			return offset;
		}

		public void setOffset(int offset) {
			this.offset = offset;
		}

	}
	
	public static class Request{
		private String command;		
		private Session session;
		
		public Request(String command, Session session) {
			this.command = command;
			this.session = session;
		}

		public String getCommand() {
			return command;
		}
		public void setCommand(String command) {
			this.command = command;
		}
		public Session getSession() {
			return session;
		}
		public void setSession(Session session) {
			this.session = session;
		}		
	}
	
	public static class Response{
		private String result;
		private Session session;
		
		public Response(String result, Session session) {
			this.result = result;
			this.session = session;
		}
		
		public String getResult() {
			return result;
		}
		public void setResult(String result) {
			this.result = result;
		}
		
		public Session getSession() {
			return session;
		}
		
		public void setSession(Session session) {
			this.session = session;
		}	
	}
	
	private BlockingQueue<Request> requestQueue = new ArrayBlockingQueue<Request>(QUEUE_SIZE);
	private BlockingQueue<Response> responseQueue = new ArrayBlockingQueue<Response>(QUEUE_SIZE);
	private StorageManager storage = new StorageManager();
	private UserManager userManager;
	private HistoryLogger historyLogger;
	
	public Worker(String historyLoggerName,String adminName, String adminPass) throws Exception{
		//Load plug-in 
		historyLogger = (HistoryLogger) new PluginLoader().loadPlugin(historyLoggerName);
				
		// create user manager
		userManager= new UserManager(adminName,adminPass);
		userManager.registerAdmin();
		
		log("Worker starting...");
		workerThread.start();
	}
	
	public void stop() throws InterruptedException{
		workerThread.interrupt();
		workerThread.join();
	}
	
	public BlockingQueue<Request> getRequestQueue(){
		return requestQueue;
	}
	
	public BlockingQueue<Response> getResponseQueue() throws Exception{
		return responseQueue;
	}
	
	private Thread workerThread = new Thread(new Runnable() {
		@Override
		public void run() {
			Request request;
			Response response;
			Session session;
			String command;
			String result;
			
			try{
				while(true){
					request = requestQueue.take();
					session = request.getSession();
					command = request.getCommand();
					
					// Remove trailing '\n'
					char lastChar = command.charAt(command.length()-1);
					if(lastChar == '\n' || lastChar == '\r'){
						lastChar = command.charAt(command.length()-2);
						if (lastChar == '\n' || lastChar == '\r'){
							command = command.substring(0, command.length()-2);
						}else{
							command = command.substring(0, command.length()-1);
						}
					}
					
					// process command
					log("command received: '" + command + "'");
					
					if(null == session.getUser() && !command.startsWith("login") && !command.equals("welcome")){
						result = "ERROR: No logged-in user";
					}else{
						if(command.startsWith("welcome")){
							result = welcomeCommand();
						}else if(command.startsWith("help")) {
							result = helpCommand();
						} else if (command.startsWith("login")) {
							result = loginCommand(command,session);
						} else if (command.startsWith("echoall")) {
							result = echoallCommand(command,request.getSession());
						} else if (command.startsWith("echo")) {
							result = echoCommand(command);
						} else if(command.startsWith("create")){
							result = createCommand(command,session);
						} else if(command.startsWith("append")){
								result = appendCommand(command,session);
						} else if(command.startsWith("open")){
							result = openCommand(command, session);
						} else if(command.startsWith("read")){
							result = readCommand(command, session);
						} else if(command.equals("list")){
							result = listCommand(session);
						} else if (command.startsWith("logout")) {
							result = logoutCommand(session);
						} else {
							result = null;
							log("Unexpected command, ignoring");
						}
					}
					
					// process result
					if(null!=result){
						log("command result: " + result);
					
						response = new Response(result+ '\n',session);
						responseQueue.add(response);
						
						session.getKey().selector().wakeup();
					}
				}	
			}catch(InterruptedException err){
				
			}catch (Exception err) {
				log(workerThread.getName() + " unexpected error");
				err.printStackTrace();
			}
			
			log("Worker stopped");
		}
	},"WorkerThread");
	
	private String welcomeCommand(){
		return "Welcome, type 'help' for commands";
	}
	
	private String helpCommand(){
		return "Commands:\n"+
				"\techo <message> : echos the message to current connection\n"+
				"\techoall <message> : echos the message to all connections\n"+
				"\tlogin <user> <pass> : logs in this user\n"+
				"\tlogout : logs out current user\n"+
				"\tupload <file path> : uploads a file to server\n"+
				"\tdownload <file name> : downloads s file from the server\n"+
				"\tlist : lists all files currently on the server";
	}
	
	private String echoCommand(String command) throws Exception {
		return command.substring(command.indexOf(" ") + 1);	
	}
	
	private String echoallCommand(String command,Session session) throws Exception {
		
		String message = echoCommand(command);
		SelectionKey key = session.getKey();
		Selector selector = key.selector();
		Response response;
		
		Iterator<SelectionKey> allKeys = selector.keys().iterator();
		while(allKeys.hasNext()){
			SelectionKey currentKey = allKeys.next();
	
			if(key!=currentKey && currentKey.readyOps() != SelectionKey.OP_ACCEPT){
				response = new Response("Message from '" + session.getUser() + "': " + message + '\n', (Session)currentKey.attachment());
				responseQueue.add(response);
			}
		
		}

 		return "Success";
	}
	
	private String loginCommand(String command, Session session) throws Exception {
		String[] data = command.split(" ");
		
		if(data.length<3){
			return "Insufficient number of arguments";
		}	
		if(null != session.getUser()){
			return "You are already logged in this session";
		}
		
		UserManager.User user = new UserManager.User(data[1], data[2]);
		if (!userManager.validate(user)){
			return "ERROR: Login failed. Wrong username or password.";
		}
		
		session.setUser(user);
		historyLogger.logUserLogin(user.getUsername());
		
		return "You are logged in as: " + user.getUsername();
	}
	
	private String logoutCommand(Session session) throws Exception {
		UserManager.User user = session.getUser();
		if (null == user) {
			return "You have not logged in yet";
		}
		
		session.setUser(null);		
		historyLogger.logUserLogout(user.getUsername());
		
		return "Logout successful";
	}
	
	private String createCommand(String command,Session session) throws Exception{
		String[] parts = command.split(" ");
		String fileName = parts[1];
		int fileSize = Integer.parseInt(parts[2]);
		
		try{
			storage.createFile(fileName, fileSize);
		}catch(Exception error){
			return "ERROR: " + error.getMessage();
		}
		
		session.setFileName(fileName);
		session.setOffset(0);
		
		historyLogger.logFileUpload(session.getUser().getUsername(), fileName);
		return "File created";
	}
	
	private String appendCommand(String command,Session session) throws IOException, Exception{
		String fileData = command.substring(command.indexOf(" ") + 1);
		byte[] decodedData = StringEncoder.decode(fileData);
		
		int offset = session.getOffset();
		storage.writeToFile(session.getFileName(), decodedData, offset);
		session.setOffset(offset + decodedData.length);
		
		return decodedData.length + " bytes written";
		
	}
	
	private String openCommand(String command, Session session) throws IOException{
		String fileName = command.substring(command.indexOf(" ") + 1);
		
		int length;
		try{
			length = storage.checkFile(fileName);
		}catch(Exception error){
			return "ERROR: " + error.getMessage();
		}
		
		session.setFileName(fileName);
		session.setOffset(0);
		
		historyLogger.logFileDownload(session.getUser().getUsername(), fileName);
		return "File opened " + length;		
	}
	
	private String readCommand(String command, Session session) throws IOException{
		int offset = session.getOffset();
		byte[] fileData;
		try{
			fileData = storage.readFromFile(session.getFileName(), offset);
		}catch(Exception error){
			return "ERROR: " + error.getMessage();
		}
		
		if(null == fileData){
			return "DONE: File closed on server";
		}
		
		session.setOffset(offset + fileData.length);
		
		return StringEncoder.code(fileData);
	}
	
	private String listCommand(Session session){
		historyLogger.logFileList(session.getUser().getUsername());
		return storage.listFiles();
	}
	
	public static void log(String message) {
		System.out.println(message);
	}
}

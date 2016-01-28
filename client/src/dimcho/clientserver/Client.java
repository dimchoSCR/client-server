package dimcho.clientserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;

import dimcho.clientserver.util.StringEncoder;

public class Client {
	
	private static final int CHUNK_SIZE = 1000;
	
	private Socket socket;
	private BufferedReader socketReader;
	private BufferedWriter socketWriter;
	private BufferedReader consoleReader;
	private String responseMessage;
	private boolean inCommand;
	private Object readerSemaphore = new Object();
	private boolean readerSemaphoreFlag = false;
	
	public void work(String host,int port){
		
		try{	
			socket = new Socket(host,port);
			log("Connected to " + socket.getRemoteSocketAddress()); 
			
			socketReader = getReader(socket.getInputStream());
			socketWriter = getWriter(socket.getOutputStream());
			consoleReader = getReader(System.in);
			
			Thread readerThread = new Thread(new Runnable() {				
				@Override
				public void run() {
					try{
						while(true){
							responseMessage = socketReader.readLine();
							
							if(null == responseMessage){
								break;
							} 
							// process response
							if (inCommand){
								// notify command thread
								notifyReader();						
							}else {
								System.out.println(responseMessage);
							}	
							
						}
					}catch(SocketException err){
						log("SocketReader closed");
					}catch(Exception err){
						log("Unexpected error while reading message from server");
						err.printStackTrace();
					}
				}
			});
			readerThread.start();
			
			while(true){
				
				String command = consoleReader.readLine();
				if(null == command){
					break;
				}
				if(command.equalsIgnoreCase("quit")){
					socket.close();
					break;
				}else if(command.startsWith("upload")){
					inCommand = true;
					uploadCommand(command);
					inCommand = false;
				}else if(command.startsWith("download")){
					inCommand = true;
					downloadCommand(command);
					inCommand = false;
				}else{
					socketWriter.write(command+"\n");
					socketWriter.flush();
				}
			}			
			
			log("SocketWriter closed");
			readerThread.join();
		}catch(Exception err){
			log("Unexpected error while writing message to server");
			err.printStackTrace();
		}
		
		log("Disconnected");
	}
	
	private void notifyReader(){
		synchronized(readerSemaphore){
			readerSemaphoreFlag = true;
			readerSemaphore.notify();
		}	
	}
	
	private void waitForReader() throws InterruptedException{
		synchronized(readerSemaphore){
			while(!readerSemaphoreFlag){
				readerSemaphore.wait();
			}
			readerSemaphoreFlag = false;
		}
	}
	
	private void uploadCommand(String command) throws IOException,Exception{
		String[] args = command.split(" ");		
		
		if(args.length < 2){
			log("Insufficient number of arguments");
			return;
		}
		
		File file =  new File(args[1]);
		
		// check if file exists
		if(!file.exists()){
			log("File does not exist");
			return;
		}
		
		// get file total size
		long totalSize = file.length();
		
		// create file on server
		socketWriter.write("create " + file.getName() + " " + totalSize + "\n");
		socketWriter.flush();
		
		// wait for server response
		waitForReader();
		if(responseMessage.startsWith("ERROR:")){
			log(responseMessage);
			return;
		}
		
		// read file by chunks
		long currentSize = 0;
		int lastDisplayedPercent = 0;
		FileInputStream fileInputStream = new FileInputStream(args[1]);
		int len;
		byte[] fileData = new byte[CHUNK_SIZE];		
		while(-1 != (len = fileInputStream.read(fileData))){
			
			// encode data to string and send to server
			socketWriter.write("append " + StringEncoder.code(fileData,len) + "\n");
			socketWriter.flush();
			
			// read response
			waitForReader();
			if(responseMessage.startsWith("ERROR:")){
				log(responseMessage);
				fileInputStream.close();
				return;
			}
			
			// print percent
			currentSize+=len;
			int percent = (int)(currentSize*100/totalSize);
			if( percent >= lastDisplayedPercent + 5){
				log(percent + "% done");
				lastDisplayedPercent = percent;
			}
			
		}
		
		log("Success");
		fileInputStream.close();
	}
	
	private void downloadCommand(String command) throws Exception{
		String[] args = command.split(" ");		
		
		if(args.length < 2){
			log("ERROR: Insufficient number of arguments");
			return;
		}
		
		socketWriter.write("open "+ args[1] + "\n");
		socketWriter.flush();
		
		// receive response from server: if the file exists
		waitForReader();
		if(responseMessage.startsWith("ERROR:")){
			log(responseMessage);
			return;
		}
		int totalSize = Integer.parseInt(responseMessage.split(" ")[2]);
	
		// create the download directory and the file on the local file system
		String filePath = "downloads" + File.separator + args[1];
		
		File downloadDir = new File("downloads");
		if(!downloadDir.isDirectory()){
			if(!downloadDir.mkdir()){
				log("ERROR: Directory" +  downloadDir.getAbsolutePath() + " could not be created");
				return;
			}
		}
		
		File file = new File(filePath);		
		if(file.exists()){
			log("ERROR: File " + file.getAbsolutePath() + " already exists");
			return;
		} 
		
		if(!file.createNewFile()){
			log("ERROR: File " + file.getAbsolutePath() + " could not be created");
			return;
		}
		
		// send data
		FileOutputStream fileOutputStream = new FileOutputStream(file);
		byte[] data;
		long currentSize = 0;
		int lastDisplayedPercent = 0;
		while(true){	
			socketWriter.write("read\n");
			socketWriter.flush();
			
			waitForReader();
			
			if(responseMessage.startsWith("ERROR:")){
				log(responseMessage);
				break;
			}
			if(responseMessage.startsWith("DONE:")){
				log("File downloaded successfuly");
				break;
			}
			
			data = StringEncoder.decode(responseMessage);
			fileOutputStream.write(data);
			
			// print percent
			currentSize+=data.length;
			int percent = (int)(currentSize*100/totalSize);
			if( percent >= lastDisplayedPercent + 5){
				log(percent + "% done");
				lastDisplayedPercent = percent;
			}
		}
		
		fileOutputStream.close();
	}
	
	 public static void main(String[] args){	
		 
		// check args
		if(args.length!=2){
			log("usage: java dimcho.clientserver.Client <host> <port>");
			return;
		}	
			
		 new Client().work(args[0],Integer.parseInt(args[1]));
	 }
	 
	 public static void log(String message) {
		System.out.println(message);
	 }
	 
	 public static BufferedReader getReader(InputStream in){
		 return new BufferedReader(new InputStreamReader(in));
	 }
	 
	 public static BufferedWriter getWriter(OutputStream out){
		 return new BufferedWriter(new OutputStreamWriter(out));
	 }
}

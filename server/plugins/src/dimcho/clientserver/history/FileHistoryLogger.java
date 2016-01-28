package dimcho.clientserver.history;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileHistoryLogger implements HistoryLogger {
	private static final String LOG_DIRECTORY = "log";
	
	private DateFormat dateFormater = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
	
	private void logMessage(String userName,String message){
		try{
			File currentUserLogFile = new File(LOG_DIRECTORY + File.separator + userName + ".log");
			if(!currentUserLogFile.exists()){
				currentUserLogFile.createNewFile();
			}
			BufferedWriter fileOut = new BufferedWriter(new FileWriter(currentUserLogFile,true)); 
			fileOut.write(dateFormater.format(new Date()) + " " + userName + " " + message + "\n");
			fileOut.close();			
		}catch(Exception error){
			error.printStackTrace();
		}		
	}
	
	@Override
	public void logUserLogin(String userName)  {
		logMessage(userName, "logged in");
	}

	@Override
	public void logUserLogout(String userName) {
		logMessage(userName, "logged out");
	}
	

	@Override
	public void logFileUpload(String userName, String fileName) {
		logMessage(userName, "uploaded " + fileName);
	}

	@Override
	public void logFileDownload(String userName, String fileName) {
		logMessage(userName, "downloaded " + fileName);
	}

	@Override
	public void logFileList(String userName) {
		logMessage(userName, "listed directory");
	}

}

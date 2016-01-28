package dimcho.clientserver.history;

public interface HistoryLogger {
	void logUserLogin(String userName);
	void logUserLogout(String userName);
	void logFileUpload(String userName,String fileName);
	void logFileDownload(String userName,String fileName);
	void logFileList(String userName);
}

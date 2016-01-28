package dimcho.clientserver.history;

public class NoOpHistoryLogger implements HistoryLogger{

	@Override
	public void logUserLogin(String userName) {
		System.out.println("loged in: " + userName);
	}

	@Override
	public void logUserLogout(String userName) {
		System.out.println("loged out: " + userName);
	}

	@Override
	public void logFileUpload(String userName, String fileName) {
		System.out.println("user: " + userName + " uploaded: " + fileName);
	}

	@Override
	public void logFileDownload(String userName, String fileName) {
		System.out.println("user: " + userName + " downloaded: " + fileName);
	}

	@Override
	public void logFileList(String userName) {
		System.out.println("user: " + userName + " listed");
	}

	

}

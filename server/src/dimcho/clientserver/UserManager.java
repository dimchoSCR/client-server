package dimcho.clientserver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import dimcho.clientserver.remote.UserManagerAdmin;

public class UserManager {
	private static String USER_STORAGE = "passvd.txt";
	
	public static class User{
		private String username;
		private String password;
		
		public User(String username, String password) {
			this.username = username;
			this.password = password;
		}
		
		public String getUsername(){
			return username;
		}
		
		public void setPassword(String password) {
			this.password = password;
		}

		@Override
		public String toString() {
			return username;
		}
	}	
	
	private static class UserManagerAdminImpl implements UserManagerAdmin{

		private UserManager userManager;
		
		public UserManagerAdminImpl(UserManager userManager) {
			this.userManager = userManager;
		}
		
		@Override
		public String[] getRegisteredUsers(String adminName, String adminPass) throws RemoteException {
			// validate admin user and pass
			if(!adminName.equals(userManager.adminName) || !adminPass.equals(userManager.adminPass)){
				throw new RemoteException("Wrong admin username or password");
			}
			
			List<String> usernames = new LinkedList<String>();
			try {
				for (User user : userManager.users) {
					usernames.add(user.getUsername());
				}				
			} catch (Exception e) {
				throw new RemoteException("Error while listing users",e);
			}
		
			return usernames.toArray(new String[usernames.size()]);
		}

		@Override
		public void deleteUser(String adminName, String adminPass,String userName) throws RemoteException {
			
			if(!adminName.equals(userManager.adminName) || !adminPass.equals(userManager.adminPass)){
				throw new RemoteException("Wrong admin username or password");
			}
			
			try {
				for (User user : userManager.users) {
					if(user.getUsername().equals(userName)){
						userManager.users.remove(user);
						break;
					}
				}
				userManager.storeUsers(userManager.users);				
			} catch (Exception e) {
				throw new RemoteException("Error while deleting user",e);
			}			
		}

		@Override
		public void changeUserPassword(String adminName, String adminPass,String userName, String password)
				throws RemoteException {
			
			if(!adminName.equals(userManager.adminName) || !adminPass.equals(userManager.adminPass)){
				throw new RemoteException("Wrong admin username or password");
			}
			
			try {
				for (User user : userManager.users) {
					if(user.getUsername().equals(userName)){
						user.setPassword(password);
					}
				}
				userManager.storeUsers(userManager.users);				
			} catch (Exception e) {
				throw new RemoteException("Error while deleting user",e);
			}	
			
		}
	}	
	
	private List<User> users = null;
	private String adminName;
	private String adminPass;
	
	public UserManager(String adminName, String adminPass) throws Exception{
		this.adminName = adminName;
		this.adminPass = adminPass;
		users = loadUsers();
	}
	
	public void registerAdmin() throws RemoteException{
		UserManagerAdminImpl adminImpl = new UserManagerAdminImpl(this);
		UserManagerAdmin stub = (UserManagerAdmin)UnicastRemoteObject.exportObject(adminImpl,0);
		Registry registry = LocateRegistry.getRegistry();
        registry.rebind("UserManagerAdmin", stub);
	}

	// return false if user pass is wrong, return true if fine
	public boolean validate(User user) throws Exception {
		// check if user exists
		for(User currentUser : users){
			if(currentUser.username.equals(user.username)){
				if(currentUser.password.equals(user.password)){
					return true;
				}
				return false;
			}
		}
		
		// create new user and store to file
		users.add(user);
		storeUsers(users);
		return true;
	}
	
	private void storeUsers(List<User> users) throws Exception{
		FileWriter fileOut = new FileWriter(USER_STORAGE);
		for(User user: users){
			fileOut.write(user.username + " " + user.password + "\n" );
		}
		fileOut.close();
	}
	
	private List<User> loadUsers() throws Exception{
		// create file if not existing
		File file = new File(USER_STORAGE);
		if(!file.exists()){
			file.createNewFile();
			return new ArrayList<User>();
		}
		
		// read all users
		BufferedReader fileIn = new BufferedReader(new FileReader(USER_STORAGE));
		String currentLine;
		List<User> users = new ArrayList<User>();
		while(null != (currentLine = fileIn.readLine())){
			String[] currentUserData = currentLine.split(" ");
			users.add(new User(currentUserData[0],currentUserData[1]));
		}
		fileIn.close();
		return users;
	}
	

}

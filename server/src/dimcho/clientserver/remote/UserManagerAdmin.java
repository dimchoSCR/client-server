package dimcho.clientserver.remote;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface UserManagerAdmin extends Remote{
	String[] getRegisteredUsers(String adminName, String adminPass)throws RemoteException;
	void deleteUser(String adminName, String adminPass,String userName)throws RemoteException;
	void changeUserPassword(String adminName, String adminPass,String userName, String password)throws RemoteException;
}

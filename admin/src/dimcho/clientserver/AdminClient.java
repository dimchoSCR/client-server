package dimcho.clientserver;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import dimcho.clientserver.remote.UserManagerAdmin;

public class AdminClient {
	
	public static void main(String[] args) {
		try{
			String name = "UserManagerAdmin";
			Registry registry = LocateRegistry.getRegistry();
			UserManagerAdmin admin  = (UserManagerAdmin) registry.lookup(name);
			// Use args to invoke methods
			
			if(args.length < 2){
				System.out.println("Specify admin name and pass");
				return;
			}
			
			if(args[2].equals("list")){
				String[] users = admin.getRegisteredUsers(args[0],args[1]);
				for(String user : users){
					System.out.println(user);
				}
			}else if(args[2].equals("delete")){
				if(args.length < 4){
					System.out.println("No username specified");
					return;
				}
				admin.deleteUser(args[0],args[1],args[3]);
				System.out.println("User " + args[3] + " deleted");
			}else if(args[2].equals("chpass")){
				if(args.length < 5){
					System.out.println("No username or password specified");
					return;
				}
				admin.changeUserPassword(args[0],args[1],args[3],args[4]);
				System.out.println("User " + args[3] + " changed password");
			}else{
				System.out.println("No such command");
			}	
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}

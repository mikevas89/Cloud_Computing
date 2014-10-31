package interfaces;

	import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;

import messages.Message;

	
	//create interface for Server - client communication
	public interface ServerClientRMI extends Remote{
		
		public void onMessageReceived(Message message) throws RemoteException, NotBoundException;
	}


package communication;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import messages.Message;
import messages.ReplyMessage;
import interfaces.ServerClientRMI;
import client.User;

//implementation of communication interface from Client to Server

public class ClientRMI extends UnicastRemoteObject implements ServerClientRMI {

	private static final long serialVersionUID = 1L;
	private User user; 


	public ClientRMI(User client) throws RemoteException {
		super();
		this.user = client;
	}

	public void onMessageReceived(Message message) throws RemoteException,
			NotBoundException {

		if (message instanceof ReplyMessage) {
			
			//this.user.setVmIP(((ReplyMessage) message).getVmIP());
			if(!this.user.putIPToQueue(((ReplyMessage) message).getVmIP()))
				System.err.println("Problem queueing the IP");
		}

	}

	

}

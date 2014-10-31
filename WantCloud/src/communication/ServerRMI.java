package communication;


import java.rmi.server.UnicastRemoteObject;



import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import cloudNodes.HeadNode;

import messages.Message;
import messages.PingMessage;
import messages.RequestMessage;
import interfaces.ServerClientRMI;


//implementation of communication interface from Server to Client

public class ServerRMI extends UnicastRemoteObject implements ServerClientRMI{

	private static final long serialVersionUID = 1L;
	private HeadNode headNode;

	public ServerRMI(HeadNode headNode) throws RemoteException {
		super();
		this.headNode=headNode;

	}

	public void onMessageReceived(Message message) throws RemoteException, NotBoundException {
		
		//RequestMessages are stored in a queue
		
		if(message instanceof RequestMessage) {
			System.out.println("Message received from"+ message.getSenderID()+" Type:"+ ((RequestMessage) message).getRequestType());
			if(!this.headNode.putRequestToQueue((RequestMessage) message))
					System.err.println("Problem queueing the request");
		}
		//ping from VMs
		else if(message instanceof PingMessage) {
			System.out.println("Ping received from"+ message.getSenderIP());
			this.headNode.getVmPool().
							get(message.getSenderIP()).
							setLastPingSent(System.currentTimeMillis());
		}
		
			
	}

}

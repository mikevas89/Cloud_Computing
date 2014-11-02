package cloudNodes;

import interfaces.ServerClientRMI;


import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import messages.PingMessage;
import constants.Constants;



public class PingSender {

	
	//public ServerRMI comm;
	private String vmIP;
	private String headNodeIP;
	
	
	public PingSender(String vmIP, String headNodeIP){
		
		this.vmIP=vmIP;
		this.headNodeIP= headNodeIP;		
	}
	
	public void startPingSender(){
		
		ServerClientRMI serverCommunication = null;
		
		while (true) {

			try {
				
				serverCommunication = this.getServerReg();
				if(serverCommunication != null){
					System.out.println("I am sending ping @ "+ System.currentTimeMillis());
					serverCommunication.onMessageReceived(
							new PingMessage(-2, vmIP, Constants.HEADNODE_ID,
									this.headNodeIP));
				}
						
			} catch (RemoteException e) {
				//e.printStackTrace();
				System.err.println("Run:"+"Server: " + Constants.HEADNODE_NAME
						+ " ServerRMI RemoteException error");
			} catch (NotBoundException e) {
				System.err.println("Run:"+"Server: " + Constants.HEADNODE_NAME
						+ " ServerRMI NotBoundException error");			}
			
			try {
				Thread.sleep(Constants.PING_PERIOD);
			} catch (InterruptedException e) {
				//e.printStackTrace();
				System.err.println("Run:"+"Server: " + Constants.HEADNODE_NAME
						+ " ServerRMI InterruptedException error");
			}

		}
	}
	
	
	
	
	// contact with server
	public ServerClientRMI getServerReg() {
		ServerClientRMI serverCommunication = null;
		try {
			serverCommunication = (ServerClientRMI) Naming.lookup("rmi://"
					+ headNodeIP + ":"
					+ String.valueOf(Constants.HEADNODE_RMI) + "/"
					+ Constants.HEADNODE_NAME);
		} catch (MalformedURLException e) {
			// e.printStackTrace();
			System.err.println("getServerReg:"+"Server: " + Constants.HEADNODE_NAME
					+ " ServerRMI MalformedURLException error");
		} catch (RemoteException e) {
			// e.printStackTrace();
			System.err.println("getServerReg:"+"Server: " + Constants.HEADNODE_NAME
					+ " ServerRMI RemoteException error");
			return null;
		} catch (NotBoundException e) {
			// e.printStackTrace();
			System.err.println("getServerReg:"+"Server: " + Constants.HEADNODE_NAME
					+ " ServerRMI NotBoundException error");
			return null;
		}
		return serverCommunication;
	}
	
	

	//args[0] -> IP of this VM
	//args[1] -> IP of HEADNODE
	
	public static void main(String[] args) {
		
		PingSender vm = new PingSender(args[0], args[1]);
		vm.startPingSender();

	}

}

package client;

import interfaces.ServerClientRMI;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.LinkedBlockingQueue;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import messages.RequestMessage;
import communication.ClientRMI;
import communication.ServerRMI;
import constants.Constants;
import constants.RequestType;
import constants.UserType;

public class User implements Runnable {

	private LinkedBlockingQueue<String> IPqueue;
	private int id;
	private String vmIP;
	private UserType type;

	ClientRMI comm = null;
	ServerRMI headNode = null;

	public User(int id, UserType UserT) {

		IPqueue = new LinkedBlockingQueue<String>();

		this.type = UserT;
		this.id = id;

		try {
			comm = new ClientRMI(this);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

		// bind to RMI registry
		if (!this.createRegistryAndBind(String.valueOf(id), comm))
			this.bindInExistingRegistry(String.valueOf(id), comm);
	}

	public void run() {

		System.out.println("Thread: " + this.id + " created");
		// request a VM
		try {
			this.getServerReg().onMessageReceived(
					new RequestMessage(this.id, RequestType.RequestVM));
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (NotBoundException e) {
			e.printStackTrace();
		}

		try {
			this.setVmIP(this.getIPqueue().take());
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}

		// establish ssh connection
		Connection conn = connectSSH(this.getVmIP());
		System.out.println("Thread: " + this.id + " SSH connection established");

		// execute job
		executeJob(conn);

		conn.close();

		
		 //ask headnode to remove User 
		try {
			RequestMessage request = new RequestMessage(this.getId(), RequestType.DeleteUser);
			request.setVmIP(this.getVmIP());
			this.getServerReg().onMessageReceived(request);
		} catch (RemoteException e) {
			e.printStackTrace(); 
		} catch (NotBoundException e) {
			e.printStackTrace(); 
		}
		

	}


	public Connection connectSSH(String VMip) {

		String username = "root";

		File keyfile = new File("/home/cld1467/.ssh/id_dsa");
		String keyfilePass = "root";
		Connection conn = new Connection(VMip);

		try {
			conn.connect();

			// System.out.println("Let's authenticate!");

			boolean isAuthenticated = conn.authenticateWithPublicKey(username,
					keyfile, keyfilePass);

			if (isAuthenticated == false) {
				System.out.println("Couldn't Authenticate");
				return null;
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return conn;

	}

	public void executeJob(Connection conn) {

		BufferedReader br;

		Session sess;
		try {
			sess = conn.openSession();

			// execute job according to client type
			if (this.getType() == UserType.LightUser)
				sess.execCommand("/bin/sh /root/userJob.sh light /root/source_image.jpg");
			else if (this.getType() == UserType.HeavyUser)
				sess.execCommand("/bin/sh /root/userJob.sh heavy /root/source_image.jpg");

			// print stdout
			InputStream stdout = new StreamGobbler(sess.getStdout());

			br = new BufferedReader(new InputStreamReader(stdout));
			String line;
			while (true) {
				line = br.readLine();
				if (line == null)
					break;
				else
					System.out.println("User: " + this.getId() + " " + line);					
			}

			sess.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}



	/*---------------------------------------------------
	 * ESTABLISH CLIENT RMI REGISTRY
	 ----------------------------------------------------
	 */
	public boolean bindInExistingRegistry(String idName, ClientRMI comm) {
		System.out.println("bindInExistingRegistry");
		Registry myRegistry;
		try {
			myRegistry = LocateRegistry.getRegistry(Constants.CLIENT_RMI);
			myRegistry.bind(idName, comm); // bind with their names
			System.out.println("bindInExistingRegistry completed");
			return true;
		} catch (RemoteException e) {
			e.printStackTrace();
			return false;
		} catch (AlreadyBoundException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean createRegistryAndBind(String idName, ClientRMI comm) {
		System.out.println("createRegistryAndBind");
		Registry myRegistry;
		try {
			myRegistry = LocateRegistry.createRegistry(Constants.CLIENT_RMI);
			myRegistry.rebind(idName, comm); // server's name
			System.out.println("createRegistryAndBind completed");
			return true;
		} catch (RemoteException e) {
			System.out.println("createRegistryAndBind failed");
			// e.printStackTrace();
			return false;
		}
	}

	// contact with server
	public ServerClientRMI getServerReg() {
		ServerClientRMI serverCommunication = null;
		try {
			serverCommunication = (ServerClientRMI) Naming.lookup("rmi://"
					+ Constants.HEADNODE_IP + ":"
					+ String.valueOf(Constants.HEADNODE_RMI) + "/"
					+ Constants.HEADNODE_NAME);
		} catch (MalformedURLException e) {
			// e.printStackTrace();
		} catch (RemoteException e) {
			// e.printStackTrace();
			System.err.println("Server: " + Constants.HEADNODE_NAME
					+ " ServerRMI RemoteException error");
			return null;
		} catch (NotBoundException e) {
			// e.printStackTrace();
			System.err.println("Server: " + Constants.HEADNODE_NAME
					+ " ServerRMI NotBoundException error");
			return null;
		}
		return serverCommunication;
	}

	public boolean putIPToQueue(String ip) {
		try {
			this.IPqueue.put(ip);
			return true;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/*---------------------------------------------------
	 * GETTERS & SETTERS
	 ----------------------------------------------------
	 */

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public UserType getType() {
		return type;
	}

	public String getVmIP() {
		return vmIP;
	}

	public void setVmIP(String vmIP) {
		this.vmIP = vmIP;
	}

	public void setType(UserType type) {
		this.type = type;
	}

	public LinkedBlockingQueue<String> getIPqueue() {
		return IPqueue;
	}

	public void setIPqueue(LinkedBlockingQueue<String> iPqueue) {
		IPqueue = iPqueue;
	}

}

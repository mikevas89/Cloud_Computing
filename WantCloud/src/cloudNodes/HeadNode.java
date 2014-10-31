package cloudNodes;

import interfaces.ServerClientRMI;

import java.io.File;
import java.net.MalformedURLException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
//import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import communication.ServerRMI;
import constants.Constants;
import constants.Policy;
import constants.RegisteredUser;
import constants.VMStats;
import messages.ReplyMessage;
import messages.RequestMessage;

import org.opennebula.client.Client;
import org.opennebula.client.ClientConfigurationException;

public class HeadNode {

	private LinkedBlockingQueue<RequestMessage> requests;
	// private List<RegisteredClient> clientList;
	private ConcurrentHashMap<String, ArrayList<RegisteredUser>> vmUsers; // registered
	// clients
	private ConcurrentHashMap<String, VMStats> vmPool; // statistics of VM

	private CopyOnWriteArrayList<RequestMessage> requestQueue;

	// Logger
	// TODO : put logStatus() to right places
	// private Vector<String> logger;

	private static Client openNebula;

	public ServerRMI comm;

	public HeadNode() {

		// initialize control structures
		requests = new LinkedBlockingQueue<RequestMessage>();
		vmUsers = new ConcurrentHashMap<String, ArrayList<RegisteredUser>>();
		vmPool = new ConcurrentHashMap<String, VMStats>();
		requestQueue = new CopyOnWriteArrayList<RequestMessage>();
		// logger = new Vector<String>();

		// create openNebula client
		try {
			setOpenNebula(new Client());
		} catch (ClientConfigurationException e1) {
			e1.printStackTrace();
		}

		try {
			comm = new ServerRMI(this);
		} catch (RemoteException e) {
			e.printStackTrace();
		}

		// create ServerRMI and publish it

		if (!this.createRegistryAndBind(Constants.HEADNODE_NAME, comm))
			this.bindInExistingRegistry(Constants.HEADNODE_NAME, comm);

		/*----------------------------------------------------
		 Create thread for monitoring the pings from the VMs
		 ----------------------------------------------------
		 */
		Runnable pingMonitor = new PingMonitor(this.getVmPool(),
				this.getVmUsers());
		new Thread(pingMonitor).start();
		/*----------------------------------------------------
		 Create thread for monitoring the VMs
		 ----------------------------------------------------
		 */
		Runnable VmMonitor = new VmMonitor(this.getVmPool(), this.getVmUsers(),
				this.getRequestQueue(), Policy.SuperSimple);
		new Thread(VmMonitor).start();

	}

	public void startHeadNode() {

		// consume client requests

		while (true) {

			RequestMessage request = null;

			try {
				request = this.getRequests().take();
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

			switch (request.getRequestType()) {
			case RequestVM:
			case PriorityRequest:
				// check to VMPool where to put client
				this.scheduleClient(request);
				break;
			case DeleteUser:
				// deletes the registered user from the VM IP that user sent
				this.deleteUser(new RegisteredUser(request.getSenderID(),
						request.getVmIP()));
				break;
			default:
				break;
			}

		}

	}

	public void scheduleClient(RequestMessage message) {

		final RequestMessage request = message;

		// check VM if available room for new job
		// String availableVMIP = this.choosePolicy();

		// add request to request queue
		this.getRequestQueue().add(request);

		return;

	}

	// deletes the registered user
	public boolean deleteUser(RegisteredUser user) {

		System.out.println("User " + user.getId() + " and IP: "
				+ user.getVmIPofUser() + " is going to be removed");

		ArrayList<RegisteredUser> group = this.getVmUsers().get(
				user.getVmIPofUser());
		VMStats vmStats = this.getVmPool().get(user.getVmIPofUser());

		return group.remove(user)
				&& vmStats.setNumRegisteredUsers(vmStats
						.getNumRegisteredUsers() - 1);
	}

	// policies to choose suitable VMs
	public String choosePolicy() {

		// TODO:returns null if a new VM has to be allocated or IP according to
		// some scheduling rules
		return null;

	}

	// send to Client message ( spawns a thread)
	public void sendUserMessage(ReplyMessage message) {

		final ReplyMessage reply = message;

		new Thread() {
			public void run() {

				try {
					HeadNode.getClientReg(String.valueOf(reply.getReceiverID()))
							.onMessageReceived(reply);
				} catch (RemoteException e) {
					e.printStackTrace();
				} catch (NotBoundException e) {
					e.printStackTrace();
				}

			}
		}.start();

	}

	public boolean putRequestToQueue(RequestMessage request) {
		try {
			this.requests.put(request);
			return true;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}

	/*---------------------------------------------------
	 * ESTABLISH SERVER RMI REGISTRY
	 ----------------------------------------------------
	 */
	public boolean bindInExistingRegistry(String headName, ServerRMI comm) {
		System.out.println("bindInExistingRegistry");
		Registry myRegistry;
		try {
			myRegistry = LocateRegistry.getRegistry(Constants.HEADNODE_RMI);
			myRegistry.bind(headName, comm); // bind with their names
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

	public boolean createRegistryAndBind(String headName, ServerRMI comm) {
		System.out.println("createRegistryAndBind");
		Registry myRegistry;
		try {
			myRegistry = LocateRegistry.createRegistry(Constants.HEADNODE_RMI);
			myRegistry.rebind(headName, comm); // server's name
			System.out.println("createRegistryAndBind completed");
			return true;
		} catch (RemoteException e) {
			System.out.println("createRegistryAndBind failed");
			// e.printStackTrace();
			return false;
		}
	}

	public static ServerClientRMI getClientReg(String clientID) {
		ServerClientRMI clientCommunication = null;
		try {
			clientCommunication = (ServerClientRMI) Naming.lookup("rmi://"
					+ Constants.CLIENT_IP + ":"
					+ String.valueOf(Constants.CLIENT_RMI) + "/" + clientID);
		} catch (MalformedURLException e) {
			// e.printStackTrace();
		} catch (RemoteException e) {
			// e.printStackTrace();
			System.err
					.println("HeadNode: ClientRMI RemoteException error with client: "
							+ clientID);
			return null;
		} catch (NotBoundException e) {
			// e.printStackTrace();
			System.err
					.println("Server: ClientRMI NotBoundException error with client: "
							+ clientID);
			return null;
		} catch (Exception e) {
			System.err
					.println("Server: ServerClientRMI Exception error with client: "
							+ clientID);
			return null;
		}
		return clientCommunication;
	}

	/*---------------------------------------------------
	 *			 GETTERS AND SETTERS
	 ----------------------------------------------------
	 */

	public LinkedBlockingQueue<RequestMessage> getRequests() {
		return requests;
	}

	public void setRequests(LinkedBlockingQueue<RequestMessage> requests) {
		this.requests = requests;
	}

	public ConcurrentHashMap<String, ArrayList<RegisteredUser>> getVmUsers() {
		return this.vmUsers;
	}

	public void setVmClients(
			ConcurrentHashMap<String, ArrayList<RegisteredUser>> vmClients) {
		this.vmUsers = vmClients;
	}

	public ConcurrentHashMap<String, VMStats> getVmPool() {
		return this.vmPool;
	}

	public void setVmPool(ConcurrentHashMap<String, VMStats> vmPool) {
		this.vmPool = vmPool;
	}

	public static Client getOpenNebula() {
		return openNebula;
	}

	public void setOpenNebula(Client openNebula) {
		HeadNode.openNebula = openNebula;
	}

	public static void createHeadNodeIPFile() {
		try {
			String[] command = { "/bin/bash", "createHeadNodeIP.sh" };
			ProcessBuilder pb = new ProcessBuilder(command);
			Process p = pb.start(); // Start the process.
			p.waitFor(); // Wait for the process to finish.
			System.out.println("HeadNodeIPFile created successfully");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public CopyOnWriteArrayList<RequestMessage> getRequestQueue() {
		return requestQueue;
	}

	public void setRequestQueue(
			CopyOnWriteArrayList<RequestMessage> requestQueue) {
		this.requestQueue = requestQueue;
	}

	public static void main(String[] args) {

		createHeadNodeIPFile();

		deleteLoggerFile();

		HeadNode headNode = new HeadNode();

		// start head node of the cluster
		headNode.startHeadNode();
	}

	public static void deleteLoggerFile() {

		try {
			String tempFile = "logger.txt";
			// Delete if tempFile exists
			File fileTemp = new File(tempFile);
			if (fileTemp.exists()) {
				fileTemp.delete();
			}
		} catch (Exception e) {
			// if any error occurs
			e.printStackTrace();
		}
		return;
	}

}

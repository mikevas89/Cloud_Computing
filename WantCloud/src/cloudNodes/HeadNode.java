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
import constants.Pair;
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
	
	private CopyOnWriteArrayList<Pair<Integer,Long>> waitingUsers;
	private CopyOnWriteArrayList<Pair<Long,Long>> waitingUsersStats;
	

	//job completion time stats
	private int completedJobs;
	private double avgCompletionTime;
	
	
	//user requests stats
	private int sumAvgRequestRatio;
	private int numAvgRequestForRatio;
	private int numWindowRequestsForRatio;
	private long windowTimeForRatio;
	
	//calculate average boot time
	private long sumBootingTimes;
	private long numBootingTimes;
	
	
	private long systemTimeStart;
	private int refreshAveragesTimes;
	
	// Logger
	// TODO : put logStatus() to right places
	// private Vector<String> logger;

	private static Client openNebula;

	public ServerRMI comm;

	public HeadNode() {
		
		this.setSystemTimeStart(System.currentTimeMillis());
		this.setRefreshAveragesTimes(1);
		
		//initialization of variables
		this.setCompletedJobs(0);
		this.setAvgCompletionTime(0);
		this.setSumRequestRatio(0);
		this.setNumAvgRequestForRatio(0);
		this.setNumWindowRequestsForRatio(0);
		this.setWindowTimeForRatio(System.currentTimeMillis());
		

		// initialize control structures
		requests = new LinkedBlockingQueue<RequestMessage>();
		vmUsers = new ConcurrentHashMap<String, ArrayList<RegisteredUser>>();
		vmPool = new ConcurrentHashMap<String, VMStats>();
		requestQueue = new CopyOnWriteArrayList<RequestMessage>();
		waitingUsers = new CopyOnWriteArrayList<Pair<Integer, Long>>();
		waitingUsersStats = new CopyOnWriteArrayList<Pair<Long, Long>>();
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
		Runnable pingMonitor = new PingMonitor(this);
		new Thread(pingMonitor).start();
		/*----------------------------------------------------
		 Create thread for monitoring the VMs
		 ----------------------------------------------------
		 */
		Runnable VmMonitor = new VmMonitor(this, Policy.Simple);
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
						request.getVmIP(),0));
				System.out.println("COMPLETION "+ System.currentTimeMillis()+" "+request.getExecutionJobTime()+ " "+request.getSenderID());
				this.updateAvgJobCompletion(request.getExecutionJobTime());
				break;
			default:
				break;
			}

		}

	}

	public void scheduleClient(RequestMessage message) {

		final RequestMessage request = message;

		//new request for time window
		this.numWindowRequestsForRatio++;		
		
		// add request to request queue
		this.getRequestQueue().add(request);

		return;

	}

	// deletes the registered user
	public boolean deleteUser(RegisteredUser user) {

		System.out.println("deleteUser: User " + user.getId() + " and IP: "
				+ user.getVmIPofUser() + " is going to be removed");

		ArrayList<RegisteredUser> group = this.getVmUsers().get(
				user.getVmIPofUser());
		VMStats vmStats = this.getVmPool().get(user.getVmIPofUser());

		return group.remove(user)
				&& vmStats.setNumRegisteredUsers(vmStats
						.getNumRegisteredUsers() - 1);
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
			//put to waitingUsers list
			this.getWaitingUsers().add(new Pair<Integer, Long>(request.getSenderID(),System.currentTimeMillis()));
			return true;
		} catch (InterruptedException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	//re-calculate average of job execution times
	public void updateAvgJobCompletion(long execTime){
		this.completedJobs++;
		this.setAvgCompletionTime(this.avgCompletionTime * ((double)(this.completedJobs-1))/this.completedJobs 
				+ ((double) (execTime)) / this.completedJobs);
		
		System.out.println("updateAvgJobCompletion: New userTime: "+ execTime +", New avgJobCompletion = "+ this.getAvgCompletionTime());
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

	public int getCompletedJobs() {
		return completedJobs;
	}

	public long getAvgCompletionTime() {
		if(this.avgCompletionTime == 0)
			return this.getAvgBootingTime();
		else
			return Math.round(avgCompletionTime);
	}

	public void setCompletedJobs(int completedJobs) {
		this.completedJobs = completedJobs;
	}

	public void setAvgCompletionTime(double avgCompletionTime) {
		this.avgCompletionTime = avgCompletionTime;
	}

	public int getSumRequestRatio() {
		return sumAvgRequestRatio;
	}
	
	public void addToSumRequestRatio(int requests) {
		this.sumAvgRequestRatio  = this.sumAvgRequestRatio + requests;
	}

	public int getNumWindowRequestsForRatio() {
		return numWindowRequestsForRatio;
	}

	public long getWindowTimeForRatio() {
		return windowTimeForRatio;
	}


	public void setSumRequestRatio(int avgRequestRatio) {
		this.sumAvgRequestRatio = avgRequestRatio;
	}

	public void setNumWindowRequestsForRatio(int numRequestsForRatio) {
		this.numWindowRequestsForRatio = numRequestsForRatio;
	}

	public void setWindowTimeForRatio(long windowTimeForRatio) {
		this.windowTimeForRatio = windowTimeForRatio;
	}
	public int getNumAvgRequestForRatio() {
		return numAvgRequestForRatio;
	}

	public void setNumAvgRequestForRatio(int numAvgRequestForRatio) {
		this.numAvgRequestForRatio = numAvgRequestForRatio;
	}

	public CopyOnWriteArrayList<Pair<Integer, Long>> getWaitingUsers() {
		return waitingUsers;
	}

	public CopyOnWriteArrayList<Pair<Long, Long>> getWaitingUsersStats() {
		return waitingUsersStats;
	}

	public void setWaitingUsers(
			CopyOnWriteArrayList<Pair<Integer, Long>> waitingUsers) {
		this.waitingUsers = waitingUsers;
	}

	public void setWaitingUsersStats(
			CopyOnWriteArrayList<Pair<Long, Long>> waitingUsersStats) {
		this.waitingUsersStats = waitingUsersStats;
	}

	public long getSumBootingTimes() {
		return sumBootingTimes;
	}
	
	
	public void addToSumBootingTimes(long bootingTime){
		this.sumBootingTimes += bootingTime; 
	}

	public void setSumBootingTimes(long sumBootingTimes) {
		this.sumBootingTimes = sumBootingTimes;
	}

	public long getNumBootingTimes() {
		return numBootingTimes;
	}

	public void setNumBootingTimes(long numBootingTimes) {
		this.numBootingTimes = numBootingTimes;
	}
	
	public long getAvgBootingTime() {
		
		if(this.getSumBootingTimes()==0)
			return Constants.INITIAL_BOOT_TIME;

		return Math.round((double) this.getSumBootingTimes()
				/ this.getNumBootingTimes());
	}

	public long getSystemTimeStart() {
		return systemTimeStart;
	}

	public void setSystemTimeStart(long systemTimeStart) {
		this.systemTimeStart = systemTimeStart;
	}

	public int getRefreshAveragesTimes() {
		return refreshAveragesTimes;
	}

	public void setRefreshAveragesTimes(int refreshAveragesTimes) {
		this.refreshAveragesTimes = refreshAveragesTimes;
	}


}

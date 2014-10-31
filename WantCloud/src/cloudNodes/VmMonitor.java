package cloudNodes;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.vm.VirtualMachine;

import constants.Constants;
import constants.Policy;
import constants.RegisteredUser;
import constants.RequestType;
import constants.VMStats;
import constants.VMstatus;
import messages.ReplyMessage;
import messages.RequestMessage;

public class VmMonitor implements Runnable {

	private CopyOnWriteArrayList<RequestMessage> requestQueue;
	private ConcurrentHashMap<String, VMStats> vmPool; // statistics of VM
	private ConcurrentHashMap<String, ArrayList<RegisteredUser>> vmUsers; // registered
																			// clients
	private Policy policy;

	// POLICY is assigned by HeadNode
	public VmMonitor(ConcurrentHashMap<String, VMStats> vmPool,
			ConcurrentHashMap<String, ArrayList<RegisteredUser>> vmUsers,
			CopyOnWriteArrayList<RequestMessage> requestQueue, Policy policy) {
		this.requestQueue = requestQueue;
		this.setVmPool(vmPool);
		this.setVmUsers(vmUsers);
		this.setPolicy(policy);
	}

	@Override
	public void run() {

		// check requestqueue
		while (true) {
			try {
				Thread.sleep(Constants.VM_MONITOR_PERIOD);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			if (this.getRequestQueue().size() <= 0)
				continue;

			System.out.println(this.getRequestQueue().size());
			// apply selected policy
			switch (this.getPolicy()) {
			case SuperSimple:
				this.applySuperSimplePolicy();
				break;
			case Simple:
				this.applySimplePolicy();
				break;
			case Advanced:
				this.applyAdvancedPolicy();
				break;
			}
		}

	}

	public void applySimplePolicy() {

		//this.logStatus("1");

		if (this.availableVM()) {
			String availableVMIP = getAvailableVM();
			this.assignVMToUser(availableVMIP, this.popFromRequestQueue());
		} else if (!this.bootingVM()) {
			System.out.println("bootingVM NOT exists" + this.bootingVM());
			new Thread() {
				public void run() {
					// calls the openNebula to return a new VM
					String vmIP = allocateVM(HeadNode.getOpenNebula());
					System.out.println("VM successfully allocated with IP:"
							+ vmIP);
				}
			}.start();
		}
	}

	public void applySuperSimplePolicy() {

		this.popFromRequestQueue();
		new Thread() {
			public void run() {
				// calls the openNebula to return a new VM
				String vmIP = allocateVM(HeadNode.getOpenNebula());
				System.out.println("VM successfully allocated with IP:" + vmIP);
			}
		}.start();

		//this.logStatus("1");
	}

	public void applyAdvancedPolicy() {

		this.handlePUsers();

		this.scheduleVMAllocations();

		// handle regular Request
		if (!this.getRequestQueue().isEmpty() && this.availableAdvancedVM())
			this.assignVMToUser(this.getAvailableAdvancedVM(),
					this.popFromRequestQueue());
	}

	/*---------------------------------------------------
	 *			 SIMPLE POLICY METHODS
	 ----------------------------------------------------
	 */

	public boolean availableVM() {

		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet()
				.iterator(); it.hasNext();) {
			Entry<String, VMStats> entry = it.next();
			if (entry.getValue().getVmStatus() == VMstatus.Running)
				return true;
		}

		return false;
	}

	public boolean bootingVM() {

		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet()
				.iterator(); it.hasNext();) {
			Entry<String, VMStats> entry = it.next();
			if (entry.getValue().getVmStatus() == VMstatus.Booting)
				return true;
		}

		return false;
	}

	public String getAvailableVM() {
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet()
				.iterator(); it.hasNext();) {
			Entry<String, VMStats> entry = it.next();
			if (entry.getValue().getVmStatus() == VMstatus.Running)
				return entry.getKey();
		}
		return null;
	}

	/*---------------------------------------------------
	 *			 ADVANCED POLICY METHODS
	 ----------------------------------------------------
	 */
	public boolean availableAdvancedVM() {

		// check if there is appropriate space in a VM
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet()
				.iterator(); it.hasNext();) {
			VMStats vm = it.next().getValue();
			if (vm.getVmStatus() == VMstatus.Running
					&& vm.hasNormalCapacityAddingOneUser())
				return true;
		}
		return false;
	}

	public String getAvailableAdvancedVM() {
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet()
				.iterator(); it.hasNext();) {
			Entry<String, VMStats> entry = it.next();
			VMStats vm = entry.getValue();
			if (vm.getVmStatus() == VMstatus.Running
					&& vm.hasNormalCapacityAddingOneUser())
				return entry.getKey();
		}
		return null;
	}

	public void handlePUsers() {

		while (this.pUserExists() && this.availableVMForPUser()) {
			this.assignVMToUser(this.getAvailableVMForPUser(),
					this.getPriorityRequest());
		}

	}

	public boolean pUserExists() {
		for (RequestMessage request : this.getRequestQueue()) {
			if (request.getRequestType() == RequestType.PriorityRequest)
				return true;
		}
		return false;
	}

	public RequestMessage getPriorityRequest() {
		RequestMessage returnedRequest;
		for (Iterator<RequestMessage> it = this.getRequestQueue().iterator(); it
				.hasNext();) {
			RequestMessage request = it.next();
			if (request.getRequestType() == RequestType.PriorityRequest) {
				returnedRequest = new RequestMessage(request);
				it.remove();
				return returnedRequest;
			}
		}
		return null;
	}

	public boolean availableVMForPUser() {
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet()
				.iterator(); it.hasNext();) {
			VMStats vm = it.next().getValue();
			if (vm.getVmStatus() == VMstatus.Running && vm.notfullCapacity())
				return true;
		}
		return false;
	}

	public String getAvailableVMForPUser() {
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet()
				.iterator(); it.hasNext();) {
			VMStats vm = it.next().getValue();
			if (vm.getVmStatus() == VMstatus.Running && vm.notfullCapacity())
				return vm.getVmIP();
		}
		return null;
	}

	public void scheduleVMAllocations() {
		// TODO: check if booting VMs exist

		// check size of request queue and decide if allocation will be done
		// according to capacities
		// avg of user arrival
		// size of request queue
	}

	/*---------------------------------------------------
	 *			 COMMON POLICY METHODS
	 ----------------------------------------------------
	 */

	public void assignVMToUser(String vmIP, RequestMessage requestMessage) {

		RequestMessage request = requestMessage;

		System.out.println("Available IP found: First request is from user "
				+ request.getSenderID());

		// add user to registered users
		ArrayList<RegisteredUser> group = this.getVmUsers().get(vmIP);
		group.add(new RegisteredUser(request.getSenderID(), vmIP));

		// update users using the VM
		VMStats availableVM = this.getVmPool().get(vmIP);
		availableVM
				.setNumRegisteredUsers(availableVM.getNumRegisteredUsers() + 1);

		System.out.println("Available IP found: Sending to user "
				+ request.getSenderID());

		// send message to user with the available IP (spawns thread)
		this.sendUserMessage(new ReplyMessage(Constants.HEADNODE_ID,
				Constants.HEADNODE_IP, request.getSenderID(), request
						.getReceiverIP(), true, vmIP));
	}

	public RequestMessage popFromRequestQueue() {
		RequestMessage request = this.getRequestQueue().get(0);

		this.getRequestQueue().remove(0);
		return request;

	}

	public String allocateVM(Client oneClient) {

		OneResponse rc = null;
		String vmIP = null;

		try {

			String vmTemplate = deserializeString(new File(
					"/home/cld1467/OpenNebula/centos-smallnet.one"));

			System.out.print("Trying to allocate the virtual machine... ");

			rc = VirtualMachine.allocate(oneClient, vmTemplate);

			if (rc.isError()) {
				System.out.println("failed!");
				throw new Exception(rc.getErrorMessage());
			}

			long timeOfAllocation = System.currentTimeMillis();

			// new vmID from the new VM
			int newVMID = Integer.parseInt(rc.getMessage());
			System.out.println("ok, ID " + newVMID + ".");

			// get VM instance
			VirtualMachine vm = new VirtualMachine(newVMID, oneClient);

			// And now we can request its information.
			rc = vm.info();

			if (rc.isError())
				throw new Exception(rc.getErrorMessage());

			// Get VMs ip using bash commands
			Runtime run = Runtime.getRuntime();
			Process proc = run.exec(new String[] {
					"/bin/sh",
					"-c",
					"onevm show " + Integer.toString(newVMID)
							+ " | grep IP | cut -d \"\\\"\" -f 2" });
			proc.waitFor();
			BufferedReader br = new BufferedReader(new InputStreamReader(
					proc.getInputStream()));
			while (br.ready()) {
				vmIP = br.readLine();
			}

			System.out.println("The new VM " + vm.getName() + " has status: "
					+ vm.status() + " and IP:" + vmIP);

			// ------------------------------------------------------------------
			// Vm is up and running with ssh deamon up and running

			// put VM to VM pool
			this.getVmPool().put(
					vmIP,
					new VMStats(newVMID, vm, vmIP, VMstatus.Booting, 0,
							timeOfAllocation));

			// make entry to vmUsers container
			this.getVmUsers().put(vmIP, new ArrayList<RegisteredUser>());

			System.out.println("Printing lists from allocate");
			//this.logStatus("");

		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		return vmIP;
	}

	public String deserializeString(File file) throws IOException {
		int len;
		char[] chr = new char[4096];
		final StringBuffer buffer = new StringBuffer();
		final FileReader reader = new FileReader(file);
		try {
			while ((len = reader.read(chr)) > 0) {
				buffer.append(chr, 0, len);
			}
		} finally {
			reader.close();
		}
		return buffer.toString();
	}

	// send to Client message ( spawns a thread)
	public void sendUserMessage(ReplyMessage message) {

		final ReplyMessage reply = message;

		new Thread() {
			public void run() {

				try {
					System.out.println("sendUserMessage: Sending to "
							+ reply.getReceiverID());
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

	public void logStatus(String input) {

		try (PrintWriter out = new PrintWriter(new BufferedWriter(
				new FileWriter("logger.txt", true)))) {
			// String logWorld = "----------"; // according to the needed output
			System.out.println("Now printing VMpool");
			out.println("Now printing VMpool");

			for (Iterator<Entry<String, VMStats>> it = this.getVmPool()
					.entrySet().iterator(); it.hasNext();) {
				Entry<String, VMStats> entry = it.next();
				System.out.println(entry.toString());
				out.println(entry.toString());
			}
			/*
			 * System.out.println("Now printing VMusers"); for
			 * (Iterator<Entry<String, ArrayList<RegisteredUser>>> it = this
			 * .getVmUsers().entrySet().iterator(); it.hasNext();) {
			 * Entry<String, ArrayList<RegisteredUser>> entry = it.next();
			 * System.out.println(entry.toString()); }
			 */

			// logger.add(logWorld);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/*---------------------------------------------------
	 *			 GETTERS AND SETTERS
	 ----------------------------------------------------
	 */

	public CopyOnWriteArrayList<RequestMessage> getRequestQueue() {
		return requestQueue;
	}

	public void setRequestQueue(
			CopyOnWriteArrayList<RequestMessage> requestQueue) {
		this.requestQueue = requestQueue;
	}

	public ConcurrentHashMap<String, VMStats> getVmPool() {
		return vmPool;
	}

	public ConcurrentHashMap<String, ArrayList<RegisteredUser>> getVmUsers() {
		return vmUsers;
	}

	public void setVmPool(ConcurrentHashMap<String, VMStats> vmPool) {
		this.vmPool = vmPool;
	}

	public void setVmUsers(
			ConcurrentHashMap<String, ArrayList<RegisteredUser>> vmUsers) {
		this.vmUsers = vmUsers;
	}

	public Policy getPolicy() {
		return policy;
	}

	public void setPolicy(Policy policy) {
		this.policy = policy;
	}

}

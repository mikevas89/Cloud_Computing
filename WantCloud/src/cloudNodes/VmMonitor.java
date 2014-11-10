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
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.vm.VirtualMachine;

import constants.Constants;
import constants.Pair;
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
	private ConcurrentHashMap<String, ArrayList<RegisteredUser>> vmUsers; // registered clients
	private HeadNode headnode;
	private Policy policy;
	
	

	// POLICY is assigned by HeadNode
	public VmMonitor(HeadNode headnode, Policy policy) {
		this.requestQueue = headnode.getRequestQueue();
		this.setVmPool(headnode.getVmPool());
		this.setVmUsers(headnode.getVmUsers());
		this.setPolicy(policy);
		this.headnode = headnode;
	}

	@Override
	public void run() {

		// check requestQueue
		while (true) {
			try {
				Thread.sleep(Constants.VM_MONITOR_PERIOD);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("--->Run : average Booting time :" +  this.headnode.getAvgBootingTime() );
			//check window time of userRequests ratio to update it
			//window Time for calculating user requests is at least the booting time of VMs
			//start update the ratio when first VM is running 
			if(System.currentTimeMillis() - this.headnode.getWindowTimeForRatio() > this.headnode.getAvgBootingTime()/8){	
				System.out.println("--->Run : New requests from window bootTime/8 :"+this.headnode.getAvgBootingTime()/8 
											+" #Requests to window: "+  this.headnode.getNumWindowRequestsForRatio()
											+ " will become eight times larger : "+this.headnode.getNumWindowRequestsForRatio()*8 );
				
				if(System.currentTimeMillis() - this.headnode.getSystemTimeStart() > this.headnode.getRefreshAveragesTimes()* Constants.REFRESH_AVERAGES_PERIOD){
					System.out.println("--->Run REFRESHING VALUES---------------------");
					System.out.println("--->Run : Refreshing avgRequests, previous:" +  this.getUserRequestRatio() );
					this.headnode.setCompletedJobs(1);
					//refresh the averages with given weights
					this.headnode.setSumRequestRatio((int)(this.getUserRequestRatio()*0.8 + this.headnode.getNumWindowRequestsForRatio()*8 *0.2));
					this.headnode.setNumAvgRequestForRatio(1); //take previous average as first average
					System.out.println("--->Run : Refreshing avgRequests, new:" +  this.getUserRequestRatio() );
					this.headnode.setRefreshAveragesTimes(this.headnode.getRefreshAveragesTimes()+1);
				}
				else{
					//update the ratio of the requests in an average Boot time period
					this.headnode.addToSumRequestRatio(this.headnode.getNumWindowRequestsForRatio()*8);
					this.headnode.setNumAvgRequestForRatio(this.headnode.getNumAvgRequestForRatio()+1);
				}

				
				this.headnode.setNumWindowRequestsForRatio(0);
				this.headnode.setWindowTimeForRatio(System.currentTimeMillis());
				
				System.out.println("--->Run : average Booting time :" +  this.headnode.getAvgBootingTime() );
				System.out.println("--->Run : Update to request ratio, new AvgRequestRatio :" +  this.getUserRequestRatio() );
				//System.out.println("--->Run : Print the waitingStats here because it is rare :" +  this.headnode.getWaitingUsersStats().toString() );
			}
			

			// apply selected policy
			switch (this.getPolicy()) {
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

		// handle the priority users if any
		this.handlePUsers();

		System.out.println("applySimplePolicy: "+ this.getRequestQueue().size()+ " regular requests have to be handled");

		while (!this.getRequestQueue().isEmpty() && this.availableVM()) {
			this.assignVMToUser(this.getAvailableVM(),
					this.popFromRequestQueue());
		}

		if (!this.getRequestQueue().isEmpty() && !this.bootingVM()) {
			System.out.println("applySimplePolicy: bootingVM NOT exists "
					+ this.bootingVM());
			new Thread() {
				public void run() {
					// calls the openNebula to return a new VM
					String vmIP = allocateVM(HeadNode.getOpenNebula());
					System.out.println("applySimplePolicy : VM successfully allocated with IP:"
									+ vmIP);
				}
			}.start();
		}
		// check if idle VMs should be removed
		this.handleIdleVMs();
	}

	

	public void applyAdvancedPolicy() {

		//handle the priority users if any
		this.handlePUsers();
		
		System.out.println("applyAdvancedPolicy: "+ this.getRequestQueue().size()+ " regular requests have to be handled");
		// handle all regular Requests than can be assigned to existing VMs 
		while (!this.getRequestQueue().isEmpty() && this.availableAdvancedVM())
			this.assignVMToUser(this.getAvailableAdvancedVM(),
					this.popFromRequestQueue());
		
		//check if new VMs have to be allocated
		this.scheduleVMs();
	}

	/*---------------------------------------------------
	 *			 SIMPLE POLICY METHODS
	 ----------------------------------------------------
	 */

	public boolean availableVM() {
		return (this.getVMwithFewestJobs() != null);
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
		return(this.getVMwithFewestJobs());
	}

	/*---------------------------------------------------
	 *			 ADVANCED POLICY METHODS
	 ----------------------------------------------------
	 */
	public boolean availableAdvancedVM() {
		return (this.getVMwithFewestJobs() != null);
	}

	public String getAvailableAdvancedVM() {
		return(this.getVMwithFewestJobs());
	}

	

	public boolean availableVMForPUser() {
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet()
				.iterator(); it.hasNext();) {
			VMStats vm = it.next().getValue();
			if (vm.getVmStatus() == VMstatus.Running && (vm.getNumRegisteredUsers() < Constants.MAX_CLIENTS_TO_VM_UPPER_THRESHOLD) )
				return true;
		}
		return false;
	}

	//firstly check if there is a VM with fewer than Constants.MAX_CLIENTS_TO_VM jobs
	public String getAvailableVMForPUser() {
		
		if(this.getVMwithFewestJobs()!=null)
			return this.getVMwithFewestJobs();
		
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet()
				.iterator(); it.hasNext();) {
			VMStats vm = it.next().getValue();
			if (vm.getVmStatus() == VMstatus.Running && (vm.getNumRegisteredUsers() < Constants.MAX_CLIENTS_TO_VM_UPPER_THRESHOLD))
				return vm.getVmIP();
		}
		return null;
	}


	
	public void scheduleVMs() {

		//current requests + predicted requests
		int allRequests = this.getRequestQueue().size() + this.getUserRequestRatio();

		System.out.println("scheduleVMAllocations: allRequests: "+ allRequests 
								+ " real Requests: " + this.getRequestQueue().size() + " predicted :" + this.getUserRequestRatio());
		
		//latencies of future available rooms
		ArrayList<Long> latencies = new ArrayList<Long>();
		
		//available rooms if booting Vms exist
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet().iterator(); 
				it.hasNext();) {
			VMStats entry = it.next().getValue();
			if (entry.getVmStatus() == VMstatus.Booting){
				for(int i=0; i<Constants.MAX_CLIENTS_TO_VM;i++)
					latencies.add(Math.abs(entry.getTimeOfAllocation() + this.headnode.getAvgBootingTime() - System.currentTimeMillis()));
			}
		}
		
		System.out.println("scheduleVMAllocations: After counting rooms from booting VMs, free rooms : "+ latencies.size());
		
		//calculate latencies from existing jobs
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet().iterator(); 
				it.hasNext();) {
			VMStats entry = it.next().getValue();
			if (entry.getVmStatus() == VMstatus.Running){
				//add registered users from running VMs 
				if(entry.getNumRegisteredUsers() > 0){
					ArrayList<RegisteredUser> users = this.getVmUsers().get(entry.getVmIP());
					for(RegisteredUser user: users)
						latencies.add(Math.abs(user.getRegistrationTime() + this.headnode.getAvgCompletionTime() - System.currentTimeMillis()));
				}
				//add free rooms from running VMs with latency=0
				for(int i=0; i<Constants.MAX_CLIENTS_TO_VM - entry.getNumRegisteredUsers();i++)
					latencies.add((long) 0);
			}
		}
		
		System.out.println("scheduleVMAllocations size of LatenciesList: "+latencies.size());
		//System.out.println("scheduleVMAllocations Latencies:"+latencies.toString());
		
		// sort latencies to find the minimum latencies
		if(!latencies.isEmpty())
			Collections.sort(latencies);
		
		this.advancedAllocation(allRequests, this.cloneList(latencies));

	}
	
	
	
	public void advancedAllocation(int numRequests, ArrayList<Long> latencies){
		
		Pair<Integer,Long> maxLatency = getNumRoomsWithSmallLatency(latencies);
		int numRoomsWithSmallLatency = maxLatency.getLeft();
	//	System.out.println("advancedAllocation : Function returned "+ numRoomsWithSmallLatency 
	//			+ " rooms with smaller latency than BootTime="+this.headnode.getAvgBootingTime() );
		if(numRoomsWithSmallLatency==0){
			this.allocateNewVMs(numRequests);
			return;
		}
		//free rooms for current requests
		if(numRequests <= numRoomsWithSmallLatency){
			System.out.println("advancedAllocation : Function returns with no actions"); 
			this.handleIdleVMs();
			return;
		}
		else{
			System.out.println("advancedAllocation : Checking available rooms for "+ (numRequests-numRoomsWithSmallLatency) + " requests"); 
			this.advancedAllocation(
					numRequests-numRoomsWithSmallLatency,
					createNewLatencies(latencies,this.headnode.getAvgCompletionTime()));		
		}
	}
	
	
	//return (num, latency) where num is the #rooms in the system whose latency < BootTime
	public Pair<Integer, Long> getNumRoomsWithSmallLatency(ArrayList<Long> latencies){
		
		int availableRooms=0;
		
		if(latencies.isEmpty())
			return (new Pair<Integer,Long>(availableRooms,(long)0));
		
		for(int i=0; i<latencies.size() && latencies.get(i)<=this.headnode.getAvgBootingTime();i++)
			availableRooms++;
		
		
		System.out.println("getNumRoomsWithSmallLatency : Found "+ availableRooms 
									+ " rooms with smaller latency than BootTime="+this.headnode.getAvgBootingTime() );
		//returns the largest latency which is smaller than Boot time
		//if the requests are more than the rooms in the system, return the largest latency 
		if(availableRooms>0)
			return (new Pair<Integer,Long>(availableRooms,latencies.get(availableRooms-1)));
		else
			return (new Pair<Integer,Long>(availableRooms,(long)0));
	}
	

	
	public void allocateNewVMs(int numNewVMs) {

		System.out.println("allocateNewVMs : New VMS have to be opened, num =: "
				+ (int) Math.ceil((double) numNewVMs/ Constants.MAX_CLIENTS_TO_VM));

		// allocate number of VMs according to restRequestsWithoutBootingVMs
		for (int i = 0; i < (int) Math.ceil((double) numNewVMs/ Constants.MAX_CLIENTS_TO_VM); i++) {
			new Thread() {
				public void run() {
					// calls the openNebula to return a new VM
					String vmIP = allocateVM(HeadNode.getOpenNebula());
					System.out.println("allocateNewVMs: VM successfully allocated with IP:"
									+ vmIP);
				}
			}.start();
		}
	}

	

	/*---------------------------------------------------
	 *		COMMON POLICY METHODS - NEW VM ALLOCATION
	 ----------------------------------------------------
	 */

	public String allocateVM(Client oneClient) {

		OneResponse rc = null;
		String vmIP = null;
		try {
			String vmTemplate = deserializeString(new File(
					"/home/cld1467/OpenNebula/centos-smallnet.one"));

			System.out.print("allocateVM: Trying to allocate the virtual machine... ");
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
					"/bin/sh","-c","onevm show " + Integer.toString(newVMID)
					+ " | grep IP | cut -d \"\\\"\" -f 2" });
			proc.waitFor();
			BufferedReader br = new BufferedReader(new InputStreamReader(
					proc.getInputStream()));
			while (br.ready()) {
				vmIP = br.readLine();
			}
			System.out.println("allocateVM: The new VM " + vm.getName() + " has status: "
					+ vm.status() + " and IP:" + vmIP);
			// ------------------------------------------------------------------
			// Vm is up and running with SSH deamon up and running
			// put VM to VM pool
			this.getVmPool().put(vmIP,new VMStats(newVMID, vm, 
							vmIP, VMstatus.Booting, 0,timeOfAllocation,0));
			// make entry to vmUsers container
			this.getVmUsers().put(vmIP, new ArrayList<RegisteredUser>());
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		return vmIP;
	}
	
	/*---------------------------------------------------
	 *	COMMON POLICY METHODS - PRIORITY USERS
	 ----------------------------------------------------
	 */
	
	public void handlePUsers() {

		while (this.pUserExists() && this.availableVMForPUser()) {
			System.out.println("handlePUsers: Handling one PUser");
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
		RequestMessage returnedRequest = null;
		RequestMessage toRemove = null;
		for (Iterator<RequestMessage> it = this.getRequestQueue().iterator(); it
				.hasNext();) {
			RequestMessage request = it.next();
			if (request.getRequestType() == RequestType.PriorityRequest) {
				toRemove = request;
				returnedRequest = new RequestMessage(request);
			}
		}
		
		if(toRemove!=null){
			this.getRequestQueue().remove(toRemove);
			return returnedRequest;
		}
		return null;
	}
	
	/*---------------------------------------------------
	 *	COMMON POLICY METHODS - IDLE VMS TO CLOSE
	 ----------------------------------------------------
	 */
	
	//deletes idle VMs which do not handle user requests for a specific period of time (10% of booting time) 
		public void handleIdleVMs(){
			
			String vmIP = this.getIdleVM();
			if(vmIP == null)
				return;
					
			System.out.println("handleIdleVMs : idleVm exists : "+ vmIP);
			
			if(this.bootingVM()){
				vmIP = this.getBootingVMtoRemove();
				System.out.println("handleIdleVMs : Prefers to cancel the booting VM : "+ vmIP);
			}
				
			//in case of an idle VM
			this.closeVM(vmIP);
			//delete all the entries of the VM
			this.getVmPool().remove(vmIP);
			this.getVmUsers().remove(vmIP);
			
			System.out.println("-----IDLE VM " +vmIP + "will be deleted!!-----");
		}		
		//returns vmIP of an idle VM according to 10% of booting time of VM
		public String getIdleVM(){
			
			for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet().iterator(); 
					it.hasNext();) {
				VMStats entry = it.next().getValue();
				
				if(     (entry.getVmStatus() == VMstatus.Running) 
						&& (entry.getNumRegisteredUsers()==0)
						&& (entry.getStartTimeWithNoUsers()!=0)
						&& (System.currentTimeMillis() - entry.getStartTimeWithNoUsers()) > (long)(0.1 * this.headnode.getAvgBootingTime()))
							return entry.getVmIP();			
			}
			
			return null;
			
		}
	
	public void closeVM(String vmIP){
		//delete VM from OpenNebulaEntries
		this.headnode.getVmPool().get(vmIP).getVmInstance().shutdown();
	}
	
	
	/*---------------------------------------------------
	 *	COMMON POLICY METHODS - REGISTER/NOTIFY USER FOR VM IP
	 ----------------------------------------------------
	 */
	
	public void assignVMToUser(String vmIP, RequestMessage requestMessage) {

		RequestMessage request = requestMessage;
		System.out.println("assignVMToUser: Available IP found: First request is from user "
				+ request.getSenderID());

		// add user to registered users
		ArrayList<RegisteredUser> group = this.getVmUsers().get(vmIP);
		group.add(new RegisteredUser(request.getSenderID(), vmIP, System.currentTimeMillis()));

		// update users using the VM
		VMStats availableVM = this.getVmPool().get(vmIP);
		availableVM.setNumRegisteredUsers(availableVM.getNumRegisteredUsers() + 1);
		availableVM.setStartTimeWithNoUsers(0); //initialization if VM had no users before
		
		//delete user form waiting queue
		//put user's waiting time to stats
		Pair<Integer, Long> toRemove = null;
		for (Iterator<Pair<Integer, Long>> it = (this.headnode.getWaitingUsers()).iterator(); it.hasNext();) {
			Pair<Integer, Long> entry = it.next();
			if(entry.getLeft() == request.getSenderID()){
				toRemove = new Pair<Integer, Long>(entry.getLeft(), entry.getRight());
				long userWaitingTime = System.currentTimeMillis() - entry.getRight();
				this.headnode.getWaitingUsersStats().add(
						new Pair<Long,Long>(System.currentTimeMillis(), userWaitingTime));
				System.out.println("LATENCY "+ System.currentTimeMillis()+" "+ userWaitingTime+" "+entry.getLeft());
				break;
			}
		}
		
		if(toRemove!=null)
			this.headnode.getWaitingUsers().remove(toRemove);
		
		System.out.println("assignVMToUser : Available IP found: Sending to user "
				+ request.getSenderID());

		// send message to user with the available IP (spawns thread)
		this.sendUserMessage(new ReplyMessage(Constants.HEADNODE_ID,
				Constants.HEADNODE_IP, request.getSenderID(), request
						.getReceiverIP(), true, vmIP));
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

	/*---------------------------------------------------
	 *	COMMON POLICY METHODS - GET STATISTICS
	 ----------------------------------------------------
	 */
	
	
	
	public int getUserRequestRatio(){
		return (int) Math.ceil((double) this.headnode.getSumRequestRatio()/ this.headnode.getNumAvgRequestForRatio());
	}

	
	public int freeSlotsFromRunningVMs(){
	
		int freeSlots=0;
		//count free slots from running VMs
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet().iterator();
				it.hasNext();) {
			VMStats entry = it.next().getValue();
			if(entry.getVmStatus() == VMstatus.Running && entry.getNumRegisteredUsers() < Constants.MAX_CLIENTS_TO_VM)
				freeSlots+= Constants.MAX_CLIENTS_TO_VM - entry.getNumRegisteredUsers();
		}
		return freeSlots;
	}
	
	public ArrayList<Long> createNewLatencies(ArrayList<Long> list, long additionalValue){
		ArrayList<Long> newList = this.cloneList(list);
		System.out.println("createNewLatencies : additional Value = "+additionalValue+" newList="+ newList.toString());
		for(int i=0; i<newList.size();i++){
			newList.set(i, newList.get(i)+ additionalValue);
		}
		return newList;	
	}
	
	public String getVMwithFewestJobs(){
		int fewestUsers=Constants.MAX_CLIENTS_TO_VM;
		String suitableVmIP = null;
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet()
				.iterator(); it.hasNext();){
			VMStats vm = it.next().getValue();
			if(vm.getVmStatus()==VMstatus.Running && (vm.getNumRegisteredUsers() < fewestUsers)){
				fewestUsers = vm.getNumRegisteredUsers();
				suitableVmIP = vm.getVmIP();
			}
		}
		return suitableVmIP;
	}
	

	/*---------------------------------------------------
	 *	COMMON POLICY METHODS - AUXILIARY METHODS
	 ----------------------------------------------------
	 */
	
	public String getBootingVMtoRemove() {

		long startBootingTime=0;
		String suitableVMIP=null;
		for (Iterator<Entry<String, VMStats>> it = this.getVmPool().entrySet()
				.iterator(); it.hasNext();) {
			Entry<String, VMStats> entry = it.next();
			if ((entry.getValue().getVmStatus() == VMstatus.Booting) && (startBootingTime < entry.getValue().getTimeOfAllocation())){
				startBootingTime = entry.getValue().getTimeOfAllocation();
				suitableVMIP = entry.getKey();
			}
		}
		return suitableVMIP;
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


	public void logStatus(String input) {

		try (PrintWriter out = new PrintWriter(new BufferedWriter(
				new FileWriter("logger.txt", true)))) {
			// String logWorld = "----------"; // according to the needed output
			//System.out.println("Now printing VMpool");
			out.println("Now printing VMpool");

		/*	for (Iterator<Entry<String, VMStats>> it = this.getVmPool()
					.entrySet().iterator(); it.hasNext();) {
				Entry<String, VMStats> entry = it.next();
				System.out.println(entry.toString());
				out.println(entry.toString());
			}*/
			
			 System.out.println("logStatus: Now printing VMusers");
			 for(Iterator<Entry<String, ArrayList<RegisteredUser>>> it = this.getVmUsers().entrySet()
					 .iterator(); it.hasNext();) {
				 Entry<String, ArrayList<RegisteredUser>> entry = it.next();
				 System.out.println(entry.toString());
				 }
			

			// logger.add(logWorld);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public RequestMessage popFromRequestQueue() {
		RequestMessage request = this.getRequestQueue().get(0);

		this.getRequestQueue().remove(0);
		return request;
	}
	
	public ArrayList<Long> cloneList(ArrayList<Long> source){
		ArrayList<Long> dest = new ArrayList<Long>();
		dest.addAll(source);
		return dest;
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

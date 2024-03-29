package constants;

import org.opennebula.client.vm.VirtualMachine;

public class VMStats {
	
	private int vmID; //returned from openNebula
	private VirtualMachine vmInstance; //returned from openNebula
	private String vmIP; //IP of VM in the cluster
	private VMstatus vmStatus; //status of VM, returned from openNebula
	private long lastPingSent; //last time Vm sent ping
	private int numRegisteredUsers; //number of users in the VM
	private long firstPing;
	private long timeOfAllocation;
	private long timeToGetReady;
	private long startTimeWithNoUsers;
	

	
	public VMStats(int vmID, VirtualMachine vmInstance, String vmIP,
			VMstatus vmStatus, int numRegisteredUsers, long timeOfAllocation ,long startTimeWithNoUsers) {
		this.vmID = vmID;
		this.vmInstance = vmInstance;
		this.vmIP = vmIP;
		this.vmStatus = vmStatus;
		this.setLastPingSent(0);
		this.numRegisteredUsers = numRegisteredUsers;
		this.timeOfAllocation = timeOfAllocation;
		this.startTimeWithNoUsers = startTimeWithNoUsers;
		this.timeToGetReady = 0;
		this.firstPing = 0;
	}

	
	public int getVmID() {
		return vmID;
	}
	public void setVmID(int vmID) {
		this.vmID = vmID;
	}
	public VirtualMachine getVmInstance() {
		return vmInstance;
	}
	public void setVmInstance(VirtualMachine vmInstance) {
		this.vmInstance = vmInstance;
	}
	public String getVmIP() {
		return vmIP;
	}
	public void setVmIP(String vmIP) {
		this.vmIP = vmIP;
	}
	public VMstatus getVmStatus() {
		return vmStatus;
	}
	public void setVmStatus(VMstatus vmStatus) {
		this.vmStatus = vmStatus;
	}
	public long getLastPingSent() {
		return lastPingSent;
	}
	public void setLastPingSent(long lastPingSent) {
		this.lastPingSent = lastPingSent;
	}
	public int getNumRegisteredUsers() {
		return numRegisteredUsers;
	}
	public synchronized boolean setNumRegisteredUsers(int numRegisteredUsers) {
		this.numRegisteredUsers = numRegisteredUsers;
		//start timer that VM has no users
		if(this.numRegisteredUsers==0)
			this.setStartTimeWithNoUsers(System.currentTimeMillis());
		return true;
	}

	


	@Override
	public String toString() {
		return "VMStats [vmID=" + vmID + ", vmIP=" + vmIP + ", vmStatus="
				+ vmStatus + ", lastPingSent=" + lastPingSent
				+ ", numRegisteredUsers=" + numRegisteredUsers
				+ ", timeToGetReady=" + timeToGetReady
				+ ", startTimeWithNoUsers=" + startTimeWithNoUsers + "]";
	}


	public long getFirstPing() {
		return firstPing;
	}



	public void setFirstPing(long firstPing) {
		this.firstPing = firstPing;
	}



	public long getTimeOfAllocation() {
		return timeOfAllocation;
	}



	public void setTimeOfAllocation(long timeOfAllocation) {
		this.timeOfAllocation = timeOfAllocation;
	}



	public long getTimeToGetReady() {
		return timeToGetReady;
	}



	public void setTimeToGetReady(long timeToGetReady) {
		this.timeToGetReady = timeToGetReady;
	}



	public long getStartTimeWithNoUsers() {
		return startTimeWithNoUsers;
	}



	public void setStartTimeWithNoUsers(long startTimeWithNoUsers) {
		this.startTimeWithNoUsers = startTimeWithNoUsers;
	}

	
}

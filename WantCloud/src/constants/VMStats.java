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
	

	
	public VMStats(int vmID, VirtualMachine vmInstance, String vmIP,
			VMstatus vmStatus, int numRegisteredUsers, long timeOfAllocation) {
		this.vmID = vmID;
		this.vmInstance = vmInstance;
		this.vmIP = vmIP;
		this.vmStatus = vmStatus;
		this.setLastPingSent(0);
		this.numRegisteredUsers = numRegisteredUsers;
		this.timeOfAllocation = timeOfAllocation;
	}



	public float capacity(){
		return (float) this.getNumRegisteredUsers() / Constants.MAX_CLIENTS_TO_VM;
	}
	
	public float additiveCapacityOfUser(){
		return (float) 1.0 / Constants.MAX_CLIENTS_TO_VM;
	}
	
	public boolean normalCapacity(){
		return this.capacity() < Constants.VM_CAPACITY_NORMAL_THRESHOLD;
	}
	
	public boolean checkNormalCapacity(float additiveCapacity){
		return this.capacity()+ additiveCapacity > Constants.VM_CAPACITY_NORMAL_THRESHOLD;
	}
	
	public boolean hasNormalCapacityAddingOneUser(){
		return this.capacity()+ this.additiveCapacityOfUser() < Constants.VM_CAPACITY_NORMAL_THRESHOLD;
	}
		
	public boolean notfullCapacity(){
		return this.capacity() <= Constants.VM_CAPACITY_FULL_THRESHOLD;
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
		return true;
	}

	



	@Override
	public String toString() {
		return "VMStats [vmID=" + vmID + ", firstPing=" + firstPing
				+ ", timeOfAllocation=" + timeOfAllocation
				+ ", timeToGetReady=" + timeToGetReady + "]";
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

	
}

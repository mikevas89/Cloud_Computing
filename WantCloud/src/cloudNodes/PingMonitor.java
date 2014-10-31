package cloudNodes;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import constants.Constants;
import constants.RegisteredUser;
import constants.VMStats;
import constants.VMstatus;

public class PingMonitor implements Runnable {
	


	private ConcurrentHashMap<String,ArrayList<RegisteredUser>> vmUsers; //registered clients
	private ConcurrentHashMap<String, VMStats> vmPool; //statistics of VM
	
	public PingMonitor( ConcurrentHashMap<String, VMStats> vmPool,ConcurrentHashMap<String,ArrayList<RegisteredUser>> vmUsers) {
		this.setVmPool(vmPool);
		this.setVmUsers(vmUsers);
		
	}

	@Override
	public void run() {
		
		while (true) {
			try {
				Thread.sleep(Constants.PING_MONITOR_CHECKING_PERIOD);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		
			for(Iterator<Entry<String,VMStats>> it= this.getVmPool().entrySet().iterator();it.hasNext();){
				Entry<String, VMStats> entry = it.next();
				
				//if the VM was at boot state and ping recieved set it to Run state
				if((entry.getValue().getVmStatus() == VMstatus.Booting) && (entry.getValue().getLastPingSent()!=0)){
					entry.getValue().setVmStatus(VMstatus.Running);
					entry.getValue().setFirstPing(System.currentTimeMillis());
					entry.getValue().setTimeToGetReady(entry.getValue().getFirstPing() - entry.getValue().getTimeOfAllocation());
					System.out.println("PING MONITOR SET TO RUNNING:"+entry.getKey());
					this.logVMPool();
				}
				
				//check for expired ping
				long currentTime = System.currentTimeMillis();
				
				if ((currentTime - entry.getValue().getLastPingSent()) >= Constants.PING_TIMEOUT && entry.getValue().getVmStatus() == VMstatus.Running) {
					//delete all the entries of the VM
					this.getVmPool().remove(entry.getKey());
					this.getVmUsers().remove(entry.getKey());
				}
			}
		}
	}
	
	public void logVMPool(){
		
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

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	

	/*---------------------------------------------------
	 *			 GETTERS AND SETTERS
	 ----------------------------------------------------
	 */


	public ConcurrentHashMap<String, ArrayList<RegisteredUser>> getVmUsers() {
		return vmUsers;
	}

	public ConcurrentHashMap<String, VMStats> getVmPool() {
		return vmPool;
	}

	public void setVmUsers(ConcurrentHashMap<String, ArrayList<RegisteredUser>> vmUsers) {
		this.vmUsers = vmUsers;
	}

	public void setVmPool(ConcurrentHashMap<String, VMStats> vmPool) {
		this.vmPool = vmPool;
	}

}
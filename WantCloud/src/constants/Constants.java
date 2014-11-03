package constants;

public class Constants {


	public static final int CLIENT_RMI= 1099;
	public static final String CLIENT_IP = "127.0.0.1";
	public static final int HEADNODE_RMI= 1099;
	public static final String HEADNODE_IP= "127.0.0.1";
	public static final String HEADNODE_NAME= "WantCloud";
	public static final int HEADNODE_ID= -1;
	
	public static final long PING_PERIOD = 6000;
	//public static final long USER_REQUEST_PERIOD = 3000;
	
	
	public static final long PING_MONITOR_CHECKING_PERIOD =PING_PERIOD/2;
	public static final long PING_TIMEOUT =PING_PERIOD *2000;
	public static final long VM_MONITOR_PERIOD = 5000;
	
	public static final long INITIAL_BOOT_TIME = 100000;
	public static final long INTIAL_COMPLETION_TIME = INITIAL_BOOT_TIME/2;
	
	public static final int EXPONENTIAL_MEAN = 25;
	public static final int EXPONENTIAL_SEED = 123456;
	
	public static final int MAX_CLIENTS_TO_VM = 5;
	public static final int MAX_CLIENTS_TO_VM_UPPER_THRESHOLD = 7;
	
//	public static final float VM_CAPACITY_NORMAL_THRESHOLD = (float) 0.7;
//	public static final float VM_CAPACITY_FULL_THRESHOLD = (float) 0.95;
	
}

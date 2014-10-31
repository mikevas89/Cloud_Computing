package constants;

public class Constants {


	public static final int CLIENT_RMI= 1099;
	public static final String CLIENT_IP = "127.0.0.1";
	public static final int HEADNODE_RMI= 1099;
	public static final String HEADNODE_IP= "127.0.0.1";
	public static final String HEADNODE_NAME= "WantCloud";
	public static final int HEADNODE_ID= -1;
	
	public static final long PING_PERIOD = 3000;
	
	public static final long PING_MONITOR_CHECKING_PERIOD =PING_PERIOD /5;
	public static final long PING_TIMEOUT =PING_PERIOD *8;
	public static final long VM_MONITOR_PERIOD = 1000;
	
	public static final int EXPONENTIAL_MEAN = 5;
	public static final int EXPONENTIAL_SEED = 123456;
	
	public static final int MAX_CLIENTS_TO_VM = 5;
	public static final float VM_CAPACITY_NORMAL_THRESHOLD = (float) 0.7;
	public static final float VM_CAPACITY_FULL_THRESHOLD = (float) 0.95;
	
}

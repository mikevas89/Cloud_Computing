package messages;

public class ReplyMessage extends Message implements java.io.Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public boolean reply;
	public String vmIP;
	public String receiverID;
	public String receiverIP;
	
	
	public boolean isReply() {
		return reply;
	}

	public String getVmIP() {
		return vmIP;
	}

	public ReplyMessage(int senderID, String senderIP,int receiverID, String receiverIP, boolean reply,String vmIP) {
		super(senderID, senderIP,receiverID, receiverIP);
		this.reply = reply;
		this.vmIP = vmIP;
	}

}

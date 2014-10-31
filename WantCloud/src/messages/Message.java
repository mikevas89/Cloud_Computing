package messages;

public class Message implements java.io.Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int senderID;
	private String senderIP;
	private int receiverID;
	private String receiverIP;

	
	public Message(int senderID, String senderIP, int receiverID, String receiverIP){
		this.setSenderID(senderID);
		this.setSenderIP(senderIP);
		this.setReceiverID(receiverID);
		this.setReceiverIP(receiverIP);
		
	}

	public int getSenderID() {
		return senderID;
	}
	public void setSenderID(int senderID) {
		this.senderID = senderID;
	}
	public String getSenderIP() {
		return senderIP;
	}
	public void setSenderIP(String senderIP) {
		this.senderIP = senderIP;
	}

	public int getReceiverID() {
		return receiverID;
	}

	public void setReceiverID(int receiverID) {
		this.receiverID = receiverID;
	}

	public String getReceiverIP() {
		return receiverIP;
	}

	public void setReceiverIP(String receiverIP) {
		this.receiverIP = receiverIP;
	}

}

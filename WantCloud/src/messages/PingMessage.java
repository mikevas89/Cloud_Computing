package messages;

public class PingMessage extends Message {

	private static final long serialVersionUID = 1L;

	public PingMessage(int senderID, String senderIP, int receiverID,
			String receiverIP) {
		super(senderID, senderIP, receiverID, receiverIP);
	}
	
	

}

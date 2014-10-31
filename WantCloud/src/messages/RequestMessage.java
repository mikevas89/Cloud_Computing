package messages;

import constants.*;

public class RequestMessage extends Message implements java.io.Serializable{

	private RequestType requestType;
	private String vmIP;
	
	public RequestType getRequestType() {
		return requestType;
	}

	public void setRequestType(RequestType requestType) {
		this.requestType = requestType;
	}

	
	private static final long serialVersionUID = 1L;

	public RequestMessage(int senderID, RequestType type) {
		super(senderID, Constants.CLIENT_IP, Constants.HEADNODE_ID,Constants.HEADNODE_IP);
		this.requestType = type;
	}
	
	public RequestMessage(RequestMessage mesg) {
		super(mesg.getSenderID(), mesg.getSenderIP(), mesg.getReceiverID(),mesg.getReceiverIP());
		this.requestType = mesg.getRequestType();
	}

	public String getVmIP() {
		return vmIP;
	}

	public void setVmIP(String vmIP) {
		this.vmIP = vmIP;
	}


}

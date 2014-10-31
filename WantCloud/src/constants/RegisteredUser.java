package constants;

public class RegisteredUser {
	
	private int id;
	private String vmIPofUser;
	
	@Override
	public String toString() {
		return "RegisteredUser [id=" + id + ", vmIPofUser=" + vmIPofUser + "]";
	}

	public RegisteredUser(int id,String vmIPofUser){
		this.id = id;
		this.vmIPofUser= vmIPofUser;		
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getVmIPofUser() {
		return vmIPofUser;
	}

	public void setVmIPofUser(String vmIPofUser) {
		this.vmIPofUser = vmIPofUser;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + id;
		result = prime * result
				+ ((vmIPofUser == null) ? 0 : vmIPofUser.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RegisteredUser other = (RegisteredUser) obj;
		if (id != other.id)
			return false;
		if (vmIPofUser == null) {
			if (other.vmIPofUser != null)
				return false;
		} else if (!vmIPofUser.equals(other.vmIPofUser))
			return false;
		return true;
	}

}

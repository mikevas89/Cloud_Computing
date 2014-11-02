package client;


import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.random.MersenneTwister;

import constants.Constants;
import constants.UserType;

public class UserCreator {



	private int userID;
	
	ExponentialDistribution expGen;
	UniformIntegerDistribution uniGen;

	public UserCreator() {
		this.setUserID(1);
	}

	public static void main(String[] args) {

		UserCreator usercreator = new UserCreator();

		usercreator.createDistribution();
		//usercreator.createUsers(10);

	}
	
	public void createUsers(int num){
		
		for(int i=0; i<num; i++){
			this.createUser(0);
			try {
				Thread.sleep(50000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public void createDistribution() {
		
		MersenneTwister rngExp = new MersenneTwister(Constants.EXPONENTIAL_SEED);
		MersenneTwister rngUni = new MersenneTwister(Constants.EXPONENTIAL_SEED);

		// Generate events at an average rate of 10 per minute.
		this.setExpGen(new ExponentialDistribution(rngExp,Constants.EXPONENTIAL_MEAN));

		this.setUniGen(new UniformIntegerDistribution(rngUni,0, 1));

		while (true) {
			//create users per minute according to exponential distribution
			
			long num = Math.round(this.getExpGen().sample());
			//resample if num == 0
			while(num == 0){
				num = Math.round(this.getExpGen().sample());
			}
			this.createUsersForMinute(num);
		}
	
	}
	
	
	public void createUsersForMinute(long num){
		
		long numUsers = num;
		long timeToSleepPerUser;
		if(numUsers==0)
			timeToSleepPerUser = Math.round((60* 1000 ));
		else
			timeToSleepPerUser = Math.round((60* 1000 / numUsers));
		
		System.out.println("Create "+ numUsers + " users for this minute");
		
		while(numUsers>0){
			try {
				Thread.sleep(timeToSleepPerUser);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}			
			//selects randomly the type of user
			int type = this.getUniGen().sample();
			this.createUser(type);
			numUsers--;
		}
		
	}
	

	public void createUser(int type) {

		Runnable user = null;

		if (type == 0){
			user = new User(this.getUserID(), UserType.LightUser);
			System.out.println("I created a light user ");
		}
		else{
			user = new User(this.getUserID(), UserType.HeavyUser);
			System.out.println("I created a heavy user ");
		}

		this.setUserID(this.getUserID() + 1);
		new Thread(user).start();

	}

	public int getUserID() {
		return userID;
	}

	public void setUserID(int userID) {
		this.userID = userID;
	}
	public ExponentialDistribution getExpGen() {
		return expGen;
	}

	public UniformIntegerDistribution getUniGen() {
		return uniGen;
	}

	public void setExpGen(ExponentialDistribution expGen) {
		this.expGen = expGen;
	}

	public void setUniGen(UniformIntegerDistribution uniGen) {
		this.uniGen = uniGen;
	}

}

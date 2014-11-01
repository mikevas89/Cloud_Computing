package constants;

public class Pair<L,R> {

	private L l;
	private R r;
	
	public Pair(L l,R r){
		this.l =l;
		this.r =r;
	}
	

	public void setRight(R r){
		this.r = r;
	}
	
	public void setLeft(L l){
		this.l = l;
	}
	
	public R getRight(){
		return this.r;
	}
	
	public L getLeft(){
		return this.l;
	}
	
}

package org.sesy.peershare.iface;

import android.os.Parcel;
import android.os.Parcelable;

public class ACLPolicy implements Parcelable {

	private long policy_id;
	private String policy_name;
	
	public ACLPolicy(long id, String name) {
		this.policy_id = id;
		this.policy_name = name;
	}
	
	public ACLPolicy(ACLPolicy other) {
		this.policy_id = other.getID();
		this.policy_name = other.getName();
	}
	
	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		// TODO Auto-generated method stub
		dest.writeLong(this.policy_id);
		dest.writeString(this.policy_name);
	}
	
    public static final Parcelable.Creator<ACLPolicy> CREATOR = new Parcelable.Creator<ACLPolicy>() {
    	public ACLPolicy createFromParcel(Parcel in) {
    		return new ACLPolicy(in);
    	}

    	public ACLPolicy[] newArray(int size) {
    		return new ACLPolicy[size];
    	}
    };
    
    private ACLPolicy(Parcel in) {
    	this.policy_id = in.readLong();
    	this.policy_name = in.readString();
    }

	public long getID() {
		return this.policy_id;
	}
	
	public String getName() {
		return this.policy_name;
	}
}

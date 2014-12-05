package org.sesy.peershare.core;

import android.os.Parcel;
import android.os.Parcelable;

public class PersonInfo implements Parcelable {
	
	long person_id;
	String socialName;
	String socialNetwork;
	int socialNetworkID;
	
	public PersonInfo(long pid, String name, String network, int nid) {
		this.person_id = pid;
		this.socialName = name;
		this.socialNetwork = network;
		this.socialNetworkID = nid;
	}
	
	public PersonInfo() {
		this.person_id = -1;
		this.socialName = new String();
		this.socialNetwork = new String();
		this.socialNetworkID = -1;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(person_id);
		dest.writeString(socialName);
		dest.writeString(socialNetwork);
		dest.writeInt(socialNetworkID);
	}
	
    public static final Parcelable.Creator<PersonInfo> CREATOR = new Parcelable.Creator<PersonInfo>() {
    	public PersonInfo createFromParcel(Parcel in) {
    		return new PersonInfo(in);
    	}

    	public PersonInfo[] newArray(int size) {
    		return new PersonInfo[size];
    	}
    };
    
    protected PersonInfo(Parcel in) {
    	person_id = in.readLong();
    	socialName = in.readString();
    	socialNetwork = in.readString();
    	socialNetworkID = in.readInt();
    }
    
    public String getSocialName() {
    	return socialName;
    }
    
    public String getSocialNetwork() {
    	return socialNetwork;
    }
    
    public long getPersonSocialId() {
    	return person_id;
    }
    
    public int getSocialNetworkId() {
    	return socialNetworkID;
    }
}

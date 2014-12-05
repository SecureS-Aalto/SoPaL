package org.sesy.peershare.iface;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class SocialInfo implements Parcelable, Serializable {

	public static final int FACEBOOK = 1;
	
	private String socialID;
	private String socialName;
	private int networkID;
	
	public SocialInfo(String id, String name, int network) {
		this.socialID = id;
		this.socialName = name;
		this.networkID = network;
	}
	
	public SocialInfo(SocialInfo other) {
		this.socialID = other.getSocialID();
		this.socialName = other.getSocialName();
		this.networkID = other.getNetworkID();
	}
	
	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(this.socialID);
		dest.writeString(this.socialName);
		dest.writeInt(this.networkID);
	}
	
    public static final Parcelable.Creator<SocialInfo> CREATOR = new Parcelable.Creator<SocialInfo>() {
    	public SocialInfo createFromParcel(Parcel in) {
    		return new SocialInfo(in);
    	}

    	public SocialInfo[] newArray(int size) {
    		return new SocialInfo[size];
    	}
    };
    
    private SocialInfo(Parcel in) {
    	this.socialID = in.readString();
    	this.socialName = in.readString();
    	this.networkID = in.readInt();
    }
	
	public String getSocialID() {
		return this.socialID;
	}
	
	public String getSocialName() {
		return this.socialName;
	}
	
	public int getNetworkID() {
		return this.networkID;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		SocialInfo other = (SocialInfo) obj;
		if (networkID != other.networkID) {
			return false;
		}
		if (!socialID.equals(other.socialID)) {
			return false;
		}
		/*if (socialName == null) {
			if (other.socialName != null) {
				return false;
			}
		} else if (!socialName.equals(other.socialName)) {
			return false;
		}*/
		return true;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + networkID;
		result = prime * result + ((socialName == null) ? 0 : socialName.hashCode());
		result = prime * result
				+ ((socialName == null) ? 0 : socialName.hashCode());
		return result;
	}
	
	@Override
	public String toString() {
		return this.socialName + " " + this.socialID + " " + this.networkID;
	}
}

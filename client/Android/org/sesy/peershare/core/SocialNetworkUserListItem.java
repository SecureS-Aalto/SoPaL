package org.sesy.peershare.core;

import android.os.Parcel;
import android.os.Parcelable;

public class SocialNetworkUserListItem implements Parcelable {
	private long listID;
	private String name;
	private String sn;
	
	public SocialNetworkUserListItem(long id, String name, String sn) {
		this.listID = id;
		this.name = name;
		this.sn = sn;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel arg0, int arg1) {
		arg0.writeLong(listID);
		arg0.writeString(name);
		arg0.writeString(sn);
	}
	
    public static final Parcelable.Creator<SocialNetworkUserListItem> CREATOR = new Parcelable.Creator<SocialNetworkUserListItem>() {
    	public SocialNetworkUserListItem createFromParcel(Parcel in) {
    		return new SocialNetworkUserListItem(in);
    	}

    	public SocialNetworkUserListItem[] newArray(int size) {
    		return new SocialNetworkUserListItem[size];
    	}
    };

    private SocialNetworkUserListItem(Parcel in) {
    	listID = in.readLong();
    	name = in.readString();
    	sn = in.readString();
    }
	
	public long getListID() {
		return listID;
	}
	
	public String getListName() {
		return name;
	}
	
	public String getSocialNetwork() {
		return sn;
	}
}

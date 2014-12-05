package org.sesy.peershare.core;

import android.os.Parcel;
import android.os.Parcelable;

public class PersonBindingInfo extends PersonInfo implements Parcelable {
	private boolean user_asserted;
	private String pseudo;
	
	public PersonBindingInfo(boolean binding_type, long social_user_id, String social_name, String social_network, int network_id, String pseudo) {
		super(social_user_id,social_name,social_network,network_id);
		this.user_asserted = binding_type;
		this.pseudo = pseudo;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		super.writeToParcel(dest, flags);
		if(this.user_asserted)
			dest.writeInt(1);
		else
			dest.writeInt(0);
		dest.writeString(pseudo);
	}
	
    public static final Parcelable.Creator<PersonBindingInfo> CREATOR = new Parcelable.Creator<PersonBindingInfo>() {
    	public PersonBindingInfo createFromParcel(Parcel in) {
    		return new PersonBindingInfo(in);
    	}

    	public PersonBindingInfo[] newArray(int size) {
    		return new PersonBindingInfo[size];
    	}
    };
    
    private PersonBindingInfo(Parcel in) {
    	super(in);
    	if(in.readInt() == 1)
    		this.user_asserted = true;
    	else
    		this.user_asserted = false;
    	pseudo = in.readString();
    }
	
	public boolean IsUserAsserted() {
		return user_asserted;
	}
	
	public String getPseudonym() {
		return pseudo;
	}
}

package org.sesy.peershare.core;

import android.os.Parcel;
import android.os.Parcelable;


/**
 * This class holds information about binding between secret and person
 * */
public class SecretBinding implements Parcelable {
	
	protected String social_name; // user social name in the social network
	protected int social_type; // type of social network used (e.g. Facebook)
	protected String secret; // actual secret value
	protected int binding_type; // type of binding used (either user-asserted or owner-asserted) 
	
	public SecretBinding(String social_name, int social_type, String secret, int type) {
		this.social_name = social_name;
		this.social_type = social_type;
		this.secret = secret;
		this.binding_type = type;
	}
	
	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void writeToParcel(Parcel arg0, int arg1) {
		// TODO Auto-generated method stub
		arg0.writeString(social_name);
		arg0.writeInt(social_type);
		arg0.writeString(secret);
		arg0.writeInt(binding_type);
	}
	
    public static final Parcelable.Creator<SecretBinding> CREATOR = new Parcelable.Creator<SecretBinding>() {
    	public SecretBinding createFromParcel(Parcel in) {
    		return new SecretBinding(in);
    	}

    	public SecretBinding[] newArray(int size) {
    		return new SecretBinding[size];
    	}
    };
    
    private SecretBinding(Parcel in) {
    	social_name = in.readString();
    	social_type = in.readInt();
    	secret = in.readString();
    	binding_type = in.readInt();
    }

	public String getSocialName() {
		return social_name;
	}
	
	public int getSocialNetwork() {
		return social_type;
	}
	
	public String getSecretDescription() {
		return secret;
	}
	
	public boolean isUserAssertedBinding() {
		if(binding_type == 1) 
			return true;
		else
			return false;
	}
}

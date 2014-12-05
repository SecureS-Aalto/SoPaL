package org.sesy.peershare.core;

import android.os.Parcel;
import android.os.Parcelable;

public class UISecret implements Parcelable {
	protected long local_ID;
	protected String text;
	protected String policy;
	
	public UISecret(long id, String t, String p) {	
		local_ID = id;
		text = t;
		policy = p;
	}
	
	public UISecret(UISecret s) {
		this.local_ID = s.getLocalID();
		this.text = s. getSecretText();
		this.policy = s.getSecretPolicy();
	}
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	@Override
	public void writeToParcel(Parcel arg0, int arg1) {
		arg0.writeLong(local_ID);
		arg0.writeString(text);
		arg0.writeString(policy);
	}
	
    public static final Parcelable.Creator<UISecret> CREATOR = new Parcelable.Creator<UISecret>() {
    	public UISecret createFromParcel(Parcel in) {
    		return new UISecret(in);
    	}

    	public UISecret[] newArray(int size) {
    		return new UISecret[size];
    	}
    };

    private UISecret(Parcel in) {
    	local_ID = in.readLong();
    	text = in.readString();
    	policy = in.readString();
    }
	
	public String getSecretText() {
		return text;
	}
	
	public String getSecretPolicy() {
		return policy;
	}
	
	public void setSecretPolicy(String p) {
		policy = p;
	}
	
	public long getLocalID() {
		return local_ID;
	}
	
}

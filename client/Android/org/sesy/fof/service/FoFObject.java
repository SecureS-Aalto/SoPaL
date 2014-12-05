package org.sesy.fof.service;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class FoFObject implements Serializable, Parcelable {

	static final long serialVersionUID = 4209360273818925922L;
	protected String local_id;
	protected String remote_id;
	protected byte session_id;
	
	public FoFObject(String localid, String remoteid, byte sessionid) {
		this.local_id = localid;
		this.remote_id = remoteid;
		this.session_id = sessionid;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeSerializable(this);
	}

	public static final Parcelable.Creator<FoFObject> CREATOR= new Parcelable.Creator<FoFObject>() {

		@Override
		public FoFObject createFromParcel(Parcel source) {
			return (FoFObject) source.readSerializable();
		}

		@Override
		public FoFObject[] newArray(int size) {
			return new FoFObject[size];
		}

	};
	
	protected FoFObject(Parcel source) {
		FoFObject tmp = (FoFObject)source.readSerializable();
		this.local_id = tmp.getLocalHostID();
		this.remote_id = tmp.getRemoteHostID();
		this.session_id = tmp.getSessionID();
	}
	
	public String getLocalHostID() {
		return this.local_id;
	}
	
	public String getRemoteHostID() {
		return this.remote_id;
	}
	
	public byte getSessionID() {
		return this.session_id;
	}
}

package org.sesy.fof.service;

import java.io.Serializable;

import javax.crypto.SecretKey;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class ProtocolResult extends FoFObject implements Parcelable, Serializable {
	
	public final static class ResponseType {
		public static final int PSI_CA = 1;
		public static final int PSI_BF = 2;
	}
	
	static final long serialVersionUID = 4209360273818925922L;
	
	protected int response_type;
	protected SecretKey key;
	
	public ProtocolResult(String local_id, String remote_id, byte session_id, int type, SecretKey key_) {
		super(local_id,remote_id,session_id);
		this.response_type = type;
		this.key = key_;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		//dest.writeInt(this.response_type);
		dest.writeSerializable(this);
	}
	
	public static final Parcelable.Creator<ProtocolResult> CREATOR= new Parcelable.Creator<ProtocolResult>() {

		@Override
		public ProtocolResult createFromParcel(Parcel source) {
			return (ProtocolResult) source.readSerializable(); //new ControllerResultResponse(source);
		}

		@Override
		public ProtocolResult[] newArray(int size) {
			return new ProtocolResult[size];
		}

	};

	protected ProtocolResult(Parcel source) {
		super(source);
		ProtocolResult tmp = (ProtocolResult)source.readSerializable();
		this.response_type = tmp.getResponseType();
		this.key = tmp.getDHKey();
	}
	
	public int getResponseType() {
		return this.response_type;
	}
	
	public SecretKey getDHKey() {
		return this.key;
	}

}

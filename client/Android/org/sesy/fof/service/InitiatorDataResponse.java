package org.sesy.fof.service;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class InitiatorDataResponse extends FoFObject implements Parcelable, Serializable {

	public static final int STATUS_DONE = 0;
	public static final int STATUS_WAIT = 1;
	
	public final static class ResponseType {
		public static final int PSI_CA = 1;
		public static final int PSI_BFPSI = 2;
	}
	
	static final long serialVersionUID = 4209360273818925922L;
	
	protected int response_type;
		
	public InitiatorDataResponse(int type, String localid, String remoteid, byte sessionid) {
		super(localid,remoteid,sessionid);
		this.response_type = type;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeSerializable(this);
	}
	
	public static final Parcelable.Creator<InitiatorDataResponse> CREATOR= new Parcelable.Creator<InitiatorDataResponse>() {

		@Override
		public InitiatorDataResponse createFromParcel(Parcel source) {
			return (InitiatorDataResponse) source.readSerializable(); //new RequestorDataResponse(source);
		}

		@Override
		public InitiatorDataResponse[] newArray(int size) {
			return new InitiatorDataResponse[size];
		}

	};

	protected InitiatorDataResponse(Parcel source) {
		super(source);
		InitiatorDataResponse tmp = (InitiatorDataResponse)source.readSerializable();
		this.response_type = tmp.getResponseType();
	}

	public int getResponseType() {
		return this.response_type;
	}
	
}

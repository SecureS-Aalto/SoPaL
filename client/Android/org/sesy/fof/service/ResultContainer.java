package org.sesy.fof.service;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class ResultContainer implements Parcelable, Serializable {
	
	public static final int STATUS_DONE = 0;
	public static final int STATUS_WAIT = 1;
	public static final int STATUS_ERROR = 2;
	public static final int STATUS_REJECTED = 3;
	
	private int status;
	private Object msg;
	private String sessionID;
	
	static final long serialVersionUID = 4209360273818925922L;
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	public ResultContainer(int stat, Object mesg, String id) {
		this.status = stat;
		this.msg = mesg;
		this.sessionID = id;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeSerializable(this);
	}
	
	public static final Parcelable.Creator<ResultContainer> CREATOR= new Parcelable.Creator<ResultContainer>() {

		@Override
		public ResultContainer createFromParcel(Parcel source) {
			return new ResultContainer(source);
		}

		@Override
		public ResultContainer[] newArray(int size) {
			return new ResultContainer[size];
		}

	};

	private ResultContainer(Parcel source) {
		ResultContainer tmp = (ResultContainer)source.readSerializable();
		this.status = tmp.getProtocolStatus();
		this.msg = tmp.getProtocolMessage();
		this.sessionID = tmp.getSessionID();
	}
	
	public int getProtocolStatus() {
		return this.status;
	}
	
	public Object getProtocolMessage() {
		return this.msg;
	}
	
	public String getSessionID() {
		return this.sessionID;
	}

}

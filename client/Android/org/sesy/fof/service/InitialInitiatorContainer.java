package org.sesy.fof.service;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class InitialInitiatorContainer extends FoFObject implements Parcelable, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	KeyDataContainer pk_container;
	boolean[] supported_types; // index 0 is PSI-CA, index 1 is BF
	int no_friends;
	
	public InitialInitiatorContainer(KeyDataContainer pk, boolean psi_support, boolean bf_support, int no, 
			String host_id, byte session_id) {
		super(host_id,null,session_id);
		this.pk_container = pk;
		this.supported_types = new boolean[2];
		if(psi_support)
			this.supported_types[0] = true;
		else
			this.supported_types[0] = false;
		
		if(bf_support)
			this.supported_types[1] = true;
		else
			this.supported_types[1] = false;
		
		this.no_friends = no;
		this.local_id = host_id;
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeParcelable(pk_container, flags);
		dest.writeBooleanArray(supported_types);
	}
	
	public static final Parcelable.Creator<InitialInitiatorContainer> CREATOR = new Parcelable.Creator<InitialInitiatorContainer>() {
		@Override
		public InitialInitiatorContainer createFromParcel(Parcel source) {
			return new InitialInitiatorContainer(source);
		}

		@Override
		public InitialInitiatorContainer[] newArray(int size) {
			return new InitialInitiatorContainer[size];
		}
	};
	
	private InitialInitiatorContainer(Parcel source) {
		super(source);
		this.pk_container = source.readParcelable(KeyDataContainer.class.getClassLoader());
		source.readBooleanArray(supported_types);
	}
	
	public KeyDataContainer getPublicKeyDataContainer() {
		return this.pk_container;
	}
	
	public boolean[] getSupportedTypes() {
		return this.supported_types;
	}
	
	public int getNumberFriends() {
		return this.no_friends;
	}
	
	public String toString() {
		return "InitialInitiatorContainer: friends=" + this.no_friends + " id=" + this.local_id;
	}
}

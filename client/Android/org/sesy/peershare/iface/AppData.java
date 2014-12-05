package org.sesy.peershare.iface;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

public class AppData implements Parcelable, Serializable {

	public static class Specificity {
		public final static int UNDISCLOSED = -1;
		public final static int USER_SPECIFIC = 1;
		public final static int DEVICE_SPECIFIC = 2;
	}
	
	public static class BindingType {
		public final static int USER_ASSERTED = 1;
		public final static int OWNER_ASSERTED = 2;
	}
	
	public static class DataType {
		public static final int BTMAC = 1;
		public static final int WIFIMAC = 2;
		public static final int SCAMPIID = 3;
		public static final int CROWDAPP = 4;
		public static final int PUBLICKEY = 5;
	}
	
	public static class DataSensitivity {
		public static final int PRIVATE = 1;
		public static final int PUBLIC = 2;
	}
	
	public static class SocialNetwork {
		public static final int FACEBOOK = 1;	
	}
	
	public static class SharingPolicy {
		public static final String DEFAULT = "Friends";
	}
	
	/** ID of the data type. It corresponds to the DataType constants. */
	protected int data_type_id; 
	/** Algorithm used to create secret data (e.g. SHA-1/HMAC) */
	protected String data_algorithm;
	/** Human readable description of the data */
	protected String description;
	/** Secret specificity to indicate if the data is device- or user-specific. It corresponds to Specificity constants. */
	protected int data_specificity;
	/** Type of binding. It corresponds to BindingType constants. */
	protected int data_binding_type_id;
	/** Sensitivity of the secret. If the data can be made public or not. It corresponds to DataSensitivity constant. */
	protected int data_sensitivity;
	
	/** Actual data value */
	protected String data_value;
	/** Data sharing policy */
	protected String acl_policy; // currently only single Facebook allowed lists
	/** Timestamp of data creation */
	protected Timestamp created;
	/** Timestamp of data validity expiry */
	protected Timestamp expires;
	protected int share;
	
	/*protected String owner_id;
	protected String owner_name;
	protected int sn_id;*/
	protected List<SocialInfo> owner_info;
	
	public AppData(int type, String algorithm, String value, String description, int specificity, int binding_type,
			int sensitivity, String policy, Timestamp created, Timestamp expires, boolean share, 
			List<SocialInfo> owners) {
		this.data_type_id = type;
		this.data_algorithm = algorithm;
		this.data_value = value;
		this.data_specificity = specificity;
		this.data_binding_type_id = binding_type;
		this.data_sensitivity = sensitivity;
		this.description = description;
		this.acl_policy = policy;
		this.created = created;
		this.expires = expires;
		if(share)
			this.share = 1;
		else
			this.share = 0;
		/*this.owner_id = owner_id;
		this.owner_name = owner_name;
		this.sn_id = network_id;*/
		this.owner_info = owners;
	}
	
	public AppData(AppData data) {
		this.data_type_id = data.getDataType();
		this.data_algorithm = data.getDataAlgorithm();
		this.data_value = data.getDataValue();
		this.data_specificity = data.getDataSpecificity();
		this.data_binding_type_id = data.getDataBindingType();
		this.data_sensitivity = data.getDataSensitivity();
		this.description = data.getDataDescription();
		this.acl_policy = data.getSharingPolicy();
		this.created = data.getCreationTimestamp();
		this.expires = data.getExpiryTimestamp();
		if(data.isSharable())
			this.share = 1;
		else
			this.share = 0;
		/*this.owner_id = data.getDataOwnerSocialID();
		this.owner_name = data.getDataOwnerSocialName();
		this.sn_id = data.getDataOwnerNetworkID();*/
		this.owner_info = data.getDataOwnerInfo();
	}
	
	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		/*dest.writeInt(this.data_type_id);
		dest.writeString(this.data_algorithm);
		dest.writeString(this.data_value);
		dest.writeInt(this.data_specificity);
		dest.writeInt(this.data_binding_type_id);
		dest.writeInt(this.data_sensitivity);
		dest.writeString(this.description);
		dest.writeString(this.acl_policy);
		dest.writeString(this.created.toString());
		dest.writeString(this.expires.toString());
		dest.writeInt(this.share);*/
		/*dest.writeString(this.owner_id);
		dest.writeString(this.owner_name);
		dest.writeInt(this.sn_id);*/
		dest.writeSerializable(this);
	}
	
    public static final Parcelable.Creator<AppData> CREATOR = new Parcelable.Creator<AppData>() {
    	public AppData createFromParcel(Parcel in) {
    		return (AppData)in.readSerializable();
    	}

    	public AppData[] newArray(int size) {
    		return new AppData[size];
    	}
    };
    
   /* protected AppData(Parcel in) {
    	AppData tmp = (AppData)*/
    	/*this.data_type_id = in.readInt();
    	this.data_algorithm = in.readString();
    	this.data_value = in.readString();
    	this.data_specificity = in.readInt();
    	this.data_binding_type_id = in.readInt();
    	this.data_sensitivity = in.readInt();
    	this.description = in.readString();
    	this.acl_policy = in.readString();
    	this.created = Timestamp.valueOf(in.readString());
    	this.expires = Timestamp.valueOf(in.readString());
    	this.share = in.readInt();*/
    	/*this.owner_id = in.readString();
    	this.owner_name = in.readString();
    	this.sn_id = in.readInt();*/
    	
    //}

	public int getDataType() {
		return this.data_type_id;
	}
	
	public String getDataAlgorithm() {
		return this.data_algorithm;
	}
	
	public String getDataValue() {
		return this.data_value;
	}
	
	public int getDataBindingType() {
		return this.data_binding_type_id;
	}
	
	public int getDataSpecificity() {
		return this.data_specificity;
	}
	
	public int getDataSensitivity() {
		return this.data_sensitivity;
	}
	
	public String getDataDescription() {
		return this.description;
	}
	
	public String getSharingPolicy() {
		return this.acl_policy;
	}
	
	public void setSharingPolicy(String policy) {
		this.acl_policy = policy;
	}
	
	public Timestamp getCreationTimestamp() {
		return this.created;
	}
	
	public Timestamp getExpiryTimestamp() {
		return this.expires;
	}
	
	public boolean isSharable() {
		if(this.share == 1)
			return true;
		else
			return false;
	}
	
	public List<SocialInfo> getDataOwnerInfo() {
		return this.owner_info;
	}
	
	/*public String getDataOwnerSocialID() {
		return this.owner_id;
	}
	
	public String getDataOwnerSocialName() {
		return this.owner_name;
	}
	
	public int getDataOwnerNetworkID() {
		return this.sn_id;
	}*/
}

package org.sesy.peershare.core;

import java.sql.Timestamp;
import java.util.List;

import org.sesy.peershare.iface.SocialInfo;

/**
 * 
 * @author mnagy
 * This class holds infromation about all details of particular secret
 */
public class SecretDetails extends UISecret {
	
	public static long UNASSIGNED = -1; // the secret has never been uploaded
	public static long UNKNOWN = -2; // this secret doesn't belong to me, so I don't know it
	
	private int type;
	private String algorithm;
	private String value;
	private int binding_type;
	private int sensitivity;
	private int specificity;
	private Timestamp created;
	private Timestamp expires;
	private long object_id;
	
	/*private int SNtype;
	private String social_name;
	private long social_id;*/
	private List<SocialInfo> owner;
	
	private int platform_id;
	private String platform_app_id;
	
	public SecretDetails() {
		super(-1,new String(),new String());
		this.type = -1;
		this.object_id = -1;
		//this.SNtype = -1;
		this.binding_type = -1;
		this.sensitivity = -1;
		this.specificity = -1;
		this.created = new Timestamp(-1);
		this.expires = new Timestamp(-1);
		this.algorithm = new String();
		this.value = new String();
		//this.social_name = new String();
		this.platform_id = -1;
		this.platform_app_id = new String();
	}
	
	public SecretDetails(int type, String algorithm, String value, int binding, int specificity, int sensitivity, String policy, 
			String description, Timestamp created, Timestamp expires, int local_id, long object_id, List<SocialInfo> owners,
			int platform, String platform_app) {
		super(local_id,description,policy);
		this.type = type;
		this.algorithm = algorithm;
		this.value = value;
		this.binding_type = binding;
		this.specificity = specificity;
		this.sensitivity = sensitivity;
		this.created = created;
		this.expires = expires;
		this.object_id = object_id;
		/*this.SNtype = SNtype;
		this.social_name = social_name;
		this.social_id = social_id;*/
		this.owner = owners;
		this.platform_id = platform;
		this.platform_app_id = platform_app;
	}
	
	public SecretDetails(int type, String algorithm, String value, int binding, int specificity, int sensitivity, String policy, 
			String description, int SNtype, Timestamp created, Timestamp expires, int local_id, long object_id, int platform, 
			String platform_app) {
		super(local_id,description,policy);
		this.type = type;
		this.algorithm = algorithm;
		this.value = value;
		this.specificity = specificity;
		this.sensitivity = sensitivity;
		this.binding_type = binding;
		this.created = created;
		this.expires = expires;
		this.object_id = object_id;
		/*this.SNtype = SNtype;
		this.social_name = new String();
		this.social_id = -1;*/
		this.platform_id = platform;
		this.platform_app_id = platform_app;
	}
	
	public SecretDetails(int type, String algorithm, String value, String description, int binding, int specificity, int sensitivity, 
			Timestamp created, Timestamp expires, long object_id, List<SocialInfo> ids, int platform, 
			String platform_app) {
		super(-1,description,new String(""));
		this.type = type;
		this.algorithm = algorithm;
		this.value = value;
		this.specificity = specificity;
		this.sensitivity = sensitivity;
		this.binding_type = binding;
		this.created = created;
		this.expires = expires;
		this.object_id = object_id;
		/*this.SNtype = SNtype;
		this.social_name = social_name;
		this.social_id = social_id;*/
		this.owner = ids;
		this.platform_id = platform;
		this.platform_app_id = platform_app;
	}
	
	public SecretDetails(int type, String algorithm, String value, String policy, String description, int binding, int specificity, int sensitivity, 
			Timestamp created, Timestamp expires, long object_id, List<SocialInfo> ids, int platform, String platform_app) {
		super(-1,description,policy);
		this.type = type;
		this.algorithm = algorithm;
		this.value = value;
		this.specificity = specificity;
		this.sensitivity = sensitivity;
		this.binding_type = binding;
		this.created = created;
		this.expires = expires;
		this.object_id = object_id;
		/*this.SNtype = SNtype;
		this.social_name = social_name;
		this.social_id = social_id;*/
		this.owner = ids;
		this.platform_id = platform;
		this.platform_app_id = platform_app;
	}
	
	public SecretDetails(int type, String algorithm, String value, String description, int binding, int specificity, int sensitivity, 
			Timestamp created, Timestamp expires, List<SocialInfo> ids, int platform, String platform_app) {
		super(-1,description,new String(""));
		this.type = type;
		this.algorithm = algorithm;
		this.value = value;
		this.specificity = specificity;
		this.sensitivity = sensitivity;
		this.binding_type = binding;
		this.created = created;
		this.expires = expires;
		this.object_id = -1;
		/*this.SNtype = SNtype;
		this.social_name = social_name;
		this.social_id = social_id;*/
		this.owner = ids;
		this.platform_id = platform;
		this.platform_app_id = platform_app;
	}
	
	public int getSecretType() {
		return this.type;
	}
	
	public String getSecretAlgorithm() {
		return this.algorithm;
	}
	
	public String getSecretValue() {
		return this.value;
	}

	
	public Timestamp getSecretCreationTS() {
		return this.created;
	}
	
	public Timestamp getSecretExpiryTS() {
		return this.expires;
	}
	
	public long getObjectID() {
		return this.object_id;
	}
	
	public List<SocialInfo> getSecretDataOwners() {
		return this.owner;
	}
	
	public int getBindingType() {
		return this.binding_type;
	}
	
	public int getSecretSpecificity() {
		return this.specificity;
	}
	
	public int getSecretSensitivity() {
		return this.sensitivity;
	}
	
	public int getPlatformID() {
		return this.platform_id;
	}
	
	public String getPlatformAppID() {
		return this.platform_app_id;
	}
}

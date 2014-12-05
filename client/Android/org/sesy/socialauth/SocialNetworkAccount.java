package org.sesy.socialauth;


public class SocialNetworkAccount {
	private String socialNetworkName; // provider ID
	private int socialNetworkID;
	private String fullName;
	private String userID; // validate ID
	private String userToken;
	
	public SocialNetworkAccount() { }
	// getters and setters
	public String getSocialNetworkName() { return socialNetworkName; }
	public int getSocialNetworkID() { return socialNetworkID; }
	public String getUsername() { return fullName; }
	public String getUserID() { return userID; }
	public String getUserToken() { return userToken; }
	
	public void setUserID(String id) { this.userID = id; }
	public void setSocialNetworkID(int id) { this.socialNetworkID = id; }
	public void setSocialNetworkName(String name) { this.socialNetworkName = name; }
	public void setUsername(String name) { this.fullName = name; }
	public void setUserToken(String token) { this.userToken = token; }
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		SocialNetworkAccount other = (SocialNetworkAccount) obj;
		if(socialNetworkID != other.socialNetworkID) {
			return false;
		}
		if(!userID.equals(other.userID)) {
			return false;
		}
		/*if(!fullName.equals(other.fullName)) {
			return false;
		}
		if(!userToken.equals(other.userToken)) {
			return false;
		}
		if (!socialNetworkName.equals(other.socialNetworkName)) {
			return false;
		}*/
		return true;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + socialNetworkID;
		result = prime * result
				+ ((userID == null) ? 0: userID.hashCode());
		return result;
	}
	
	@Override
	public String toString() {
		return "Social network: " + socialNetworkID + " userID: " + userID + " username: " + fullName + " network name: " + socialNetworkName;
	}
}

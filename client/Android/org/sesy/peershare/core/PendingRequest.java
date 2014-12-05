package org.sesy.peershare.core;

public class PendingRequest {
	
	private String url;
	private String request_type;
	private String body;
	private long id;
	
	public PendingRequest(long id, String url, String request, String body) {
		this.id = id; 
		this.url = url;
		this.request_type = request;
		this.body = body;
	}
	
	public long getRequestID() {
		return this.id;
	}
	
	public String getRequestUrl() {
		return this.url;
	}
	
	public String getRequestType() {
		return this.request_type;
	}
	
	public String getRequestBody() {
		return this.body;
	}
	
	public String toString() {
		return new String("ID: " + this.id + " URL: " + this.url + " Type: " + this.request_type + " Body: " + this.body);
	}
}

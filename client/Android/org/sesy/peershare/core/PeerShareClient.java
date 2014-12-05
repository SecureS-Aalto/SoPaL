package org.sesy.peershare.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSession;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sesy.peershare.iface.SocialInfo;
import org.sesy.socialauth.SocialNetworkAccount;
import org.sesy.tetheringapp.Logger;
import org.sesy.tetheringapp.R;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class PeerShareClient extends Service {
	private static final String TAG = "PeerSenseClient";

	//private HTTPRequest HTTPTask;
	private ArrayList<PendingRequest> pending_requests;
	private ArrayList<Long> toupdate;
	private long ps_id;
	private Map<Integer,String> periodic_token;
	
	private static final String PEER_CERTIFICATES = "peer-certificates";

	public class LocalBinder extends Binder {
		PeerShareClient getClient() {
			return PeerShareClient.this;
		}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return (LocalBinder)mBinder;
	}

	private final IBinder mBinder = new LocalBinder();

	@Override
	public void onCreate() {
		super.onCreate();
		pending_requests = new ArrayList<PendingRequest>();
		toupdate = new ArrayList<Long>();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		pending_requests = null;
		toupdate = null;
	}

	private class HTTPRequest extends AsyncTask<String,Void,JSONObject> {

		private long begin_ts;
		
		protected JSONObject doInBackground(String... params) {
			JSONObject recvdjson = new JSONObject();
			try {
				begin_ts = System.currentTimeMillis();
					
				DefaultHttpClient httpClient = new DefaultHttpClient();
				httpClient.addResponseInterceptor(new HttpResponseInterceptor() {
					
					@Override
					public void process(HttpResponse response, HttpContext context)
							throws HttpException, IOException {
				        ManagedClientConnection routedConnection= (ManagedClientConnection)context.getAttribute(ExecutionContext.HTTP_CONNECTION);
				        SSLSession ssl_session = routedConnection.getSSLSession();
				        if(ssl_session != null) {
				            X509Certificate[] certificates= ssl_session.getPeerCertificateChain();
				            if(context.getAttribute(PEER_CERTIFICATES) == null)
				            	// Log.d(TAG,"Adding certificate");
				            	context.setAttribute(PEER_CERTIFICATES, certificates);
				        }
					}
				});
				
				HttpPost httpPostReq = new HttpPost(params[0]);
				httpPostReq.setHeader("Content-Type", "application/x-www-form-urlencoded");
				httpPostReq.setHeader("Charset", "UTF-8");
				httpPostReq.setHeader("Accept", "application/json");
				
				HttpContext context = new BasicHttpContext();
				
				httpPostReq.setEntity(new StringEntity(params[1], "UTF-8"));
				//Log.d(TAG,"HTTPRequest sending request to " + params[0] + " Body: " + params[1]);
				HttpResponse httpResponse = httpClient.execute(httpPostReq,context);
				InputStream is = getAssets().open("se-sy.org.crt");
				try {
					X509Certificate our_server_certificate = X509Certificate.getInstance(is);
					X509Certificate[] peerCertificates = (X509Certificate[])context.getAttribute(PEER_CERTIFICATES);
					if(our_server_certificate.equals(peerCertificates[0])) {
						// Log.d(TAG,"Peer certicate verified successfully.");
					} else {
						//Log.e(TAG, "Wrong certificate received. Terminating connection");
						try {
							recvdjson = new JSONObject();
							recvdjson.put("request-type", params[2]);
							recvdjson.put("error", "Received server certificate not signed by the valid authority");
							return recvdjson;
						} catch(JSONException ex) {
							// Log.e(TAG,"HTTPRequest certificate exception on handling invalid certificate error: " + ex.getMessage());
						}					
					}
				} catch(CertificateException e) {
					//Log.e(TAG, "Certificate exception: " + e.getMessage());
					try {
						recvdjson = new JSONObject();
						recvdjson.put("request-type", params[2]);
						recvdjson.put("error", e.getMessage());
						return recvdjson;
					} catch(JSONException ex) {
						// Log.e(TAG,"HTTPRequest certificate exception on handling certificate error: " + ex.getMessage());
					}
				}
				// Log.d(TAG,"HTTP response status line: " + httpResponse.getStatusLine().toString());
				if(httpResponse.getStatusLine().getStatusCode() == 200) {
					HttpEntity resultEntity = httpResponse.getEntity();
					// Log.d(TAG,"Content length: " + resultEntity.getContentLength());
					InputStream httpstream = resultEntity.getContent();
					String resultstring = convertStreamToString(httpstream);
					httpstream.close();
					Logger.d(TAG, "Received HTTP content: " + resultstring,this);
					recvdjson = new JSONObject(resultstring);
					recvdjson.put("request-type", params[2]);
				} else {
					recvdjson = new JSONObject();
					recvdjson.put("http-error", httpResponse.getStatusLine().getStatusCode());
					recvdjson.put("request-body", params[1]);
					recvdjson.put("request-type", params[2]);
					recvdjson.put("request-url", params[0]);
				}
			} catch(ClientProtocolException e) {
				// Log.e(TAG, "HTTPRequest ClientProtocolException: " + e.getMessage());
				try {
					recvdjson = new JSONObject();
					recvdjson.put("request-type", params[2]);
					recvdjson.put("error", e.getMessage());
				} catch(JSONException ex) {
					// Log.e(TAG,"HTTPRequest JSONException on handling connection error: " + ex.getMessage());
				}
			} catch(IOException e) {
				// Log.e(TAG, "HTTPRequest IOException: " + e.getMessage());
				try {
					recvdjson = new JSONObject();
					recvdjson.put("io-error", e.getMessage());
					recvdjson.put("request-body", params[1]);
					recvdjson.put("request-type", params[2]);
					recvdjson.put("request-url", params[0]);
				} catch(JSONException ex) {
					// Log.e(TAG,"HTTPRequest JSON exception on handling IO exception: " + ex.getMessage());
				}
			} catch (JSONException e) {
				// Log.e(TAG,"HTTPRequest JSONException: " + e.getMessage());
			}

			return recvdjson;
		}

		protected void onPostExecute(JSONObject json) {
			//Log.d(TAG,"onPostExecute; JSON=" + json.toString());
			// Log.i(TAG,"Transaction time: " + ((System.currentTimeMillis() - begin_ts)/1000.0));
			String request_type = new String();
			try {
				if(json.has("http-error")) {
					handleHttpError(json);
				} else if(json.has("io-error")) {
					handleNetworkError(json);
				} else {
					request_type = json.getString("request-type");
					if(request_type.equals("registerRequest"))
						handleRegisterResponse(json);
					else if(request_type.equals("unregisterRequest"))
						handleUnregisterResponse(json);
					else if(request_type.equals("uploadRequest"))
						handleUploadRequest(json);
					else if(request_type.equals("updateRequest"))
						handleUpdateRequest(json,true);
					else if(request_type.equals("downloadRequest") || request_type.equals("periodicDownloadRequest"))
						handleDownloadRequest(json);
					else if((request_type.contains(".pending.")) || (request_type.equals("periodicUpdateRequest")))
						handlePendingRequest(json);
					else if(request_type.contains("periodic"))
						handlePendingRequest(json);
					else if(request_type.equals("deleteRequest"))
						handleDeleteRequest(json);
					else if(request_type.equals("updateRequestNoDownload"))
						handleUpdateRequest(json,false);
				}
			} catch(JSONException e) {
				// Log.e(TAG,"HTTPRequest onPostExecute JSONException: " + e.getMessage());
			}

		}
	}
	
	public void registerUser(SocialNetworkAccount account, long psID) {
		try {
			JSONObject jsonobj = new JSONObject();
			jsonobj.put("sn", account.getSocialNetworkID());
			jsonobj.put("name", account.getUsername());
			jsonobj.put("id", account.getUserID());
			if(psID!=-1)
				jsonobj.put("psID", psID);
			jsonobj.put("token", account.getUserToken());
			new HTTPRequest().execute(getString(R.string.registerURL),jsonobj.toString(),"registerRequest");
		} catch(JSONException e) {
			// Log.e(TAG,"registerUser exception: " + e.getMessage());
		}		
	}

	public void registerUser(List<SocialNetworkAccount> accounts, long psID) {
		// Log.i(TAG,"registerUser for ID: " + id + " name: " + name);
		try {
			JSONObject jsonobj = new JSONObject();
			JSONArray array = new JSONArray();
			JSONArray tokens = new JSONArray();
			for(SocialNetworkAccount account : accounts) {
				JSONObject identity = new JSONObject();
				identity.put("sn", account.getSocialNetworkID());
				identity.put("name", account.getUsername());
				identity.put("id", account.getUserID());
				array.put(identity);
				tokens.put(account.getSocialNetworkID(), account.getUserToken());
			}
			jsonobj.put("identities", array);
			if(psID!=-1)
				jsonobj.put("psID", psID);
			jsonobj.put("tokens", tokens);
			jsonobj.put("version", "2.0");
			new HTTPRequest().execute(getString(R.string.registerURL),jsonobj.toString(),"registerRequest");
		} catch(JSONException e) {
			// Log.e(TAG,"registerUser exception: " + e.getMessage());
		}
	}

	private void handleRegisterResponse(JSONObject json) {
		//Log.i(TAG,"HTTPRequest handleRegisterResponse input: " + json.toString());
		Intent intent = new Intent(getString(R.string.httpclientintent));
		try {
			if(json.has("error")) {
				intent.putExtra("error", json.getString("error"));
			} else {
				String status = json.getString("status");
				intent.putExtra("response-type", "registerResponse");
				intent.putExtra("status", status);
				if(status.equals("OK"))
					intent.putExtra("id", json.getLong("id"));
				else
					intent.putExtra("info", json.getString("info"));
				if(!json.has("version")) {
					intent.putExtra("version", "1.0");
					intent.putExtra("sn", json.getInt("sn"));
				} else {
					JSONArray array = json.getJSONArray("sns");
					List<Integer> sns = new ArrayList<Integer>();
					for(int i=0; i < array.length(); i++)
						sns.add(Integer.valueOf(array.getInt(i)));
					intent.putIntegerArrayListExtra("sns", (ArrayList<Integer>)sns);
					intent.putExtra("version", "2.0");
				}
			}
		} catch(JSONException e) {
			//Log.e(TAG,"HTTPRequest handleRegisterResponse JSONException: " + e.getMessage());
		}
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);		
	}

	public void unregisterUser(long fb_id, long ps_id, String token) {
		// Log.i(TAG,"unregisterUser");
		try {
			JSONObject jsonobj = new JSONObject();
			jsonobj.put("sn", 1);
			jsonobj.put("ps_id", ps_id);
			jsonobj.put("sn_id", fb_id);
			jsonobj.put("token", token);
			new HTTPRequest().execute(getString(R.string.unregisterURL),jsonobj.toString(),"unregisterRequest");
		} catch(JSONException e) {
			// Log.e(TAG,"UnregisterUser exception: " + e.getMessage());
		}		
	}

	private void handleUnregisterResponse(JSONObject json) {
		// Log.i(TAG,"HTTPRequest handleUnregisterResponse input: " + json.toString());
		Intent intent = new Intent(getString(R.string.httpclientintent));
		try {
			if(json.has("error")) {
				intent.putExtra("error", json.getString("error"));
			} else {
				String status = json.getString("status");
				intent.putExtra("response-type", "registerResponse");
				intent.putExtra("status", status);
				if(!status.equals("OK"))
					intent.putExtra("info", json.getString("info"));
			}
		} catch(JSONException e) {
			// Log.e(TAG,"HTTPRequest handleRegisterResponse JSONException: " + e.getMessage());
		}
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);	
	}

	public void uploadSecrets(ArrayList<SecretDetails> secrets, long ps_id, Map<Integer,String> tokens) {
		// Log.i(TAG,"uploadSecrets()");
		try {
			JSONObject jsondata = new JSONObject();
			JSONArray jsonarray = new JSONArray();
			for(SecretDetails secret : secrets) {
				JSONObject json = new JSONObject();
				json.put("type", secret.getSecretType());
				json.put("algorithm",secret.getSecretAlgorithm());
				json.put("value", secret.getSecretValue());
				json.put("sensitivity", secret.getSecretSensitivity());
				json.put("specificity", secret.getSecretSpecificity());
				json.put("platform_id", secret.getPlatformID());
				json.put("platform_app_id", secret.getPlatformAppID());
				json.put("binding_type", secret.getBindingType());
				json.put("share", secret.getSecretPolicy());
				json.put("created", secret.getSecretCreationTS());
				json.put("expires", secret.getSecretExpiryTS());
				//json.put("sn", secret.getSecretSocialNetworkType());
				json.put("sn", -1);
				json.put("description", secret.getSecretText());
				json.put("local_id", secret.getLocalID());
				jsonarray.put(json);
			}
			JSONObject tokenobj = new JSONObject();
			for(Integer i : tokens.keySet())
				tokenobj.put(String.valueOf(i), tokens.get(i));
			jsondata.put("ps_id", ps_id);
			jsondata.put("tokens", tokenobj);
			jsondata.put("operation", "upload");
			jsondata.put("secrets", jsonarray);
			new HTTPRequest().execute(getString(R.string.uploadURL),jsondata.toString(),"uploadRequest");
		} catch(JSONException e) {
			// Log.e(TAG,"uploadSecrets exception: " + e.getMessage());
		}
	}

	private void handleUploadRequest(JSONObject json) {
		// Log.i(TAG,"handleUploadRequest: " + json.toString());
		Intent intent = new Intent(getString(R.string.httpclientintent));
		try {
			if(json.has("error")) {
				intent.putExtra("error", json.getString("error"));
			} else {
				String status = json.getString("status");
				intent.putExtra("response-type", "uploadResponse");
				intent.putExtra("status", status);
				if(status.equals("OK")) {
					JSONArray objects = json.getJSONArray("objects");
					HashMap<Long, Long> object_ids = new HashMap<Long, Long>();
					for(int i=0;i<objects.length();i++) {
						JSONObject obj = objects.optJSONObject(i);
						Long local_id = obj.getLong("local-id");
						Long object_id = obj.getLong("object-id");
						// Log.d(TAG,"Objects array. index=" + i + " local=" + local_id + " object=" + object_id);
						object_ids.put(local_id,object_id);
					}
					intent.putExtra("object-ids",object_ids);
				}
				else
					intent.putExtra("info", json.getString("info"));
			}
		} catch(JSONException e) {
			// Log.e(TAG,"HTTPRequest handleUploadResponse JSONException: " + e.getMessage());
		}
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);	
	}
	
	public void updateSecrets(ArrayList<Long> torefresh, long ps_id, Map<Integer,String> tokens) {
		// Log.d(TAG,"updateSecrets(); torefresh="+torefresh.toString() + " my PSID=" + ps_id);
		updateSecrets(torefresh, ps_id, "updateRequest", tokens);
	}
	
	public void updateSecrets(ArrayList<Long> torefresh, long ps_id, String request_type, Map<Integer,String> tokens) {
		// Log.i(TAG,"updateSecrets() but refreshing them");
		try {
			JSONObject jsondata = new JSONObject();
			JSONArray jsonarray = new JSONArray();
			for(Long object : torefresh) {
				JSONObject json = new JSONObject();
				json.put("object_id", object);
				jsonarray.put(json);
			}
			JSONObject tokenobj = new JSONObject();
			for(Integer i : tokens.keySet())
				tokenobj.put(String.valueOf(i), tokens.get(i));
			jsondata.put("ps_id", ps_id);
			jsondata.put("tokens", tokenobj);
			jsondata.put("operation", "update");
			jsondata.put("secrets", jsonarray);
			new HTTPRequest().execute(getString(R.string.uploadURL),jsondata.toString(),request_type);
		} catch(JSONException e) {
			// Log.e(TAG,"updateSecrets exception: " + e.getMessage());
		}		
	}

	public void updateSecrets(ArrayList<SecretDetails> toupdate, Map<Integer,String> tokens, long ps_id) {
		this.updateSecrets(toupdate, tokens, ps_id, "updateRequest");
	}
	
	public void updateSecret(SecretDetails toupdate, Map<Integer,String> tokens, long ps_id, boolean download) {
		// Log.d(TAG,"updateSecret in http client");
		try {
			JSONObject jsondata = new JSONObject();
			JSONArray jsonarray = new JSONArray();
			
			JSONObject json = new JSONObject();
			json.put("type", toupdate.getSecretType());
			json.put("algorithm",toupdate.getSecretAlgorithm());
			json.put("value", toupdate.getSecretValue());
			json.put("share", toupdate.getSecretPolicy());
			json.put("created", toupdate.getSecretCreationTS());
			json.put("expires", toupdate.getSecretExpiryTS());
			//json.put("sn", toupdate.getSecretSocialNetworkType());
			json.put("sn", -1);
			json.put("description", toupdate.getSecretText());
			json.put("local_id", toupdate.getLocalID());
			json.put("object_id", toupdate.getObjectID());
			jsonarray.put(json);
			
			JSONObject tokenobj = new JSONObject();
			for(Integer i : tokens.keySet())
				tokenobj.put(String.valueOf(i), tokens.get(i));
			
			jsondata.put("ps_id", ps_id);
			jsondata.put("tokens", tokenobj);
			jsondata.put("operation", "update");
			jsondata.put("secrets", jsonarray);
			
			String request_type = null;
			if(download)
				request_type = new String("updateRequest");
			else
				request_type = new String("updateRequestNoDownload");
			
			new HTTPRequest().execute(getString(R.string.uploadURL),jsondata.toString(),request_type);
		} catch(JSONException e) {
			// Log.e(TAG,"uploadSecrets exception: " + e.getMessage());
		}
	}
	
	public void deleteSecrets(ArrayList<Long> objects, Map<Integer,String> tokens, long ps_id) {
		try {
			JSONObject jsondata = new JSONObject();
			JSONArray jsonarray = new JSONArray();
			for(Long obj : objects) {
				jsonarray.put(obj);
			}
			JSONObject tokenobj = new JSONObject();
			for(Integer i : tokens.keySet())
				tokenobj.put(String.valueOf(i), tokens.get(i));
			
			jsondata.put("ps_id", ps_id);
			jsondata.put("tokens", tokenobj);
			jsondata.put("operation", "delete");
			jsondata.put("objects", jsonarray);
			new HTTPRequest().execute(getString(R.string.uploadURL),jsondata.toString(),"deleteRequest");
		} catch(JSONException e) {
			// Log.e(TAG,"uploadSecrets exception: " + e.getMessage());
		}
	}
	
	public void updateSecrets(ArrayList<SecretDetails> toupdate, Map<Integer,String> tokens, long ps_id, String request_type) {
		// Log.i(TAG,"updateSecrets()");
		try {
			JSONObject jsondata = new JSONObject();
			JSONArray jsonarray = new JSONArray();
			for(SecretDetails secret : toupdate) {
				JSONObject json = new JSONObject();
				json.put("object_id", secret.getObjectID());
				json.put("share", secret.getSecretPolicy());
				jsonarray.put(json);
			}
			JSONObject tokenobj = new JSONObject();
			for(Integer i : tokens.keySet())
				tokenobj.put(String.valueOf(i), tokens.get(i));
			jsondata.put("ps_id", ps_id);
			jsondata.put("tokens", tokenobj);
			jsondata.put("operation", "update");
			jsondata.put("secrets", jsonarray);
			new HTTPRequest().execute(getString(R.string.uploadURL),jsondata.toString(),request_type);
		} catch(JSONException e) {
			// Log.e(TAG,"updateSecrets exception: " + e.getMessage());
		}
	}

	private void handleUpdateRequest(JSONObject json, boolean download) {
		// Log.i(TAG,"handleUpdateRequest: " + json.toString());
		Intent intent = new Intent(getString(R.string.httpclientintent));
		try {
			String status = json.getString("status");
			intent.putExtra("response-type", "updateResponse");
			intent.putExtra("status", status);
			if(status.compareTo("OK") != 0) {
				intent.putExtra("error", json.getString("info"));
			} else {
				JSONArray objects = json.getJSONArray("remove");
				ArrayList<Long> secrets = new ArrayList<Long>();
				for(int i=0;i<objects.length();i++) {
					JSONObject obj = objects.getJSONObject(i);
					Long id = Long.valueOf(obj.getLong("object-id"));
					secrets.add(id);
				}
				intent.putExtra("secrets", secrets);
				intent.putExtra("download", download);
			}
		} catch(JSONException e) {
			// Log.e(TAG,"HTTPRequest handleUpdateResponse JSONException: " + e.getMessage());
		}
		// Log.i(TAG,"Sending intent in handleUpdateRequest");
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
	
	private void handleDeleteRequest(JSONObject json) {
		// Log.i(TAG,"handleDeleteRequest: " + json.toString());
		Intent intent = new Intent(getString(R.string.httpclientintent));
		try {
			String status = json.getString("status");
			intent.putExtra("response-type", "deleteResponse");
			intent.putExtra("status", status);
			if(status.compareTo("OK") != 0)
				intent.putExtra("error","Error while deleting objects on the server");
		} catch(JSONException e) {
			// Log.e(TAG,"HTTPRequest handleDeleteRequest JSONException: " + e.getMessage());
		}
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
	
	public void updateSecretsInBackground(ArrayList<PendingRequest> pending, ArrayList<Long> toupdate, Map<Integer,String> tokens, long ps_id) {
		// Log.d(TAG,"Pending requests: " + pending.toString());
		this.pending_requests = pending;
		this.toupdate = toupdate;
		this.ps_id = ps_id;
		this.periodic_token = tokens;
		updateSecretInBackground(0);
	}
	
	public void updateSecretInBackground(int index) {
		if(pending_requests != null) {
			if(index < pending_requests.size()) {
				PendingRequest request = pending_requests.get(index);
				new HTTPRequest().execute(request.getRequestUrl(),request.getRequestBody(),request.getRequestType()+".pending." + String.valueOf(index));
			} else if(index == pending_requests.size()) {
				this.pending_requests = null;
				try {
				updateSecrets(this.toupdate, ps_id, "periodicUpdateRequest", this.periodic_token);
				} catch(ExceptionInInitializerError eiie) {
					// TODO: handle
					//Log.e(TAG, eiie.toString());
				}
			} 
		} else {
			if(periodic_token!=null)
				downloadSecrets(this.ps_id,"periodicDownloadRequest", this.periodic_token);
			this.periodic_token = null;
		}
	}
	
	private void handlePendingRequest(JSONObject json) {
		try {
			String request_type = json.getString("request-type");
			int index = -1;
			if(request_type.contains("pending")) {
				String[] separated_request_type = request_type.split("\\.");
				index = Integer.valueOf(separated_request_type[2]);
				// Log.d(TAG,"Pending request " + index + " completed. Removing it from the list");
				long id = this.pending_requests.get(index).getRequestID();
				this.pending_requests.remove(index);
				Intent intent = new Intent(getString(R.string.httpclientintent));
				intent.putExtra("response-type", "pendingResponse");
				intent.putExtra("index", id);
				LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
			} else if(request_type.equals("periodicUpdateRequest")) {
				handleUpdateRequest(json, false);
				this.pending_requests = null;
			}
			
			if(((pending_requests != null) && (index <= pending_requests.size() + 1)) || (pending_requests == null))
				updateSecretInBackground(index+1);
		} catch(JSONException e) {
			// Log.e(TAG,"JSON exception in handlePendingRequest: " + e.getMessage());
		}
	}
	
	public void downloadSecrets(long PS_id, String request_type, Map<Integer,String> tokens) {
		// Log.i(TAG,"downloadSecrets()");
		try {
			JSONObject jsondata = new JSONObject();
			jsondata.put("ps_id", PS_id);
			jsondata.put("operation", "download");
			JSONObject tokenobj = new JSONObject();
			for(Integer i : tokens.keySet())
				tokenobj.put(String.valueOf(i), tokens.get(i));
			jsondata.put("tokens", tokenobj);
			new HTTPRequest().execute(getString(R.string.downloadURL),jsondata.toString(),request_type);
		} catch(JSONException e) {
			// Log.e(TAG,"updateSecrets exception: " + e.getMessage());
		}		
	}
	
	public void downloadSecrets(long PS_id, Map<Integer,String> tokens) {
		downloadSecrets(PS_id, "downloadRequest",tokens);
	}
	
	private void handleDownloadRequest(JSONObject json) {
		// Log.i(TAG,"handleDownloadRequest: " + json);
		Intent intent = new Intent(getString(R.string.httpclientintent));
		try {
			String status = json.getString("status");
			intent.putExtra("response-type", "downloadResponse");
			intent.putExtra("status", status);
			if(status.compareTo("OK") != 0)
				intent.putExtra("error", "Errors while updating sharing policy on the server");
			else {
				JSONArray objects = json.getJSONArray("objects");
				//Log.d(TAG,"Downloaded objects to store: " + objects.toString());
				HashMap<Long,SecretDetails> secrets = new HashMap<Long,SecretDetails>();
				for(int i=0;i<objects.length();i++) {
					JSONObject obj = objects.getJSONObject(i);
					JSONArray ids = obj.getJSONArray("social-identities");
					List<SocialInfo> social_ids = new ArrayList<SocialInfo>();
					for(int j=0; j<ids.length(); j++) {
						JSONObject o = ids.getJSONObject(j);
						social_ids.add(new SocialInfo(o.getString("social-id"), o.getString("social-name"), o.getInt("network-id")));
					}
					SecretDetails secret = null;
					if(obj.has("object_id")) {
						secret = new SecretDetails(obj.getInt("type"),obj.getString("algorithm"),obj.getString("value"),obj.getString("policy"),
								obj.getString("description"),obj.getInt("binding_type"), obj.getInt("specificity"),obj.getInt("sensitivity"),
								new Timestamp(obj.getLong("created")),new Timestamp(obj.getLong("expires")),obj.getLong("object_id"),
								social_ids,obj.getInt("platform_id"),obj.getString("platform_app_id"));
					} else {
						secret = new SecretDetails(obj.getInt("type"),obj.getString("algorithm"),obj.getString("value"),obj.getString("description"),obj.getInt("binding_type"), obj.getInt("specificity"),
								obj.getInt("sensitivity"),new Timestamp(obj.getLong("created")),new Timestamp(obj.getLong("expires")),SecretDetails.UNKNOWN,
								social_ids,obj.getInt("platform_id"),obj.getString("platform_app_id"));
					}
					secrets.put(Long.valueOf(i),secret);
				}
				intent.putExtra("secrets", secrets);
			}
		} catch(JSONException e) {
			//Log.e(TAG,"HTTPRequest handleDownloadResponse JSONException: " + e.getMessage());
		}
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
	
	private void handleHttpError(JSONObject json) {
		Intent intent = new Intent(getString(R.string.httpclientintent));
		int code = -1;
		try {
			code = json.getInt("http-error");
		} catch(JSONException e) {
			// Log.e(TAG,"handleHttpError JSON exception: " + e.getMessage());
		}
		if(code == 500)
			intent.putExtra("http-error", "Internal PeerSense server error");
		else
			intent.putExtra("http-error", code);
		try {
			intent.putExtra("request-type", json.getString("request-type"));
			intent.putExtra("request-body", json.getString("request-body"));
			intent.putExtra("request-url", json.getString("request-url"));
		} catch(JSONException e) {
			// Log.e(TAG,"handleHttpError JSON exception: " + e.getMessage());
		}
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
	
	private void handleNetworkError(JSONObject json) {
		Intent intent = new Intent(getString(R.string.httpclientintent));
		intent.putExtra("io-error", "Network unavailable");
		try {
			intent.putExtra("request-type", json.getString("request-type"));
			intent.putExtra("request-body", json.getString("request-body"));
			intent.putExtra("request-url", json.getString("request-url"));
		} catch(JSONException e) {
			// Log.e(TAG,"handleHttpError JSON exception: " + e.getMessage());
		}
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private String convertStreamToString(InputStream is) {
		String line = "";
		StringBuilder total = new StringBuilder();
		BufferedReader rd = new BufferedReader(new InputStreamReader(is));
		try {
			while ((line = rd.readLine()) != null) {
				total.append(line);
			}
		} catch (Exception e) {
			//Toast.makeText(this, "Stream Exception", Toast.LENGTH_SHORT).show();
		}
		return total.toString();
	}
}

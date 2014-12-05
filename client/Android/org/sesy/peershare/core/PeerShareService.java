package org.sesy.peershare.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.sesy.peershare.iface.AppData;
import org.sesy.socialauth.SocialAuthManager;
import org.sesy.socialauth.SocialNetworkAccount;
import org.sesy.tetheringapp.Logger;
import org.sesy.tetheringapp.R;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Binder;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class PeerShareService extends Service {
	private static final String TAG = "PeerShareService";	
	
	public static final int P2DRegistrationFailed = -1;
	public static final int P2DRegistrationSuccessful = 0;
	public static final int P2DRegistrationInProgress = -2;
	
	public static final int OPERATION_SUCCESS = 0;
	public static final int OPERATION_ERROR = -1;
	public static final int APP_AUTH_ERROR = -2;
	public static final int SERVICE_UNAVAILABLE_ERROR = -3;
	public static final int PROTOCOL_ERROR = -4;
	
	public static class MobilePlatform {
		public static final int ANDROID = 1;
		public static final int WP = 2;
		public static final int iOS = 3;
	}
	
	private static final long REFRESH_BINDING_PERIOD = (10 * 60 * 1000); // 10 minutes in milliseconds
	private static final long REFRESH_BINDING_INITIAL_DELAY = (10 * 60 * 1000); // 1 minut in milliseconds
	
	private PeerShareClient httpClient = null;
	private DatabaseManager dbmanager = null;
	
	Map<Integer,SocialNetworkAccount> social_accounts;
	
	//private boolean update_needed = false;
		
	private Timer refresh_bindings_timer;
	
	private boolean nativeDeviceSecretsUploaded;
	
	private int P2DRegistrationStatus = P2DRegistrationInProgress;
	private int last_service_status;
	private static int STATUS_READY = 0;
	private static int STATUS_FAILED = 1;
	private static int STATUS_OFFLINE = 2;
	
	public static final String PEERSHARE_INTENT = "org.sesy.peershare";
	
	
	private ConnectivityReceiver connection_state;
	private NetworkInfo.State network_status;
	//private ScreenStateReceiver screen_state;
	private static PeerShareClientResponse peershareClientResponse;
	
	/*private boolean uploadNeeded = false;
	private boolean updateNeeded = false;
	private boolean downloadNeeded = false;
	private boolean deleteNeeded = false;*/
	/*private List<Long> old_local_object_ID_to_update = new ArrayList<Long>();
	private List<Long> old_local_object_ID_to_delete = new ArrayList<Long>();*/
	
    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new PeerShareBinder();
    
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class PeerShareBinder extends Binder {
        public PeerShareService getService() {
            return PeerShareService.this;
        }
    }
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		//new LogCat().start();
		
		//Log.w(TAG, "onCreate()");
			
		Intent in = new Intent(this, PeerShareClient.class);
		boolean ret = bindService(in, psConnection, Context.BIND_AUTO_CREATE);
		//Log.w(TAG,"bindService ret="+ret);
		
		this.nativeDeviceSecretsUploaded = false;
		this.P2DRegistrationStatus = P2DRegistrationInProgress;
		this.last_service_status = PeerShareService.STATUS_FAILED;
		
		peershareClientResponse = new PeerShareClientResponse();
		LocalBroadcastManager.getInstance(this).registerReceiver(peershareClientResponse, new IntentFilter(getString(R.string.httpclientintent)));
				
		
		connection_state = new ConnectivityReceiver(this);
		IntentFilter conn_filter = new IntentFilter();
		conn_filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		registerReceiver(connection_state, conn_filter);
		
		this.social_accounts = new HashMap<Integer,SocialNetworkAccount>();
		
		if(this.dbmanager == null)
			this.dbmanager = new DatabaseManager(this);

		this.refresh_bindings_timer = new Timer();
		this.scheduleRefreshBindingsTask();
		// Log.d(TAG,"onCreate() end");
	}

	@Override
	public IBinder onBind(Intent intent) {
		// Log.d(TAG,"onBind() called; ");
			return mBinder;
	}
	
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}
		
	public boolean onUnbind(Intent intent) {
		//Log.w(TAG,"onUnbind");
		return true;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Logger.w(TAG, "onDestroy()",this);
		unbindService(psConnection);
		try {
			/*if(screen_state!=null)
				unregisterReceiver(screen_state);*/
			if(connection_state!=null)
				unregisterReceiver(connection_state);
			//LocalBroadcastManager mgr = LocalBroadcastManager.getInstance(this);
		} catch(IllegalArgumentException e) {
			// Log.e(TAG,"Illegal argument exception: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	
	/** Defines callbacks for service binding, passed to bindService() */
	private ServiceConnection psConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className,
				IBinder service) {
			Logger.e(TAG, "IBinder: " + service,this);
			//Log.d(TAG, "IBinder: " + service);
			// We've bound to LocalService, cast the IBinder and get LocalService instance
			//LocalBinder binder = ((PeerShareClient.LocalBinder)service).getClient();
			//LocalBinder binder = (PeerShareClient.LocalBinder)service;
			httpClient = ((PeerShareClient.LocalBinder)service).getClient();//binder.getClient();
			checkServiceStatus();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			//Log.w(TAG,"HTTP client disconnected");
			httpClient = null;
		}
	};
	
	public void setSocialNetworkData(SocialNetworkAccount snAccount) {
		registerSocialUser(snAccount);
	}
	
	public void setSocialNetworkData(List<SocialNetworkAccount> snAccounts) {
		registerSocialUser(snAccounts);
	}
	
	public void removeSocialNetworkData(int sn) {
		// remove from registered users in DB
		// remove data that are obtained via this social network
		if(dbmanager.unregisterUserAccount(sn)) {
			//Log.d(TAG,"Social data successfully removed from local database");
			this.social_accounts.remove(Integer.valueOf(sn));
		}
		if(this.social_accounts.size() > 0) {
			downloadData();
		}
	}
	
	private void scheduleRefreshBindingsTask() {
		Logger.d(TAG, "scheduling RefreshBindingsTask",this);
		//long delay = dbmanager.getNextScheduledRefresh();
		//if(delay==-1)
		long delay = PeerShareService.REFRESH_BINDING_INITIAL_DELAY;
		
		//if(delay < PeerShareService.REFRESH_BINDING_INITIAL_DELAY) // needed to avoid race condition if Facebook is not registered
			//delay = PeerShareService.REFRESH_BINDING_INITIAL_DELAY;
		
		// Log.d(TAG,"schedulingRefreshBindingTask with delay: " + delay + " and period: " + PeerShareService.REFRESH_BINDING_PERIOD);
		this.refresh_bindings_timer.schedule(new TimerTask() {
			
			@Override
			public void run() {
					syncBindingsWithServer();
			}
		},delay,PeerShareService.REFRESH_BINDING_PERIOD);
		//Log.w(TAG, "scheduleTimerForServiceResponse end");
	}
	
	private void syncBindingsWithServer() {
		Logger.d(TAG,"syncBindingsWithServer",this);
		KeyguardManager kgMgr = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
		boolean showing = kgMgr.inKeyguardRestrictedInputMode();
		if(!showing && network_status == State.CONNECTED) {
			Map<Integer,String> tokens = getUserTokens();
			if(tokens!=null && tokens.size() > 0) {
				ArrayList<PendingRequest> pending = dbmanager.getPendingActions();
				ArrayList<Long> toupdate = dbmanager.getSecretsForRefresh();
				long ps_id = dbmanager.getUserID();
				httpClient.updateSecretsInBackground(pending,toupdate,tokens,ps_id);
			}
		} /*else {
			// Log.d(TAG,"Setting sync needed flag");
			//screen_state.setSyncingNeededFlag(true);
		}*/
	}
	
	/*public void accessTokenUpdated(String token) {
		// Log.d(TAG,"accessTokenUpdated() upload: " + uploadNeeded + " update: " + updateNeeded + " delete: " + deleteNeeded + " download: " + downloadNeeded);
		if(uploadNeeded) {
			uploadNeeded = false;
			// Log.d(TAG,"Uploading data enqueued to upload");
			uploadSecrets(token);
		}
		if(updateNeeded && !old_local_object_ID_to_update.isEmpty()) {
			updateNeeded = false;
			// Log.d(TAG,"Updating data enqueued to update");
			for(Long id : old_local_object_ID_to_update)
				updateSecrets(token, id,false);
			old_local_object_ID_to_update.clear();
		}
		if(deleteNeeded && !old_local_object_ID_to_delete.isEmpty()) {
			deleteNeeded = false;
			// Log.d(TAG,"Deleting data enqueued to delete");
			for(Long id : old_local_object_ID_to_delete)
				removeSecrets(token, id);
			old_local_object_ID_to_delete.clear();			
		}
		if(downloadNeeded) {
			downloadNeeded = false;
			long ps_id = dbmanager.getUserID();
			httpClient.downloadSecrets(ps_id, token);
		}
		//if(screen_state.isUpdateNeeded()) {
		if(update_needed) {
			syncBindingsWithServer();
		}
	}*/
	
	public long getUserId(){
		return dbmanager.getUserID();
	}
	
	private class PeerShareClientResponse extends BroadcastReceiver {
	    
		@Override
		@SuppressWarnings("unchecked")
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction() != null && intent.getAction().equals(getString(R.string.httpclientintent))) {
				String error = new String();

				//Log.w(TAG,"Broadcast receiver. Intent has special error: " + intent.hasExtra("http-error") + " " + intent.hasExtra("io-error"));

				if(intent.hasExtra("http-error")) {
					Logger.e(TAG, "Response from the http client; HTTP-ERROR: " + intent.getIntExtra("http-error",404),this);
					if(intent.hasExtra("request-type") && intent.getStringExtra("request-type").contains("periodic") == false && dbmanager.storePendingAction(intent.getStringExtra("request-type"),intent.getStringExtra("request-url"),intent.getStringExtra("request-body")))
						Logger.e(TAG, intent.getIntExtra("http-error",404) + ". Request is stored and will be processed later",this);
					else
						Logger.e(TAG, String.valueOf(intent.getIntExtra("http-error",404)),this);
					return;
				} else if(intent.hasExtra("io-error")) {
					Logger.d(TAG,"Response from the http client; I/O-ERROR: " + intent.getStringExtra("io-error"),this);
					if(intent.hasExtra("request-type") && intent.getStringExtra("request-type").contains("periodic") == false && dbmanager.storePendingAction(intent.getStringExtra("request-type"),intent.getStringExtra("request-url"),intent.getStringExtra("request-body")))
						Logger.e(TAG,intent.getStringExtra("io-error") + ". Request is stored and will be processed later",this);
					else
						Logger.e(TAG,intent.getStringExtra("io-error"),this);
					if(PeerShareService.this.P2DRegistrationStatus == PeerShareService.P2DRegistrationInProgress)
						P2DRegistrationStatus = P2DRegistrationFailed;
					else if(P2DRegistrationStatus == PeerShareService.P2DRegistrationSuccessful) {
						Intent dw_intent = new Intent();
						dw_intent.setAction(PeerShareService.PEERSHARE_INTENT);
						dw_intent.putExtra("status", "ready");
						dw_intent.putExtra("download", "completed");
						//Log.w(TAG,"Sending service download complete intent, as we're offline");
						sendBroadcast(dw_intent);
					}
					return;				
				} else if(intent.hasExtra("error")) {
					Logger.d(TAG,"Response from the http client; Other ERROR: " + intent.getStringExtra("error"),this);
					if(intent.hasExtra("request-type") && intent.getStringExtra("request-type").contains("periodic") == false && dbmanager.storePendingAction(intent.getStringExtra("request-type"),intent.getStringExtra("request-url"),intent.getStringExtra("request-body")))
						Logger.e(TAG,intent.getStringExtra("error") + ". Request is stored and will be processed later",this);
					else
						Logger.e(TAG,intent.getStringExtra("error"),this);
					P2DRegistrationStatus = P2DRegistrationFailed;
					return;						
				}

				String status = intent.getStringExtra("status");
				String response_type = intent.getStringExtra("response-type");

				//Log.w(TAG,"Response from the http client; type=" + response_type + " status=" + status + " error: " + error);
				
				if(response_type.equals("registerResponse")) {
					if(status.equals("OK")) {
						boolean dbRegistrationResult = false;
						long id=intent.getLongExtra("id", -1);
						if(intent.getStringExtra("version").equals("1.0")) {
							int sn=intent.getIntExtra("sn", SocialAuthManager.FACEBOOK);
							SocialNetworkAccount account = social_accounts.get(sn);
							//Log.d(TAG,"Getting account: " + account + " size: " + social_accounts.size());
							String uid = account.getUserID();
							int SNid = account.getSocialNetworkID();
							String uname = account.getUsername();
							dbRegistrationResult = dbmanager.registerUser(id,uid,SNid,uname);
						} else if(intent.getStringExtra("version").equals("2.0")) {
							List<Integer> sns = (ArrayList<Integer>)intent.getIntegerArrayListExtra("sns");
							for(Integer sn : sns) {
								SocialNetworkAccount account = social_accounts.get(sn);
								//Log.d(TAG,"Getting account: " + account.toString() + " size: " + social_accounts.size());
								String uid = account.getUserID();
								int SNid = account.getSocialNetworkID();
								String uname = account.getUsername();
								dbRegistrationResult = dbmanager.registerUser(id,uid,SNid,uname);
								if(!dbRegistrationResult)
									break;
							}
						}
						if(!dbRegistrationResult) {
							Logger.e(TAG,"User registration for ID= " + id + " failed",this);
							Logger.e(TAG,"Error while registering user in the local database",this);
							P2DRegistrationStatus = P2DRegistrationFailed;
						} else {
							//Log.i(TAG, "User registration succeeded"); // Social network ID=" + dbmanager.getRegisteredSocialNetworkAccounts() + " PS ID=" + dbmanager.getUserID());
							/*Map<Integer,SocialNetworkAccount> accounts = dbmanager.getRegisteredSocialNetworkAccounts();
							for(Integer i : accounts.keySet()) {
								SocialNetworkAccount a = accounts.get(i);
								Log.d(TAG,"Active account: " + a.getSocialNetworkID() + " " + a.getUserID());
							}*/
							Map<Integer,String> tokens = getUserTokens();
							if(tokens.size() > 0)
								httpClient.downloadSecrets(dbmanager.getUserID(), tokens);
							
							
							//Log.i(TAG,"Sending registration complete notificaiton");
							P2DRegistrationStatus = P2DRegistrationSuccessful;	
						}
					} else {
						error = intent.getStringExtra("info");
						Logger.e(TAG,"User registration failed",this);
						Logger.e(TAG,"Error while registering user on the server: " + error,this);
						P2DRegistrationStatus = P2DRegistrationFailed;
					}
					checkServiceStatus();
				} else if(response_type.equals("unregisterResponse")) {
					if(!dbmanager.unregisterUser()) {
						Logger.e(TAG,"User unregistration failed",this); 
					}
					else {
						Logger.i(TAG,"User unregistration succeeded",this);
					}
				} else if(response_type.equals("uploadResponse")) {
					if(status.equals("OK")) {
                        //Log.i(TAG, "Data successfully uploaded to the server. Starting downloading");
                        HashMap<Long,Long> objects = (HashMap<Long,Long>)intent.getExtras().get("object-ids");
                        dbmanager.setObjectIDs(objects);
						Map<Integer,String> tokens = new HashMap<Integer, String>();
						for(Integer sn : PeerShareService.this.social_accounts.keySet()) {
							tokens.put(sn, PeerShareService.this.social_accounts.get(sn).getUserToken());
						}
						if(tokens.size() > 0) {
							long ps_id = dbmanager.getUserID();
							httpClient.downloadSecrets(ps_id,tokens);
						}
					} else {
						error = intent.getStringExtra("info");
						Logger.e(TAG,"Error while uploading secrets to the server: " + error,this);
					}
				} else if(response_type.equals("updateResponse")) {
					if(status.equals("OK")) {
						//Log.i(TAG, "Data successfully updated to the server");
						ArrayList<Long> updated_secrets = (ArrayList<Long>)intent.getSerializableExtra("secrets");
						if(updated_secrets != null && updated_secrets.size() > 0) {
							boolean delete_rsp = dbmanager.removeDeletedSecrets(updated_secrets);
							//Log.d(TAG,"removeDeletedSecrets returns: " + delete_rsp);
						}
						if(intent.getBooleanExtra("download", false) == true) {
							Map<Integer,String> tokens = new HashMap<Integer, String>();
							for(Integer sn : PeerShareService.this.social_accounts.keySet()) {
								tokens.put(sn, PeerShareService.this.social_accounts.get(sn).getUserToken());
							}
							if(tokens.size() > 0) {
								long ps_id = dbmanager.getUserID();
								Logger.d(TAG,"Starting downloading following the update",this);
								httpClient.downloadSecrets(ps_id,tokens);
							}
						}
					} else {
						if(intent.getStringExtra("error").equals("Invalid user access token")) {
							// Log.d(TAG,"Wrong token: " + intent.getStringExtra("error"));
						}
						Logger.e(TAG,"Sharing policies update failed. Due to server problems",this);
					}
				} else if(response_type.equals("downloadResponse")) {
					//Log.d(TAG,"Download operation successful");
					P2DRegistrationStatus = P2DRegistrationSuccessful;
					//checkServiceStatus();
					if(status.equals("OK")) {
						Logger.i(TAG, "Download operation successful",this);
						HashMap<Long, SecretDetails> secrets = (HashMap<Long, SecretDetails>)intent.getExtras().get("secrets");
						//Log.d(TAG,"Casted downloaded data: " + secrets);
						if(dbmanager.updateSecretsFromServer(secrets) == true) {
							//Log.i(TAG, "Downloaded data successfully stored locally");
							Intent dw_intent = new Intent();
							dw_intent.setAction(PeerShareService.PEERSHARE_INTENT);
							dw_intent.putExtra("status", "ready");
							dw_intent.putExtra("download", "completed");
							Log.w(TAG,"Sending service download complete intent");
							sendBroadcast(dw_intent);
						}
						else {
							//Log.e(TAG,"Secret's download failed. Due to local database problems");
						}
					} else {
						//Log.e(TAG,"Secret's download failed. Due to server problems");
					}
				} else if(response_type.equals("deleteResponse")) {
					if(status.equals("OK")) {
						Logger.i(TAG, "Data successfully deleted on the server",this);
					}
					else {
						Logger.e(TAG,"Secret's deletion failed. Due to server problems",this);
					}
				} else if(response_type.equals("pendingResponse")) {
					long id = intent.getLongExtra("index", 0);
					Logger.d(TAG,"Deleting pending response with ID: " + id,this);
					dbmanager.deletePendingRequest(id);
				}
			}
			
		}
	};
	
		
		public long removeData(long local_objectID) throws RemoteException {
			if(getServiceStatus() != 0) {
				Logger.d(TAG,"Service not ready. Not removing data",this);
				return SERVICE_UNAVAILABLE_ERROR;
			}
			int uid = Binder.getCallingUid();
			Logger.d(TAG,"removing secret with local object ID: " + local_objectID,this);
			long globalID = dbmanager.removeMySecret(local_objectID,uid);
			if(globalID > 0) {
				removeSecrets(local_objectID);
			}
			Logger.d(TAG, "removeSecret returns: " + globalID,this);
			return globalID;
		}
		
		public int updateData(long local_objectID, AppData data) throws RemoteException {
			if(getServiceStatus() != 0) {
				Logger.d(TAG,"Service not ready. Not updating data",this);
				return SERVICE_UNAVAILABLE_ERROR;
			}
			if(data==null) {
				return PROTOCOL_ERROR;
			}
			int uid = Binder.getCallingUid();
			Logger.d(TAG,"updateSecret for object with local id=" + local_objectID,this);
			int ret = dbmanager.updateMySecret(local_objectID,data, uid);
			// Log.d(TAG,"updateMySecret returns: " + ret);
			if(ret >= 0) {
				updateSecrets(local_objectID,false);
			} /*else if(ret >= 0) {
				old_local_object_ID_to_update.add(local_objectID);
				updateNeeded = true;
			}*/
			Logger.d(TAG,"updateSecret returns: " + ret,this);
			return ret;
		}
		
		public int updateDataForceDownload(long local_objectID, AppData data) throws RemoteException {
			if(getServiceStatus() != 0) {
				Logger.d(TAG,"Service not ready. Not updating data",this);
				return SERVICE_UNAVAILABLE_ERROR;
			}
			if(data==null) {
				return PROTOCOL_ERROR;
			}
			int uid = Binder.getCallingUid();
			Logger.d(TAG,"updateSecret for object with local id=" + local_objectID,this);
			int ret = dbmanager.updateMySecret(local_objectID,data, uid);
			Logger.d(TAG,"updateMySecret returns: " + ret,this);
			if(ret >= 0) {
				updateSecrets(local_objectID,true);
			} /*else if(ret >= 0) {
				old_local_object_ID_to_update.add(local_objectID);
				updateNeeded = true;
				downloadNeeded = true;
			}*/
			Logger.d(TAG,"updateSecret returns: " + ret,this);
			return ret;
		}
		
		// This method returns local ID of the created object
		public long addData(AppData data) {
			// Log.d(TAG,"addNewSecret in PeerShare API");
			if(getServiceStatus() != 0) {
				Logger.d(TAG,"Service not ready. Not adding new data",this);
				return SERVICE_UNAVAILABLE_ERROR;
			}
			if(data==null) {
				return PROTOCOL_ERROR;
			}
			int uid = Binder.getCallingUid();
			// data owner ID is hard-coded to -1
			long local_object_id = dbmanager.addMySecret(data.getDataType(), data.getDataAlgorithm(), data.getDataValue(), 
					data.getDataDescription(), data.getSharingPolicy(), data.getDataSpecificity(), data.getDataSensitivity(), 
					data.getDataBindingType(), -1, true,data.getExpiryTimestamp(),data.getCreationTimestamp(),uid);
			if(local_object_id > 0) {
				// Log.d(TAG,"Object stored locally. Sending request to server");
				int res = uploadSecrets();
				Logger.d(TAG,"Uploading " + res + " new secrets",this);
			} else {
				Logger.e(TAG,"Error while storing the new secret locally. Error: " + local_object_id,this);
			}
			Logger.d(TAG,"addNewSecret returns: new local object id=" + local_object_id,this);
			return local_object_id;
		}
		
		public List<org.sesy.peershare.iface.Long> getMyData(int type) {
			int uid = Binder.getCallingUid();
			Logger.d(TAG,"getMySecrets() called with type=" + type,this);
			ArrayList<Long> secrets = dbmanager.getMyTypeSecretIDsForApp(type,uid);
			ArrayList<org.sesy.peershare.iface.Long> res = new ArrayList<org.sesy.peershare.iface.Long>();
			// Log.d(TAG,"List of registered my secrets of type: " + type);
			for(Long secret: secrets) {
				org.sesy.peershare.iface.Long tmp = new org.sesy.peershare.iface.Long(secret.longValue());
				// Log.d(TAG,"Local object ID: " + tmp.getValue());
				res.add(tmp);
			}
			Logger.d(TAG, "getMySecrets returns: " + res,this);
			return (List<org.sesy.peershare.iface.Long>)res;
		}
		
		public List<org.sesy.peershare.iface.Long> getSharedData(int type) {
			int uid = Binder.getCallingUid();
			Logger.d(TAG, "getSharedSecrets called with type=" + type,this);
			ArrayList<Long> secrets = dbmanager.getSharedTypeSecretIDsForApp(type,uid);
			ArrayList<org.sesy.peershare.iface.Long> res = new ArrayList<org.sesy.peershare.iface.Long>();
			Logger.d(TAG,"List of registered shared secrets of type: " + type,this);
			for(Long secret: secrets) {
				org.sesy.peershare.iface.Long tmp = new org.sesy.peershare.iface.Long(secret.longValue());
				Logger.d(TAG,"Local object ID: " + tmp.getValue(),this);
				res.add(tmp);
			}
			Logger.d(TAG,"getSharedSecrets returns: " + res,this);
			return (List<org.sesy.peershare.iface.Long>)res;
		}
		
		public AppData getMyDataDetail(long local_objectID) {
			int uid = Binder.getCallingUid();
			Logger.d(TAG,"getMySecretData for object with local id=" + local_objectID,this);
			//Log.d(TAG,"getMySecretData for object with local id=" + local_objectID);
			return dbmanager.getMySecretDataForApp(local_objectID,uid);
		}
		
		public AppData getSharedDataDetail(long local_objectID) {
			int uid = Binder.getCallingUid();
			Logger.d(TAG,"getSharedSecretData for object with local id=" + local_objectID,this);
			return dbmanager.getSharedSecretDataForApp(local_objectID,uid);
		}
		
		public List<AppData> getSharedDataDetails(int type) {
			int uid = Binder.getCallingUid();			
			Logger.d(TAG,"getSharedSecretData for objects of type=" + type,this);
			return dbmanager.getSharedSecretsDataForApp(type,uid);
		}
		
		public int deleteMyData(int type) {
			if(getServiceStatus() != 0) {
				Logger.d(TAG,"Service not ready. Not deleting my data",this);
				return SERVICE_UNAVAILABLE_ERROR;
			}
			int uid = Binder.getCallingUid();			
			Logger.d(TAG,"deleteMySecrets type=" + type,this);
			ArrayList<Long> todelete = dbmanager.deleteMySecrets(type,uid);
			if(todelete.size() > 0) {
				Map<Integer,String> tokens = getUserTokens();
				if(tokens.size() > 0) {
					long PSid = dbmanager.getUserID();
					httpClient.deleteSecrets(todelete, tokens, PSid);
				}
				
			}
			Logger.d(TAG,"deleteMySecrets returns: " + todelete.size(),this);
			return todelete.size();
		}
				
		public int getServiceStatus() {
			return getStatusInternal();
		}
		
		public Map<Integer,SocialNetworkAccount> getMySocialData() {
			// Log.d(TAG,"getMySocialData: name=" + FBname + " id=" + FBid);
			//dbmanager.getRegisteredSocialNetworkAccounts();
			//Log.d(TAG,"getMySocialData()");
			//for(Integer i : social_accounts.keySet())
				//Log.d(TAG,"Account: " + social_accounts.get(i).getSocialNetworkID() + " " + social_accounts.get(i).getUserID());
			if(getServiceStatus() !=0)
				return null;
			return social_accounts;
		}
			
	private int getStatusInternal() {
		int ret = 0;
		// Log.d(TAG, " P2DRegistrationStatus: " + P2DRegistrationStatus);
		if(P2DRegistrationStatus==P2DRegistrationSuccessful)
			ret = P2DRegistrationSuccessful;
		else if(P2DRegistrationStatus == P2DRegistrationFailed)
			ret = P2DRegistrationFailed;
		else
			ret = P2DRegistrationInProgress;
		// Log.d(TAG,"getServiceStatus() returning: " + ret);
		// Log.d(TAG,"FBid=" + FBid + " name: " + FBname + " snID: " + SNid);
		return ret;
	}
	
	private void checkServiceStatus() {
		//Log.d(TAG,"checkServiceStatus");
		if((this.P2DRegistrationStatus == PeerShareService.P2DRegistrationSuccessful)
			 && (this.last_service_status != PeerShareService.STATUS_READY) && (network_status!=State.DISCONNECTED)) {
			Intent intent = new Intent();
			intent.setAction(PeerShareService.PEERSHARE_INTENT);
			intent.putExtra("status", "ready");
			//Log.w(TAG,"Sending service ready intent");
			sendBroadcast(intent);
			this.last_service_status = PeerShareService.STATUS_READY;
		} else if(((P2DRegistrationStatus == P2DRegistrationFailed)) 
				&& (this.last_service_status != PeerShareService.STATUS_OFFLINE)) {
			Intent intent = new Intent();
			intent.setAction(PeerShareService.PEERSHARE_INTENT);
			intent.putExtra("status", "failed");
			//Log.w(TAG,"Sending service failed intent");
			sendBroadcast(intent);
			this.last_service_status = PeerShareService.STATUS_FAILED;
		} else if((P2DRegistrationStatus == PeerShareService.P2DRegistrationSuccessful)
				&& network_status==State.DISCONNECTED && (this.last_service_status != PeerShareService.STATUS_OFFLINE)) {
			Intent intent = new Intent();
			intent.setAction(PeerShareService.PEERSHARE_INTENT);
			intent.putExtra("status", "offline");
			//Log.w(TAG,"Sending service offline intent");
			sendBroadcast(intent);			
			this.last_service_status = PeerShareService.STATUS_OFFLINE;
		} else {
			Intent intent = new Intent();
			intent.setAction(PeerShareService.PEERSHARE_INTENT);
			intent.putExtra("status", "clientLaunched");
			//Log.w(TAG,"Sending service client launched intent");
			LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);			
		}
	}
	
	public void registerSocialUser(List<SocialNetworkAccount> accounts) {
		//Log.d(TAG,"generic registerSocialUser");
		/*for(SocialNetworkAccount a : accounts)
			Log.d(TAG,"Account: " + a.toString());*/
		Map<Integer,SocialNetworkAccount> registered = dbmanager.getRegisteredSocialNetworkAccounts();
		boolean addedAccount = false;
		for(SocialNetworkAccount a : accounts) {
			if(!social_accounts.containsKey(a.getSocialNetworkID())) {
				social_accounts.put(a.getSocialNetworkID(), a);
				addedAccount = true;
			}
		}
		if(!addedAccount) {
			//Log.d(TAG,"Ignoring next notification that auth for SN is completed");
			return;			
		}
		
		//Log.d(TAG,"Registered accounts:");
		Collection<SocialNetworkAccount> b = registered.values();
		/*for(SocialNetworkAccount i : b) {
			Log.d(TAG,"Account: " + i.toString());
		}*/
		
		boolean remoteRegistrationNeeded = false;
		for(SocialNetworkAccount a : accounts) {
			if(!registered.containsKey(a.getSocialNetworkID())) {
				remoteRegistrationNeeded = true;
				break;
			} else {
				SocialNetworkAccount reg = registered.get(a.getSocialNetworkID());
				if(!reg.getUserID().equals(a.getUserID())) {
					remoteRegistrationNeeded = true;
					break;
				}
			}
		}
		
		if(remoteRegistrationNeeded) {
			//Log.d(TAG,"registering user account");
			long psID = dbmanager.getUserID();
			httpClient.registerUser(accounts,psID);	
		} else {
			completeRegistrationLocally();
		}
	}
	
	public void registerSocialUser(SocialNetworkAccount account) {
		Logger.d(TAG,"registerSocialUser: " + String.valueOf(account.getUserID()) + " name: " + account.getUsername(),this);
		//Log.d(TAG,"registerSocialUser: " + String.valueOf(account.getUserID()) + " name: " + account.getUsername());
		
		Map<Integer,SocialNetworkAccount> registered = dbmanager.getRegisteredSocialNetworkAccounts();
		if(!social_accounts.containsKey(account.getSocialNetworkID())) {
			//Log.d(TAG,"Adding social network: " + account.getSocialNetworkID() + " to the list of social accounts");
			social_accounts.put(account.getSocialNetworkID(), account);
		}
		else {
			//Log.d(TAG,"Ignoring next notification that auth for SN " + account.getSocialNetworkID() + " is completed");
			return;
		}
		SocialNetworkAccount registered_account = registered.get(account.getSocialNetworkID());
		if(registered_account==null || !registered_account.getUserID().equals(account.getUserID())) {
			//Log.d(TAG,"registering user account");
			long psID = dbmanager.getUserID();
			httpClient.registerUser(account,psID);			
		} else {
			completeRegistrationLocally();
		}
	}
	
	private void completeRegistrationLocally() {
		Logger.i(TAG,"Correct user already registered. Sending registration complete notification",this);
		//Log.i(TAG,"Correct user already registered. Sending registration complete notification");
		P2DRegistrationStatus = PeerShareService.P2DRegistrationSuccessful;
		checkServiceStatus();
		if(connection_state.isConnected() ) {/*&& !nativeDeviceSecretsUploaded) {
			if(uploadSecrets()==0)
				nativeDeviceSecretsUploaded=true;*/
			Map<Integer,String> tokens = getUserTokens();
			if(tokens.size() > 0)
				httpClient.downloadSecrets(dbmanager.getUserID(), tokens);
		} else {
			Intent dw_intent = new Intent();
			dw_intent.setAction(PeerShareService.PEERSHARE_INTENT);
			dw_intent.putExtra("status", "ready");
			dw_intent.putExtra("download", "completed");
			//Log.w(TAG,"Sending service download complete intent, as we're offline");
			sendBroadcast(dw_intent);
		}		
	}
	
	public int downloadData() {
		//Log.d(TAG,"downloadData");
		long PSid = dbmanager.getUserID();	
		Map<Integer,String> tokens = getUserTokens();
		if(tokens.size() > 0)
			httpClient.downloadSecrets(PSid, tokens);
		return 0;
	}
	
	public int uploadSecrets() {
		//Log.d(TAG,"uploadSecrets");
		long PSid = dbmanager.getUserID();
		ArrayList<SecretDetails> toupload = dbmanager.getUploadableSecrets();
		Map<Integer,String> tokens = getUserTokens();
		if(toupload.size() > 0 && tokens.size() > 0) {
			//Log.d(TAG,"Uploading new secrets");
			httpClient.uploadSecrets(toupload,PSid,tokens);
		} else if(tokens.size() > 0) {
			//Log.i(TAG,"uploadMySecrets: No secrets to upload. Refreshing current ones");
			ArrayList<Long> torefresh = dbmanager.getSecretsForRefresh();
			//Log.d(TAG,"Amount to secrets to refresh: " + torefresh.size());
			if(torefresh.size() > 0)
				httpClient.updateSecrets(torefresh,PSid,tokens);
			else
				httpClient.downloadSecrets(PSid, tokens);
		}
		return toupload.size();
	}
	
	public int updateSecrets(long local_objectID, boolean download) {
		// Log.d(TAG,"updateSecrets is called");
		SecretDetails secret = dbmanager.getMySecret(local_objectID);
		Map<Integer,String> tokens = getUserTokens();
		if(tokens.size() > 0) {
			long PSid = dbmanager.getUserID();
			httpClient.updateSecret(secret,tokens,PSid,download);
			return 0;
		} else 
			return -1;
	}
	
	public int removeSecrets(long local_objectID) {
		ArrayList<Long> todelete = new ArrayList<Long>();
		todelete.add(Long.valueOf(local_objectID));
		long PSid = dbmanager.getUserID();
		Map<Integer,String> tokens = getUserTokens();
		if(tokens.size() > 0) {
			httpClient.deleteSecrets(todelete, tokens, PSid);
			return 0;
		} else
			return -1;
	}
	
	private Map<Integer,String> getUserTokens() {
		//Log.d(TAG,"getUserTokens() number of accounts: " + this.social_accounts.size());
		Map<Integer,String> tokens = new HashMap<Integer, String>();
		for(Integer sn : this.social_accounts.keySet()) {
			tokens.put(sn, this.social_accounts.get(sn).getUserToken());
			//Log.d(TAG,"Adding token: " + this.social_accounts.get(sn).getUserToken() + " for: " + sn);
		}
		return tokens;
	}
	
	private class ConnectivityReceiver extends BroadcastReceiver {
		
		private PeerShareService ps;
		private ConnectivityManager cm;
		
		public ConnectivityReceiver(PeerShareService s) {
			this.ps = s;
			this.cm = (ConnectivityManager)PeerShareService.this.getSystemService(Context.CONNECTIVITY_SERVICE);
		}
		
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle extras = intent.getExtras();
			NetworkInfo info = (NetworkInfo)extras.getParcelable("networkInfo");
			// Log.d(TAG,"Network info: " + info);
			if(info!=null) {
				network_status = info.getState();
				if((network_status==State.CONNECTED)) {
				} else if(network_status==State.DISCONNECTED)
					checkServiceStatus();
			}
		}
		
		public boolean isConnected() {
			boolean ret = false;
			NetworkInfo info = cm.getActiveNetworkInfo();
			if(info!=null && info.isConnected()) {
				PeerShareService.this.network_status = info.getState();
				ret = true;
			} else {
				PeerShareService.this.network_status = NetworkInfo.State.DISCONNECTED;
			}
			return ret;
		}
	}
	
	private class LogCat extends Thread {
		
		private PrintWriter writer;
		
		public LogCat() {
			File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"peershare");
			if (!file.exists()) {
				// Log.d(TAG,"Creating directory for the new logcat file");
				file.mkdirs();
			}
			// Log.d(TAG, "Logcat file location:" + file.getAbsolutePath());
			
			try {
				File logfile = new File(file.getAbsolutePath() + "/peershare_logcat.log");
				if(!logfile.exists()) logfile.createNewFile();
			
				FileWriter logwriter = new FileWriter(logfile.getAbsolutePath());
				writer = new PrintWriter(logwriter);
				// Log.d(TAG,"Logcat writer: " + writer);
			} catch(IOException e) {
				// Log.e(TAG,"Log file creation error: " + e.getMessage());
			}
		}
		
		public void run() {
		    try {
		        Process process = Runtime.getRuntime().exec("logcat -f /storage/sdcard0/Download/peershare/peershare_logcat.log -v time " + 
		        		"PeerSenseService:D PeerSenseActivity:D DatabaseManager:D SecretAdapter:D MySecrets:D FacebookActivity:D FacebookAgent:D " +
		        		"PeerSenseClient:D PeerSenseServiceAgent:D PeerShareApplication:D *:W");
		      } catch (IOException e) {
		      }
		}
	}
}

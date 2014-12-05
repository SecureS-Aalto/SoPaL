package org.sesy.socialauth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.brickred.socialauth.AuthProvider;
import org.brickred.socialauth.Profile;
import org.brickred.socialauth.android.DialogListener;
import org.brickred.socialauth.android.SocialAuthAdapter;
import org.brickred.socialauth.android.SocialAuthAdapter.Provider;
import org.brickred.socialauth.android.SocialAuthError;
import org.brickred.socialauth.android.SocialAuthListener;
import org.brickred.socialauth.util.AccessGrant;
import org.sesy.tetheringapp.Logger;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.facebook.Request;
import com.facebook.Session;
import com.facebook.SessionState;
import com.facebook.model.GraphUser;

public class SocialAuthManager {
	// handlers to supported social networks
	private SocialAuthAdapter social_auth_adapter;
	private Session facebookSession;
	private Context context;
	
	// add some structure to hold information about active accounts
	List<SocialNetworkAccount> activeAccounts;
	List<Integer> supportedNetworks;
	List<Integer> authenticatedAccounts;
	
	private ProgressDialog progressDialog;
	private Handler linkedInError;
	private boolean linkedInProfileProcessed;
	
	private static final String TAG = "SocialAuthManager";
	
	public static final int FACEBOOK = 1;
	public static final int LINKEDIN = 2;
	public static final String AUTH_CHOICE_COMPLETED = "authChoiceCompleted";
	public static final String SIGNOUT_CHOICE_COMPLETED = "signoutChoiceCompleted";
	public static final String SN_AUTH_COMPLETED = "snAuthCompleted";
	public static final String AUTH_COMPLETED = "authCompleted";
	public static final String AUTH_FAILED = "authFailed";
	public static final String AUTH_ABORTED = "authAborted";
	public static final String SIGNOUT_COMPLETED = "signOutCompleted";
	
	private static final String SN_PREFS = "SNDataPrefs";
	private static final String LINKEDIN_AUTHORIZED = "liAuthorized";
	private static final String LINKEDIN_UNAME = "liUname";
	private static final String LINKEDIN_UID = "liUid";
	private static final String LINKEDIN_TOKEN = "liToken";
	
	private AuthenticationActivityReceiver authReceiver;
	IntentFilter auth_filter;
	
	public SocialAuthManager(Context ctx) {
		// constructor
		this.social_auth_adapter = new SocialAuthAdapter(new ResponseListener());
		this.linkedInProfileProcessed = false;
		
		this.activeAccounts = new ArrayList<SocialNetworkAccount>();
		this.authenticatedAccounts = new ArrayList<Integer>();
		this.context = ctx;
		
		this.progressDialog = null;
		
		this.supportedNetworks = new ArrayList<Integer>();
		this.supportedNetworks.add(SocialAuthManager.FACEBOOK);
		this.supportedNetworks.add(SocialAuthManager.LINKEDIN);
		
		auth_filter = new IntentFilter(SN_AUTH_COMPLETED);
		auth_filter.addAction(AUTH_CHOICE_COMPLETED);
		auth_filter.addAction(SIGNOUT_CHOICE_COMPLETED);
		authReceiver = new AuthenticationActivityReceiver();
		
		try {
			this.context.registerReceiver(authReceiver, auth_filter);
		} catch(IllegalArgumentException e) {}
		
		// check which account we're logged in, and add it to the structure
		this.facebookSession = Session.openActiveSessionFromCache(context);
		//Log.d(TAG,"facebookSession: " + this.facebookSession);
		if (facebookSession != null && facebookSession.isOpened()) {
			// Facebook session is open
			//Log.d(TAG, "Facebook session is already opened");
			// request permissions
			List<String> availablePermissions = this.facebookSession.getPermissions();
			if(!availablePermissions.contains("user_friends")) {
				List<String> permissions = Arrays.asList("user_friends");
				facebookSession.requestNewReadPermissions(new Session.NewPermissionsRequest((Activity) ctx, permissions));
			}

			this.authenticatedAccounts.add(SocialAuthManager.FACEBOOK);
			onFacebookSessionOpened(this.facebookSession);
		} 
		
		SharedPreferences settings = this.context.getSharedPreferences(SocialAuthManager.SN_PREFS, 0);
		boolean li = settings.getBoolean(SocialAuthManager.LINKEDIN_AUTHORIZED, false);
		if(li) {
			this.authenticatedAccounts.add(SocialAuthManager.LINKEDIN);
			try {
				//Log.d(TAG,"Trying to get Linkedin handler");
				social_auth_adapter.authorize(context, Provider.LINKEDIN);	
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		// authentication success broadcast receiver
	}
	
	public List<SocialNetworkAccount> getAuthenticatedSocialNetworkAccounts() {
		// return data from the structure
		//Log.d(TAG,"Returning getAuthenticatedSocialNetworkAccounts:");
		//for(SocialNetworkAccount a : this.activeAccounts)
			//Log.d(TAG,"SN id: " + a.getSocialNetworkID() + " name: " + a.getUsername());
		return this.activeAccounts;
	}
	
	public List<Integer> getAuthenticatedAccounts() {
		return this.authenticatedAccounts;
	}
	
	public List<Integer> getSupportedSocialNetworks() {
		return this.supportedNetworks;
	}
	
	public void authenticate() {
		// start a new activity to let user choose which SN to authenticate with
		//Log.d(TAG,"Calling authenticate");
		Intent i = new Intent(context, AuthenticationActivity.class);
		if(!this.authenticatedAccounts.contains(FACEBOOK))
			i.putExtra("Facebook", 1);
		if(!this.authenticatedAccounts.contains(LINKEDIN))
			i.putExtra("LinkedIn", 1);
		context.startActivity(i);
	}
	
	public void unregisterReceivers() {
		if(authReceiver!=null) {
			try {
				this.context.unregisterReceiver(authReceiver);
			} catch(IllegalArgumentException e) {}
		}
	}
	
	public void registerReceivers() {
		if(authReceiver!=null) {
			try {
				this.context.registerReceiver(authReceiver,auth_filter);
			} catch(IllegalArgumentException e) {}
		}		
	}
		
	private void onFacebookSessionOpened(Session session) {
		requestSocialGraphData(session);
	}
	
	private Session.StatusCallback callback = new Session.StatusCallback() {
		@Override
		public void call(Session session, SessionState state,
				Exception exception) {
			//Log.i(TAG, "call() invoked");
			onSessionStateChange(session, state, exception);
		}
	};
	
	private void onSessionStateChange(Session session, SessionState state,
			Exception exception) {
		/*Log.d(TAG,
				"openActiveSession call(): Current session state: "
						+ state.toString());*/
		if (exception != null) {
			//Log.e(TAG, "Facebook login exception: " + exception.getMessage());
			Logger.e(TAG, "Facebook login exception: " + exception.getMessage(), this);
			Intent i = new Intent(AUTH_FAILED);
			i.putExtra("SN", FACEBOOK);
			i.putExtra("error", exception.getMessage());
			context.sendBroadcast(i);
			return;
		}

		if (session.isOpened()) {
			//Log.i(TAG, "Facebook session is opened");
			facebookSession = session;
			onFacebookSessionOpened(session);
		} else if (state == SessionState.CLOSED_LOGIN_FAILED) {
			//Log.d(TAG, "Facebook session closed, login failed");
			Logger.d(TAG, "Facebook session closed, login failed", this);
			
			Intent i = new Intent(AUTH_FAILED);
			i.putExtra("SN", FACEBOOK);
			i.putExtra("error", "Facebook session closed, login failed");
			context.sendBroadcast(i);
			return;
		}
		//Log.d(TAG, "Current session state: " + state.toString());
		Logger.d(TAG, "Current session state: " + state.toString(), this);
	}
	
	private void requestSocialGraphData(Session session) {
		// make request to the /me API
		//Log.d(TAG, "making request to social graph");
		Request.newMeRequest(session,
				new Request.GraphUserCallback() {
					
					@Override
					public void onCompleted(GraphUser user, com.facebook.Response response) {
						Logger.d(TAG,"Facebook Request on completed. Response: " + response.toString(), this);
						//Log.d(TAG, "Facebook Request on completed. Response: "+ response.toString());
						if (user != null) {
							//Log.i(TAG,"Logged in to Facebook as: " + user.getId() + " " + user.getName());
							Logger.i(TAG,"Logged in to Facebook as: " + user.getId() + " " + user.getName(), this);
							
							String FacebookName = new String(user.getName());
							long FacebookID = Long.valueOf(user.getId());
							SharedPreferences settings = context.getSharedPreferences(SocialAuthManager.SN_PREFS, 0);
							SharedPreferences.Editor editor = settings.edit();
							editor.putString("FBName", FacebookName);
							editor.putLong("FBid", FacebookID);
							editor.commit();
							
							SocialNetworkAccount account = new SocialNetworkAccount();
							account.setUsername(user.getName());
							account.setSocialNetworkID(FACEBOOK);
							account.setSocialNetworkName(new String("Facebook"));
							account.setUserID(user.getId());
							account.setUserToken(facebookSession.getAccessToken());
							if(!activeAccounts.contains(account))
								activeAccounts.add(account);
							if(!authenticatedAccounts.contains(SocialAuthManager.FACEBOOK))
								authenticatedAccounts.add(SocialAuthManager.FACEBOOK);
							if(progressDialog!=null) {
								//Log.d(TAG,"Dismissing progress dialog");
								progressDialog.dismiss();
								progressDialog = null;
							}
							Intent i = new Intent(SN_AUTH_COMPLETED);
							i.putExtra("SN", FACEBOOK);
							//Log.d(TAG,"Sending SN_AUTH completed for FB");
							context.sendBroadcast(i);
						} else {
							//Log.d(TAG, "Handle lack of network error");
							SharedPreferences settings = context.getSharedPreferences(SocialAuthManager.SN_PREFS, 0);
							
							String name = settings.getString("FBName", null);
							long id = settings.getLong("FBid", -1);
							if(name != null && id != -1) {
								SocialNetworkAccount account = new SocialNetworkAccount();
								account.setUsername(name);
								account.setSocialNetworkID(FACEBOOK);
								account.setSocialNetworkName(new String("Facebook"));
								account.setUserID(String.valueOf(id));
								account.setUserToken(facebookSession.getAccessToken());
								if(!activeAccounts.contains(account))
									activeAccounts.add(account);
								if(!authenticatedAccounts.contains(SocialAuthManager.FACEBOOK))
									authenticatedAccounts.add(SocialAuthManager.FACEBOOK);
								if(progressDialog!=null) {
									progressDialog.dismiss();
									progressDialog = null;
								}
								Intent i = new Intent(SN_AUTH_COMPLETED);
								i.putExtra("SN", FACEBOOK);
								//Log.d(TAG,"Sending SN_AUTH completed for FB");
								context.sendBroadcast(i);
							}
						}
						
					}
				}).executeAsync();
	}
	
	final class ResponseListener implements DialogListener {
		
		public void onComplete(Bundle values) {
			//Log.d(TAG,"LinkedIn response onComplete() called");
			if(!authenticatedAccounts.contains(SocialAuthManager.LINKEDIN)) {
				social_auth_adapter.getUserProfileAsync(new ProfileDataListener());
				//Log.d(TAG,"Linked in getUserProfileAsync");
				authenticatedAccounts.add(Integer.valueOf(SocialAuthManager.LINKEDIN));
			} else {
				if(!linkedInProfileProcessed) {
					Profile profile = social_auth_adapter.getUserProfile();
					processLinkedInProfileData(profile);
				}
			}
		}

		@Override
		public void onBack() {
			//Log.d(TAG,"onBack()");
			if(progressDialog!=null) {
				progressDialog.dismiss();
				progressDialog=null;
			}
			Intent i = new Intent(AUTH_ABORTED);
			i.putExtra("SN", LINKEDIN);
			context.sendBroadcast(i);
		}

		@Override
		public void onCancel() {
			//Log.d(TAG,"onCancel()");
			if(progressDialog!=null) {
				progressDialog.dismiss();
				progressDialog=null;
			}
			Intent i = new Intent(AUTH_ABORTED);
			i.putExtra("SN", LINKEDIN);
			context.sendBroadcast(i);
		}

		@Override
		public void onError(SocialAuthError arg0) {
			//Log.d(TAG,"Response Listener onError() for socialAuth: " + arg0.getMessage());
			SharedPreferences settings = SocialAuthManager.this.context.getSharedPreferences(SocialAuthManager.SN_PREFS, 0);			
			String uname = settings.getString(SocialAuthManager.LINKEDIN_UNAME, null);
			String uid = settings.getString(LINKEDIN_UID, null);
			String token = settings.getString(LINKEDIN_TOKEN, null);
			
			
			if(uname==null || uid==null || token==null) {
				final String msg = arg0.getMessage();
				if(linkedInError==null) {
					linkedInError = new Handler(Looper.getMainLooper());
					Runnable task = new Runnable() {				
						@Override
						public void run() {
							linkedInError = null;
							//Log.d(TAG,"Checking if LinkedIn auth really failed");
							if(progressDialog!=null) {
								progressDialog.dismiss();
								progressDialog=null;
							}
							
							if(authenticatedAccounts.contains(SocialAuthManager.LINKEDIN)) {
								//Log.d(TAG,"Authenticated to LinkedIn");
								return;
							}
							
							Intent i = new Intent(AUTH_FAILED);
							i.putExtra("error", msg);
							i.putExtra("SN", LINKEDIN);
							context.sendBroadcast(i);
							//Log.d(TAG,"Not authenticated to LinkedIn");
						}
					};
					linkedInError.postDelayed(task, 32000);
				}
			} else {
				//this.onCancel();
				//social_auth_adapter.
				
				if(progressDialog!=null) {
					progressDialog.dismiss();
					progressDialog=null;
				}
				
				SocialNetworkAccount account = new SocialNetworkAccount();
				account.setSocialNetworkID(SocialAuthManager.LINKEDIN);
				account.setSocialNetworkName(new String("LinkedIn"));
				account.setUserID(uid);
				account.setUsername(uname);
				account.setUserToken(token);
				
				if(!activeAccounts.contains(account))
					activeAccounts.add(account);
				if(!authenticatedAccounts.contains(SocialAuthManager.FACEBOOK))
					authenticatedAccounts.add(SocialAuthManager.FACEBOOK);
				Intent i = new Intent(SN_AUTH_COMPLETED);
				i.putExtra("SN", LINKEDIN);
				//Log.d(TAG,"Sending SN_AUTH completed for LI");
				context.sendBroadcast(i);
			}
		}

	}
	
	public class ProfileDataListener implements SocialAuthListener<Profile> {

		@Override
		public void onError(SocialAuthError arg0) {
			//Log.d(TAG,"ProfileDataListener onError(): " + arg0.getMessage());
		}

		@Override
		public void onExecute(String providerStr, Profile prof) {
			if(!linkedInProfileProcessed)
				processLinkedInProfileData(prof);
		}
	}
	
	private void processLinkedInProfileData(Profile prof) {
		this.linkedInProfileProcessed = true;
		Profile profileMap = prof;
		SocialNetworkAccount account = new SocialNetworkAccount();
		
		account.setUsername(profileMap.getFirstName() + " " + profileMap.getLastName());
		account.setSocialNetworkID(LINKEDIN);
		account.setSocialNetworkName(new String("LinkedIn"));
		account.setUserID(profileMap.getValidatedId());
		
		String linkedInToken = null;
		try {
			AuthProvider provider = social_auth_adapter.getCurrentProvider();
			if(provider!=null) {
				AccessGrant grant = provider.getAccessGrant();
				linkedInToken = "key=" + grant.getKey() + "&secret=" + grant.getSecret();
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		account.setUserToken(linkedInToken);
		if(!activeAccounts.contains(account))
			activeAccounts.add(account);
		if(!authenticatedAccounts.contains(SocialAuthManager.LINKEDIN))
			authenticatedAccounts.add(SocialAuthManager.LINKEDIN);
		
		SharedPreferences settings = SocialAuthManager.this.context.getSharedPreferences(SocialAuthManager.SN_PREFS, 0);
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean(SocialAuthManager.LINKEDIN_AUTHORIZED,true);
		editor.putString(SocialAuthManager.LINKEDIN_UNAME, account.getUsername());
		editor.putString(SocialAuthManager.LINKEDIN_UID, account.getUserID());
		editor.putString(SocialAuthManager.LINKEDIN_TOKEN, linkedInToken);
		editor.commit();
		
		if(progressDialog!=null) {
			progressDialog.dismiss();
			progressDialog = null;
		}
		
		Intent i = new Intent(SN_AUTH_COMPLETED);
		i.putExtra("SN", LINKEDIN);
		//Log.d(TAG,"Sending SN_AUTH completed for LI");
		context.sendBroadcast(i);
	}
	
	private class AuthenticationActivityReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			//Log.d(TAG,"Received intent: " + action);
			if(AUTH_CHOICE_COMPLETED.equals(action)) {
				//Log.d(TAG,"AuthActivity intent received");
				int sn = intent.getIntExtra("SN", 1);
				// show progress dialog
				String network = null;
				if(sn == LINKEDIN)
					network = "LinkedIn";
				else
					network = "Facebook";
				if(sn == LINKEDIN) {
					// authenticate with LI
					social_auth_adapter.authorize(context, Provider.LINKEDIN);
				} else if(sn == FACEBOOK) {
					// authenticate with FB
					//Log.d(TAG, "call openActiveSession");
					//Session.openActiveSession((Activity)context, true, callback);
					Session session = new Session(SocialAuthManager.this.context);
					Session.setActiveSession(session);
					Session.OpenRequest request = new Session.OpenRequest((Activity)SocialAuthManager.this.context);
					request.setPermissions(Arrays.asList("user_friends"));
					request.setCallback(callback);

					// get active session
					
					/*if (session == null || session.isClosed()) 
					{
					    session = new Session(SocialAuthManager.this.context);
					}*/
					session.openForRead(request);
				}
				launchRingDialog(true,network);
			} else if(SN_AUTH_COMPLETED.equals(action)) {
				if(progressDialog!=null) {
					progressDialog.dismiss();
					progressDialog = null;
				}
				Intent j = new Intent(AUTH_COMPLETED);
				j.putExtra("SN", intent.getIntExtra("SN",SocialAuthManager.FACEBOOK));
				context.sendBroadcast(j);
			} else if(SIGNOUT_CHOICE_COMPLETED.equals(action)) {
				int sn = intent.getIntExtra("SN", 1);
				if(sn == LINKEDIN) {
					String name = social_auth_adapter.getCurrentProvider().getProviderId();
					social_auth_adapter.signOut(SocialAuthManager.this.context,name);
					linkedInProfileProcessed = false;
				} else {
					facebookSession.closeAndClearTokenInformation();
				}
				authenticatedAccounts.remove(Integer.valueOf(sn));
				for(SocialNetworkAccount a : activeAccounts) {
					if(a.getSocialNetworkID() == sn) {
						activeAccounts.remove(a);
						break;
					}
				}
				
				SharedPreferences settings = SocialAuthManager.this.context.getSharedPreferences(SocialAuthManager.SN_PREFS, 0);
				SharedPreferences.Editor editor = settings.edit();
				if(sn == SocialAuthManager.LINKEDIN) {
					editor.remove(SocialAuthManager.LINKEDIN_AUTHORIZED);
					editor.remove(SocialAuthManager.LINKEDIN_UNAME);
					editor.remove(SocialAuthManager.LINKEDIN_UID);
					editor.remove(SocialAuthManager.LINKEDIN_TOKEN);
				} else {
					editor.remove("FBName");
					editor.remove("FBid");					
				}
				editor.commit();
				
				Intent j = new Intent(SIGNOUT_COMPLETED);
				j.putExtra("SN", sn);
				context.sendBroadcast(j);
			}
		}
	}
	
    public void launchRingDialog(boolean login, String network) {
    	String msg = null;
    	if(login)
    		msg = "Authenticating to " + network;
    	else
    		msg = "Signing out from " + network;
        progressDialog = ProgressDialog.show(context, "Please wait ...", msg, true);
        progressDialog.setCancelable(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                	while(true)
                		Thread.sleep(10000);
                } catch (Exception e) {
                	
                }
                progressDialog.dismiss();
            }
        }).start();
    }


}

<?php

include_once(dirname(__FILE__) . "/../tools.php");

$conf = array('appId' => 107152436111996, 'secret' => '4289a5244d98814451ee104174ea0117');
$confbeta = array('appId' => 465789826851274, 'secret' => '1382b134e93209036213880743e8597d');
$confgamma = array('appId' => 1466012520282858, 'secret' => 'c163cfaf4a80de76633172cb8176098b');

class FacebookHandler {

	public static function getFriends($sn_id, $token) {
		try {
	        $url = "https://graph.facebook.com/v1.0/me/friends?access_token=" . $token;
	        $ch = curl_init();
	        curl_setopt($ch, CURLOPT_URL, $url);
	        curl_setopt($ch, CURLOPT_RETURNTRANSFER, 1);
	        $res = curl_exec($ch);
	        $friends_response = json_decode($res);
	        $friends = $friends_response->{'data'};
			return $friends;
		} catch(FacebookApiException $e) {
			Log::write('Facebook API exception: ' . $e->getMessage());
	        return null;
	    }
	}

	public static function getMutualFriends($user_id_with_token, $token, $user_id1) {
		try {
	        $url = "https://graph.facebook.com/v1.0/" . $user_id_with_token . "/mutualfriends/" . $user_id1 . "?access_token=" . $token;
	        $ch = curl_init();
	        curl_setopt($ch, CURLOPT_URL, $url);
	        curl_setopt($ch, CURLOPT_RETURNTRANSFER, 1);
	        $res = curl_exec($ch);
	        $friends_response = json_decode($res);
	        $friends = $friends_response->{'data'};
			return $friends;
		} catch(FacebookApiException $e) {
			Log::write('Facebook API exception: ' . $e->getMessage());
	        return null;
	    }
	}

	public static function getGroupMembers($groupId, $token) {
		$url = "https://graph.facebook.com/" . $groupId . "/members?access_token=" . $token;
		error_log("Querying Facebook at: " . $url . "\n",3,"./log.txt");

		$ch = curl_init();
		curl_setopt($ch, CURLOPT_URL, $url);
		curl_setopt($ch, CURLOPT_RETURNTRANSFER, 1);
		$res = curl_exec($ch);
		$jsonRes = json_decode($res);
		$members = $jsonRes->{'data'};

		return $members;
	}

	public static function getAppAccessToken($version) {
		global $conf;
		global $confbeta;
		global $confgamma;
		if($version == 1)
			$app_token_url = "https://graph.facebook.com/oauth/access_token?client_id=" . $confbeta['appId'] . "&client_secret=" . $confbeta['secret'] . "&grant_type=client_credentials";
		else if($version == 2)
			$app_token_url = "https://graph.facebook.com/oauth/access_token?client_id=" . $confgamma['appId'] . "&client_secret=" . $confgamma['secret'] . "&grant_type=client_credentials";
		else
	  		$app_token_url = "https://graph.facebook.com/oauth/access_token?client_id=" . $conf['appId'] . "&client_secret=" . $conf['secret'] . "&grant_type=client_credentials";
	  	$ch = curl_init();
	  	curl_setopt($ch, CURLOPT_URL, $app_token_url);
	  	curl_setopt($ch, CURLOPT_RETURNTRANSFER, 1);
	  	$res = curl_exec($ch);
	  	parse_str($res, $token);
		//error_log("\nObtained app access token: " . $token['access_token'],3,"./log.txt");
		return $token['access_token'];	
	}

	public static function verifyTokenInit($token,$sn_id,$sn_type) {
		global $conf;
		global $confbeta;
		global $confgamma;
		if($sn_type != 1) {
			error_log("Unsupported social network type\n",3,"./log.txt");
			return -2; // token not supported
		}
		for($i=0;$i<3;$i++) {
			$app_token = self::getAppAccessToken($i);
    		$check_url = "https://graph.facebook.com/debug_token?input_token=" . $token . "&access_token=" . $app_token;
    		//error_log("Querying for Facebook token validity: " . $check_url . "\n",3,"./log.txt");
    		$ch = curl_init();
    		curl_setopt($ch, CURLOPT_URL, $check_url);
    		curl_setopt($ch, CURLOPT_RETURNTRANSFER, 1);
    		$res = curl_exec($ch);
 			$check_response = json_decode($res);
    		//error_log("Facebook query for valid token: " . var_export($check_response,true) . "\n",3,"./log.txt");
    		if(isset($check_response->data)) {
				$data = $check_response->data;
				error_log("ID: " . $data->app_id . " alphaID=" . $conf['appId'] . " betaID=" . $confbeta['appId'] . "\n",3,"./log.txt");
				if($i==0 && $data->app_id == $conf['appId'])
					return $data->user_id;
				else if($i==0)
					continue; // check beta app
				else if($i==1 && $data->app_id == $confbeta['appId']) {
					error_log("Returning id for beta app token verification: " . $data->app_id . "\n",3,"./log.txt");
					return $data->user_id;
				} else if($i==1)
					continue; // check gamma app
				else if($i==2 && $data->app_id == $confgamma['appId']) {
					error_log("Returning id for gamma app token verification: " . $data->app_id . "\n",3,"./log.txt");
					return $data->user_id;
				} else {
					return -2;
				}
			} else {
				if($i==2) {
					return -2;
				}
			}
		}
	}
}

?>
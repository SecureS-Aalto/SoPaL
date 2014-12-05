<?php

$linkedinconf = array('key' => '7597bz4nqbcuu9', 'secret' => 'XmwjsNhHubRBNL9p');

class LinkedInHandler {
	public static function getContacts($user_token) {
		global $linkedinconf;

		$contacts = array();
	    error_log("Fetching LinkedIn profile; token: " . var_export($user_token,true) . "\n",3,"./log.txt");
	    error_log("LinkedIn app data: " . var_export($linkedinconf,true) . "\n",3,"./log.txt");
	    
	    $oauth = new OAuth($linkedinconf['key'], $linkedinconf['secret']);
	    $token_blocks = explode("&",$user_token);
	    error_log("Token blocks: " . var_export($token_blocks,true) . "\n",3,"./log.txt");
	    $user_key = explode("=",$token_blocks[0]);
	    $user_secret = explode("=",$token_blocks[1]);
	    $oauth->setToken($user_key[1], $user_secret[1]);

	    $params = array();
	    $headers = array();
	    $method = OAUTH_HTTP_METHOD_GET;

	    // Specify LinkedIn API endpoint to retrieve your own profile
	    $url = "http://api.linkedin.com/v1/people/~/connections?format=json";

	    // Make call to LinkedIn to retrieve your own profile
	    $oauth->fetch($url, $params, $method, $headers);
	    $response = $oauth->getLastResponse();
	    $json = json_decode($response);
	    $values = $json->values;
	    foreach($values as $contact) {
	    	$contacts[] = array('id' => $contact->id,
	                            'name' => $contact->firstName . " " . $contact->lastName);
	    }

		return $contacts;
	}
}

?>
<?php
require_once './peershare_handler.php';
require_once './tools.php';
require_once './socialnetworks/facebookhandler.php';

date_default_timezone_set("Europe/Helsinki");

$opts = array('http' => array('header' => 'Accept-Charset: UTF-8, *;q=0'));
$context = stream_context_create($opts);
$input = file_get_contents("php://input",false,$context);
Log::write("Input: " . var_export(prepareJSON($input),true));
$data = json_decode(prepareJSON($input));
Log::write("\n\n----------- register.php called at " . date("r") . " ---------------");
Log::write("Received JSON: " . var_export($data,true));

$legacy_check_failed = (!isset($data->{'id'}) || !isset($data->{'sn'}) || !isset($data->{'name'}) || !isset($data->{'name'}));
$new_check_failed = ( !isset($data->{'identities'}) || !isset($data->{'tokens'}) );
Log::write("Legacy check failed: " . $legacy_check_failed);
Log::write("New check failed: " . $new_check_failed);
if( $legacy_check_failed && $new_check_failed ) {
	$response = array('status' => "Error", 'info' => "Wrong request. Social information needed");
	$json = json_encode($response);
	echo $json;
	Log::write("Sending wrong register request");
	exit(1);
}

$legacy_registration = 0;

if(isset($data->{'identities'})) {
	$identities = $data->{'identities'};
	$tokens = $data->{'tokens'};
	Log::write("Identities: " . var_export($identities,true));
} else {
	$id = $data->{'id'};
	$sn = $data->{'sn'};
	$token = $data->{'token'};
	$name = $data->{'name'};
	$legacy_registration = 1;
}

Log::write("Legacy registration flag: " . $legacy_registration);

if($legacy_registration) {
	Log::write("ID: " . $id . " sn: " . $sn . " name: " . $name);
	if($sn == 1) {
		$obtained_id = FacebookHandler::verifyTokenInit($token,$id,$sn);
	} else {
		// We don't verify user token for other social networks
		$obtained_id = $id;
	}
} else {
	error_log("App authentication for new registration version");
	for($i=0; $i < count($identities); $i++) {
		Log::write("Checking identity: " . var_export($identities[$i],true));
		$identity = (array) $identities[$i];
		Log::write("ID: " . $identity['id'] . " sn: " . $identity['sn'] . " name: " . $identity['name'] . " token: " . $tokens[$identity['sn']]);
		if($identity['sn'] == 1) {
			$obtained_id = FacebookHandler::verifyTokenInit($tokens[$identity['sn']],$identity['id'],$identity['sn']);
		} else {
        	// We don't verify user token for other social networks
        	$obtained_id = $identity['id'];		
		}
		if($obtained_id != $identity['id']) {
			$ps_id = -2;
			break;
		}
	}
}

Log::write("Obtained ID: " . $obtained_id);


// open database
$db = openDb();

if($legacy_registration) {
	if($obtained_id == $id) {
		if(!isset($data->{'psID'})) {
			$ps_id = registerUser($db, $id, $sn, $name);
		} else {
        	$psID = $data->{'psID'};
        	$ps_id = addUserAccount($db, $id, $sn, $name, $psID);
        	$res = addUsersToSharedDataSet($db, $ps_id, $sn, $id, $token);
        	Log::write("addUsersToSharedDataSet returns: " . $res);
		}
	} else {
		$ps_id = -2;
	}
} else if(!isset($ps_id) || $ps_id!=-2) {
	$ps_id = registerUsers($db, $identities);
}


if($ps_id > 0) {
    if($legacy_registration) {
        $response = array('status' => "OK", 'id' => $ps_id, 'sn' => $sn);
    } else {
		$sns = array();
		foreach($identities as $keys => $values) {
			$tmp = (array)$identities[$keys];
			$sns[] = $tmp['sn'];
		}		
        $response = array('status' => "OK", 'id' => $ps_id, 'version' => "2.0", 'sns' => $sns);
    }
} else if($ps_id == -1) {
	$response = array('status' => "Error", 'info' => "Unspecified database error");
} else {
	$response = array('status' => "Error", 'info' => "Invalid user access token");
}

$json = json_encode($response);
Log::write("Register response: " . var_export($json,true) . "\n");
echo $json;

?>

<?php
require_once './peershare_handler.php';
require_once './tools.php';

date_default_timezone_set("Europe/Helsinki");

$tmp = file_get_contents('php://input');
$data = json_decode($tmp);
Log::write("\n\n----------- upload.php called at " . date("r") . " ---------------");
Log::write("Submitted data: " . var_export($data,true));

$ps_id = $data->ps_id;
$tokens = $data->tokens;
$secrets = $data->secrets;
$operation = $data->operation;

Log::write("SN access tokens: " . var_export($tokens,true));
Log::write("PeerShare ID: " . $ps_id);
Log::write("Operation: " . $operation);

// open db
$db = openDb();

$obtained_id = verifySocialNetworkTokens($db, $tokens, $ps_id);
Log::write("Obtained id: $obtained_id");

if(!is_numeric($obtained_id) || $obtained_id > 0) {
    // update the token
    foreach($tokens as $sn_type => $token) {
    	$sn_id = getSnInfoId($db, $sn_type, $ps_id);
    	if ($sn_id != -1) {
    		updateSnInfoToken($db, $sn_type, $sn_id, $token);	
    	}
    }

	if($operation == "upload") {
		$ids = storeSecrets($db, $ps_id, $secrets, $tokens);
		Log::write("storeSecrets returns: " . var_export($ids,true));
		if($ids==-1) {
			$response = array('status' => "Error", 'info' => "unspecified database error");
		} else {
			$response = array('status' => "OK", 'objects' => $ids);
		}
	} else if($operation == "update") {
		$todelete = updateSecrets($db, $ps_id, $secrets, $tokens);
		Log::write("updateSecrets to delete: " . var_export($todelete,true));
		if($todelete != -1) {
			$response = array('status' => "OK", 'remove' => $todelete);
		} else {
			$response = array('status' => "Error", 'info' => "unspecified database error");
		}
	} else if($operation == "delete") {
		$secrets = $data->{'objects'};
		$success = removeSecrets($db, $ps_id, $secrets, $token);
		Log::write("removeSecrets returns: " . $success);
		if($success == 0) {
			$response = array('status' => "OK");
		} else {
			$response = array('status' => "Error", 'info' => "unspecified database error");
		}
	}
} else {
	$response = array('status' => "Error", 'info' => "Invalid user access token");
}

$json = json_encode($response);
Log::write("Final response data=" . var_export($json,true));
echo $json;

?>


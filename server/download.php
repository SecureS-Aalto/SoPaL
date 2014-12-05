<?php
require_once './peershare_handler.php';
require_once './tools.php';

date_default_timezone_set("Europe/Helsinki");
$data = json_decode(file_get_contents('php://input'));
Log::write("\n\n----------- download.php called at " . date("r") . " ---------------");

$ps_id = $data->ps_id;
$tokens = $data->tokens;

Log::write("User PeerShare ID: " . $ps_id);

// open db
$db = openDb();

$obtained_id = verifySocialNetworkTokens($db, $tokens, $ps_id);
Log::write("Token updated ID: " . $obtained_id);

if(!is_numeric($obtained_id) || $obtained_id > 0) {
	Log::write("Tokens to submit: " . var_export($tokens,true));
	$secrets = getAvailableSecrets($db, $ps_id, $tokens);
	Log::write("Obtained secrets: " . var_export($secrets,true));
	if($secrets != -1) {
		$response = array('status' => "OK", 'objects' => $secrets);
	} else {
		$response = array('status' => "error", 'info' => 'Unspecified database problems');
	}
} else {
	$response = array('status' => "error", 'info' => "Invalid user access token");
}

$json = json_encode($response);
Log::write("Sending download response: "	. var_export($json,true));
echo $json;
?>


<?php
require_once './peershare_handler.php';
require_once './tools.php';

$data = json_decode(file_get_contents('php://input'));
Log::write("\n\n----------- unregister.php called at " . date("r") . " ---------------");
Log::write("JSON: " . print_r($data,true));
if(!isset($data->{'ps_id'}) || !isset($data->{'sn_id'}) || !isset($data->{'sn'})) {
	$response = array('status' => "Error", 'info' => "PeerSense ID and social network information needed to complete unregistration");
	$json = json_encode($response);
	echo $json;
	exit(1);
}

$ps_id = $data->{'ps_id'};
$user_id = $data->{'sn_id'};
$sn_id = $data->{'sn'};
Log::write("PeerSense ID: $ps_id, Social ID: $user_id, network ID: $sn_id");

// open db
$db = openDb();

if(unregisterUser($db, $ps_id, $user_id, $sn_id)!=-1) {
	$response = array('status' => "OK");
} else {
	$response = array('status' => "Error", 'info' => "Your account cannot be unregistered at the moment");
}
$json = json_encode($response);
echo $json;

?>
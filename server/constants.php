<?php

class Specificity {
	const UNDISCLOSED = -1;
	const USER_SPECIFIC = 1;
	const DEVICE_SPECIFIC = 2;
}
	
class BindingType {
	const USER_ASSERTED = 1;
	const OWNER_ASSERTED = 2;
}

class DataType {
	const BTMAC = 1;
	const WIFIMAC = 2;
	const SCAMPIID = 3;
	const CROWDAPP = 4;
	const PUBLICKEY = 5;
}

class Algorithm {
	const PLAIN = "plain";
	const PLAIN_SHA1 = "plain/sha1";
}

class Description {
	const SERVER_GENERATED = "Server generated";
}

define("BEARER_CAPABILITY_LIFETIME", 7 * 24 * 3600 * 1000); // 7days

class DataSensitivity {
	const PRIVATE_SENS = 1;
	const PUBLIC_SENS = 2;
}

class SocialNetwork {
	const FACEBOOK = 1;	
	const LINKED_IN = 2;
}

class SharingPolicy {
	const FRIENDS = "Friends";
	const USER_ASSERTED = "User-asserted";
	const ONLY_ME = "Only me";
}

class ViewerRelation {
	const DIRECT_FRIEND = 0;
	const FRIEND_OF_FRIEND = 1;
}

?>
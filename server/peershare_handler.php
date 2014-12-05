<?php

require_once './dbhelper.php';
require_once './constants.php';
require_once './tools.php';
require_once './config.php';
if (FOF_ENABLED) {
	require_once './fof_handler.php';	
}

function verifySocialNetworkTokens($db, $tokens, $ps_id) {
	Log::write("verifySocialNetworkTokens; psID=" . $ps_id);
	$id = -1;
	try {
        $snInfoRows = getSnInfoByPsId($db, $ps_id);
    	if($snInfoRows) {
        	$id = $snInfoRows[0]['sn_id'];
		}
	} catch(PDOException $e) {
		Log::write("Token checking exception: " . $e->getMessage());
		return -2;
	}
	Log::write("Obtained social ID: " . $id);
	$tokens = (array)$tokens; //json_decode($tokens,true);
	Log::write("Submitted tokens: " . var_export($tokens,true));
	foreach($tokens as $sn_type => $token) {
		if($sn_type == 1) {
			$ret = FacebookHandler::verifyTokenInit($token, $id, 1);
			Log::write("Returned Facebook token verification: $ret");
			if($ret!=$id) {
				return $ret;
            }
		}
	}
	Log::write("Returning: " . $id);
	return $id;
}

function checkIfIdentitiesAlreadyRegistered($db, $identities) {
    $ids = array();
    $stm = $db->prepare("SELECT user_id FROM SnInfo WHERE sn_id=:uid AND sn_type=:sn");
    for($i=0; $i < count($identities); $i++) {
        $identity = $identities[$i];
        $stm->bindParam(":uid",$identity['id']);
        $stm->bindParam(":sn",$identity['sn']);
        $stm->execute();
        if(($row = $stm->fetch()) && ($row['user_id'] != NULL)) {
            $ids[] = $row['user_id'];
	}
    }

    Log::write("checkIfIdentitiesAlreadyRegistered returns: " . var_export($ids,true));
    $stm=null;
    return $ids;
}

function registerUsers($db, $identities) {
    try {
        Log::write("registerUsers() start");

        $db->beginTransaction();
        $ids = checkIfIdentitiesAlreadyRegistered($db, $identities);

        if(count($ids) == 0) {
            // we must create PeerShare ID
            $peershare_id = createNewUserInfoEntry($db);
            Log::write("Created user PeerShare ID : $peershare_id");
        } else {
            $peershare_id = $ids[0];
        }

        if($peershare_id != -1) {
            for($i=0;$i<count($identities);$i++) {
                $identity = $identities[$i];
                if(!in_array($identity['sn'],$ids)) {
                    Log::write("Identity: " . var_export($identity,true) . " must be registered");
                    createNewSnInfoEntry($db, $identity->sn, $identity->id, $identity->name, $peershare_id, "TRUE");
                } else {
                    Log::write("Identity: " . var_export($identity,true) . " already registered");
                }
            }
        }
        $db->commit();

        Log::write("Returned user PeerShare ID : $peershare_id");
        return $peershare_id;

    } catch(PDOException $e) {
        Log::write('Register users exception : ' . $e->getMessage());
        return -1;
    }
}

function registerUser($db, $sn_id, $sn_type, $sn_name) {
	try {
		Log::write("registerUser() start");
        Log::write("registerUser: snID=$sn_id, snType=$sn_type");

		$db->beginTransaction();

        // check if user already registered
		$snInfo = getSnInfo($db, $sn_id, $sn_type);

		if($snInfo === null || $snInfo['active_user'] === 'FALSE') {
            // create new userInfo
            $peersense_id = createNewUserInfoEntry($db);
            // create new SnInfo
            createNewSnInfoEntry($db, $sn_type, $sn_id, $sn_name, $peersense_id, 'TRUE');
		} else {
			$peersense_id = $snInfo['user_id'];
		}

		$db->commit();
		Log::write("Returned user PeerSense ID : $peersense_id");
		return $peersense_id;
	}
	catch (PDOException $e) {
		Log::write('Register user exception : ' . $e->getMessage());
		return -1;
	}
}

function addUserAccount($db, $sn_id, $sn_type, $sn_name, $psID) {
    try {
        Log::write("addUserAccount() starts");

        $db->beginTransaction();
		
        $snInfo = getSnInfo($db, $sn_id, $sn_type);
        if($snInfo === null || $snInfo['active_user'] == 'FALSE') {
            createNewSnInfoEntry($db, $sn_type, $sn_id, $sn_name, $psID, "TRUE");
			$peershare_id = $psID;
        } else {
            $peershare_id = $snInfo['user_id'];
        }

        $db->commit();
        Log::write("Returned user PeerShare ID : $peershare_id");

        return $peershare_id;

    } catch (PDOException $e) {
        Log::write('Register user exception : ' . $e->getMessage());
        return -1;
    }
}

function addUsersToSharedDataSet($db, $psID, $sn_type, $sn_id, $token) {
	$toUpdate = getDataToAddForUser($db, $psID, $sn_type);
	Log::write("Obtained data for update: " . var_export($toUpdate,true));
	
	if(count($toUpdate) > 0) {
    	$contacts = null;
    	if($sn_type == SocialNetwork::FACEBOOK) { // FACEBOOK
        	$contacts = FacebookHandler::getFriends($sn_id,$token);
    	} else if($sn_type == SocialNetwork::LINKED_IN) { // LINKEDIN
        	$contacts = LinkedInHandler::getContacts($token);
    	}
    	Log::write("Fetched contacts: " . var_export(count($contacts),true));

		try {
        	$db->beginTransaction();
        	foreach($toUpdate as $object_id) {
				$bid = createNewBindingEntry($db, $object_id, 0, $sn_id, $sn_type,
                                                				BindingType::OWNER_ASSERTED);

            	if(setPolicy($db, $bid, $sn_type, $sn_id, SharingPolicy::FRIENDS, $contacts) == -1) {			// HARDCODED POLICY: Friends
            		Log::write("addUsersToSharedDataSet. Error while setting sharing policies");
					//return -1;
				}
			}
			$db->commit();

			return 0;
    	} catch(PDOException $e) {
        	Log::write("Exception on getDataToAddForUser: " . $e->getMessage());
        	return -1;
    	}
	} else {
		return 0;
	}
}

function getDataToAddForUser($db, $ps_id, $sn_type) {
	$ids = array();
    try {
        $db->beginTransaction();

		$stm = $db->prepare("SELECT object_id, sn_type FROM Bindings WHERE sn_id IN (SELECT sn_id FROM SnInfo WHERE user_id=:uid)");
		$stm->bindParam(":uid",$ps_id);
		$stm->execute();
		while($row = $stm->fetch()) {
			Log::write("Checking if data are not already added. Adding type: $sn_type, retreived: " . $row['sn_type']);
			if($row['sn_type'] == $sn_type) {
				Log::write("Social network match aborting");
				$ids = array();
				break;
			}
			$ids[] = $row['object_id'];
		}
		$stm=null;
        $db->commit();

        return $ids;		
	} catch(PDOException $e) {
		Log::write("Exception on getDataToAddForUser: " . $e->getMessage());
		return null;
	}
}

function unregisterUser($db, $ps_id, $sn_id, $sn) {
	try {
		$db->beginTransaction();

		$stm = $db->prepare("UPDATE SnInfo SET active_user='FALSE' WHERE user_id=:id");
		$stm->bindParam(":id", $ps_id);
		$stm->execute();

		$stm = $db->prepare("SELECT bid, object_id FROM bindings WHERE sn_id=:id AND assertedby=-1");
		$stm->bindParam(":id",$sn_id);
		$stm->execute();
		while($row = $stm->fetch()) {
			deleteAclInfo($db, $row['bid']);

			deleteAllowedViewersByBid($db, $row['bid']);

			deleteSecretInfo($db, $row['object_id']);
		}

		$stm = $db->prepare("DELETE FROM Bindings WHERE sn_id=:id AND assertedby=-1");
		$stm->bindParam(":id", $sn_id);
		$stm->execute();
		$stm=null;
		$db->commit();
		return 0;
	} catch(PDOException $e) {
		Log::write('Register user exception : ' . $e->getMessage());
		return -1;
	}
}

function storeSecrets($db, $ps_id, $data, $tokens) {
	try {
		$ids = array();
		$tokens = get_object_vars($tokens);		

        $db->beginTransaction();

        if (checkUserExist($ps_id, $db) == false) {
            return -1;
        }

        $snInfoRows = getSnInfoByPsId($db, $ps_id);
        $sn_data = array();
        foreach ($snInfoRows as $row) {
			$sn_data[$row['sn_type']] = array('sn_id' => $row['sn_id']);
		}
		$social_query=null;
		Log::write("Obtained social network ID: " . var_export($sn_data,true));

		if(count($sn_data)==0) {
			Log::write("Transaction aborted. Given PeerShare ID doesn't correspond to any social profile");
			$db->rollBack();
			return -1;
		}

		$tmp = array_map(function($item) { return $item['sn_id']; }, $sn_data);
		$sn_id_set = "'" . implode("','",$tmp) . "'";
		$sn_type_set = implode(",",array_keys($sn_data));		

		Log::write("storeSecrets: data=" . var_export($data,true));
		Log::write("Submitted tokens: " . var_export($tokens,true));
		for($i=0;$i<count($data);$i++) {
			$secret = $data[$i];

			Log::write("Sn id set: $sn_id_set  sn type set: $sn_type_set");  
			$duplicates = $db->prepare("SELECT S.object_id, secret_type, secret_algorithm, description, sensitivity, specificity, platform_id, platform_app_id FROM SecretInfo S, Bindings B WHERE secret_type=:type " . 
				"AND secret_algorithm=:algo AND sensitivity=:sensitive AND specificity=:specific AND platform_id=:platform_id AND platform_app_id=:platform_app_id " .
				" AND B.object_id=S.object_id AND B.sn_id IN ($sn_id_set) AND B.sn_type IN ($sn_type_set)");
			$duplicates->bindParam(":type",$secret->type);
			$duplicates->bindParam(":algo",$secret->algorithm);
			//$duplicates->bindParam(":desc",$secret->description);
			$duplicates->bindParam(":sensitive",$secret->sensitivity);
			$duplicates->bindParam(":specific",$secret->specificity);
			$duplicates->bindParam(":platform_id",$secret->platform_id);
			$duplicates->bindParam(":platform_app_id",$secret->platform_app_id);
			$duplicates->execute();
			
			$duplicate_values_suspected = false;
			$duplicate_row=null;
			while(($tmp = $duplicates->fetch())) {
				$duplicate_row = $tmp;
				$social_check = $db->prepare("SELECT sn_id, sn_type FROM Bindings WHERE object_id=:oid");
				$social_check->bindParam(":oid",$duplicate_row['object_id']);
				$social_check->execute();
				$social_check_res = array();
				while($social_check_row = $social_check->fetch()) {
					$social_check_res[$social_check_row['sn_type']] = array('sn_id' => $social_check_row['sn_id']);
				}
				Log::write("Duplicate row checks: type=" . $secret->type . " algo=" . $secret->algorithm . " sensitive=" . $secret->sensitivity . " specific=" . 
					$secret->specificity . " sn_data=" . var_export($sn_data,true));
				Log::write("Compare: type=" . $duplicate_row['secret_type'] . " algo=" . $duplicate_row['secret_algorithm'] . " sensitive=" . 
					$duplicate_row['sensitivity'] . " specific=" . $duplicate_row['specificity'] . " sn_data=" . var_export($social_check_res,true));
				Log::write("Social data intersection: " . var_export(array_uintersect($sn_data,$social_check_res,'compareDeepValue'),true));
				if(($duplicate_row['secret_type'] == $secret->type) && ($duplicate_row['secret_algorithm'] == $secret->algorithm) && ($duplicate_row['platform_id'] == $secret->platform_id) && 
    					($duplicate_row['platform_app_id'] == $secret->platform_app_id) && 
    					($duplicate_row['sensitivity'] == $secret->sensitivity) && ($duplicate_row['specificity'] == $secret->specificity) && 
                        (count(array_uintersect($sn_data,$social_check_res,'compareDeepValue')) > 0)) {
					$duplicate_values_suspected = true;
					Log::write("Duplicates suspected with object=" . $duplicate_row['object_id']);
                    break;
				}
			}

			$duplicates = null;
			Log::write("Duplicate row: " . var_export($duplicate_row,true));

			$object_id = -1;
			if($duplicate_values_suspected && ($duplicate_row['specificity'] != Specificity::USER_SPECIFIC)) {
				$ids[$i] = array("local-id" => $secret->local_id, "object-id" => $duplicate_row['S.object_id']);
				continue;
			} else {
				if($duplicate_values_suspected) {
					Log::write("Calling update instead of insert. It's user-specific data");
					$stm1 = $db->prepare("UPDATE SecretInfo SET secret_type=:type, secret_algorithm=:algo, secret_value=:value, description=:descr, validity=:expires, secret_ts=:created, active=:active, " .
                                                " sensitivity=:sensitive, specificity=:specific, platform_id=:platform_id, platform_app_id=:platform_app_id WHERE object_id=:oid");
					$stm1->bindParam(":oid",$duplicate_row['object_id']);
					$object_id = $duplicate_row['object_id'];
				} else {
					$stm1 = $db->prepare("INSERT OR REPLACE INTO SecretInfo (secret_type, secret_algorithm, secret_value, description, validity, secret_ts, active, sensitivity, specificity, platform_id, " .
					"platform_app_id) VALUES(:type,:algo,:value,:descr,:expires,:created,:active,:sensitive,:specific,:platform_id,:platform_app_id)");
				}
				$stm1->bindParam(":type",$secret->type);
				$stm1->bindParam(":algo",$secret->algorithm);
				$stm1->bindParam(":value",$secret->value);
				$stm1->bindParam(":descr",$secret->description);
				$stm1->bindParam(":expires",$secret->expires);
				$stm1->bindParam(":created",$secret->created);
				$stm1->bindParam(":active",$secret->active);
				$stm1->bindParam(":sensitive",$secret->sensitivity);
				$stm1->bindParam(":specific",$secret->specificity);
				$stm1->bindParam(":platform_id",$secret->platform_id);
				$stm1->bindParam(":platform_app_id",$secret->platform_app_id);
				$stm1->execute();
				$stm1=null;

				if(!$duplicate_values_suspected) {
            		$stm_2 = $db->prepare("SELECT last_insert_rowid()");
            		$stm_2->execute();
            		$object_id = $stm_2->fetchColumn();
					$stm_2=null;
            		Log::write("Created object ID for " . $secret->description . ": $object_id");
					$ids[$i] = array("local-id" => $secret->local_id, "object-id" => $object_id);
				} else {
					$ids[$i] = array("local-id" => $secret->local_id, "object-id" => $duplicate_row['object_id']);
				}

				Log::write("Selected social information: " . var_export($sn_data,true));
				
				if(!$duplicate_values_suspected) {
					$stm3 = $db->prepare("INSERT INTO Bindings (object_id, assertedby, sn_id, sn_type, binding_type) VALUES(:oid,:asserted,:sn_id,:sn_type,:binding_type)");
				} else {
					$bid_stm = $db->prepare("SELECT bid, sn_id, sn_type FROM Bindings WHERE oid=:oid");
					$bid_stm->bindParam(":oid",$object_id);
					$bid_stm->execute();
					while($row = $bid_stm->fetch()) {
						$item = $sn_data[$row['sn_type']];
						Log::write("Item: " . var_export($item,true));
						$item['bid'] = $row['bid'];
						$sn_data[$row['sn_type']] = $item;
					}
					Log::write("Updated social data: " . var_export($sn_data,true));
					$bid_stm = null;
					$stm3 = $db->prepare("UPDATE Bindings SET object_id=:oid, assertedby=:asserted, sn_id=:sn_id, sn_type=:sn_type, binding_type=:binding_type WHERE bid=:bid");
				}

				foreach($sn_data as $sn_type => $sn) {
					if(!array_key_exists($sn_type,$tokens)) {
						Log::write("Although user is registered for social network: $sn_type, no token is provided for it. Omitting this social network");
						continue;
					}
					Log::write("Setting bindings for identity: " . $sn_type . " " . var_export($sn,true));
					if($duplicate_values_suspected && array_key_exists('bid',$sn)) {
						$stm3->bindParam(":bid",$sn['bid']);
					} else if($duplicate_values_suspected) {
						continue;
                    }
					Log::write("Social identity: " . var_export($sn,true));
					Log::write("Object id: $object_id");
					Log::write("Stm3: " . var_export($stm3,true));
					$stm3->bindParam(":oid",$object_id);
					if($secret->share != "User-asserted") {
						$zero_id = 0;
						$stm3->bindParam(":asserted",$zero_id);
					} else {
						$stm3->bindParam("asserted",$ps_id);
                    }
					$stm3->bindParam(":binding_type",$secret->binding_type);
					$stm3->bindParam(":sn_id",$sn['sn_id']);
					$stm3->bindParam(":sn_type",$sn_type);
					$stm3->execute();
					$stm3=null;
					Log::write("Object with ID=" . $object_id . " inserted into database");
				
					if(!$duplicate_values_suspected) {
            			$stm_4 = $db->prepare("SELECT last_insert_rowid()");
            			$stm_4->execute();
            			$bid = $stm_4->fetchColumn();
						$sn['bid'] = $bid;
						$stm_4=null;
						Log::write("Created binding ID=" . $bid);
					}
					Log::write("Calling setPolicy for user with ID: " . var_export($sn_data,true));
					
					$friends = null;
					if($sn_type == 1) { // FACEBOOK
						Log::write("Getting Facebook friends: sn=" . var_export($sn,true) . " tokens: " . var_export($tokens,true)); 
						$friends = FacebookHandler::getFriends($sn['sn_id'], $tokens[$sn_type]);
					} else if($sn_type == 2) { // LINKEDIN
						$friends = LinkedInHandler::getContacts($tokens[$sn_type]);
					}

					if(setPolicy($db, $sn['bid'], $sn_type, $sn['sn_id'], $secret->share, $friends) == -1) {
						Log::write("Store secrets. Error while setting sharing policies");
						$db->rollBack();
						return -1;
					}
				}
			}
		}

		$db->commit();
		return $ids;
	} catch(PDOException $e) {
		Log::write("Store secrets exception: " . $e->getMessage());
		return -1;
	} catch(FacebookApiException $e) {
		Log::write("Facebook API exception: " . $e->getMessage());
		$db->rollBack();
		return -1;
	}
}

/*!
 * Remove the relations that belongs to the old policy but not 
 * the new policy, or the relations that doesn't belong to the policy 
 * anymore, such as when users do unfriend.
 */
function removeOldRelations($db, $bid, $sn_type, $sn_id, 
									$oldList, $newList, $oldPolicy, 
										$platform_id, $platform_app_id) {
	try {
		Log::write("Removing old relations");

		// delete all the relation from the given binding
		deleteAllowedViewersByBid($db, $bid);

		// look for the ones which is not in the new member list anymore
		$toDeleteFriends = array();
		foreach ($oldList as $o) {
			$found = false;
			foreach ($newList as $n) {
				if ($o['sn_id'] == $n['sn_id']) {
					$found = true;
					break;
				}
			}
			if ($found === false) {
				array_push($toDeleteFriends, $o);
			}
		}

		if (count($toDeleteFriends) == 0) {
			Log::write("The friends list is still the same");
			return 0;
		}
		Log::write("There are changes in the friends list: " . var_export($toDeleteFriends, true));

		// delete direct relationship
		foreach ($toDeleteFriends as $i) {
			$friend_id = $i['sn_id'];
			Log::write("Deleting relation from user: " . $friend_id);

			deleteOneWayRelation($db, $sn_id, $friend_id, $sn_type, $oldPolicy, 
										ViewerRelation::DIRECT_FRIEND,
											$platform_id, $platform_app_id);
		}

		// delete friends of friends relationship if enabled
		if (FOF_ENABLED && $oldPolicy == SharingPolicy::FRIENDS) {
			if (removeOldFofRelation($db, $sn_id, $sn_type, $oldPolicy,
												$platform_id, $platform_app_id) == -1){
				return -1;
			}
		}

		return 0;

	} catch (Exception $e) {
        Log::write('RemoveOldRelation Exception: ' . $e->getMessage());
        return -1;
    }
}
			

function setPolicy($db, $bid, $sn_type, $sn_id, $policy, $friends) {
	Log::write("setPolicy() social info: " . var_export($sn_id,true) . " friends: " . var_export($friends,true));

	if($friends == null) { 
        return -1;
    }

	try {
		// get platform_id and platform_app_id from the initiator's secret
	    $platformInfo = getPlatformInfo($db, $bid);
	    if ($platformInfo !== false) {
	        $platform_id = $platformInfo['platform_id'];
	        $platform_app_id = $platformInfo['platform_app_id'];
	    } else {
	        Log::write("Unable to find the secret info with bid: " . $bid);
	        return -1;
	    }
		// get the new list of friends according to the new policy
		if($policy == SharingPolicy::FRIENDS) {
			$tmpList = $friends;			
		} elseif ($policy == SharingPolicy::USER_ASSERTED 
					or $policy == SharingPolicy::ONLY_ME) {
			$tmpList = null;
		} else {
            $group_id = $policy;
            $tmpList = FacebookHandler::getGroupMembers($group_id, $user_token);
		}

		// change the keys' name of the friends list returned from facebook
		// to match the fields in the database
		Log::write("Before: " . var_export($tmpList, true));
		$newList = array();
		foreach ($tmpList as $fObj) {
			array_push($newList, array("sn_id" => $fObj->id,
										"sn_name" => $fObj->name));
		}
		$tmpList = null;
		Log::write("After: " . var_export($newList, true));

		// delete old data of the old policy
		$oldPolicy = getPolicy($db, $bid);
		if ($oldPolicy !== false) {
			$oldList = getLocalMemberList($db, $sn_id, $sn_type, $oldPolicy, 
												$platform_id, $platform_app_id);
			
			if (count($oldList) > 0 && $newList != null) {
				if (removeOldRelations($db, $bid, $sn_type, $sn_id, 
										$oldList, $newList, $oldPolicy, 
											$platform_id, $platform_app_id) == -1) {
					return -1;
				}
			}
		}

		// update AclInfo of the binding with the new policy
		createNewAclInfoEntry($db, $bid, $policy);
		Log::write("AclInfo table updated: bid=$bid policy=" . $policy);
		
		// distribute new data
		if ($newList != null) {
			foreach ($newList as $f) {
	            createNewAllowedViewerEntry($db, $bid, $f['sn_id'], $sn_type, 
	            								ViewerRelation::DIRECT_FRIEND);                
			}

	        // distribute secret info if FOF is enabled
	        if (FOF_ENABLED && $policy == SharingPolicy::FRIENDS) {
	            if (distributeSecret($db, $bid, $sn_id, $sn_type, 
	            					$platform_id, $platform_app_id, $newList) == -1) {
	            	return -1;
	            }
	        }
		}

		// adding myself
        createNewAllowedViewerEntry($db, $bid, $sn_id, $sn_type, ViewerRelation::DIRECT_FRIEND);

		Log::write("setPolicy returns 0");
		return 0;
	} catch(PDOException $e) {
    	Log::write('setPolicy exception : ' . $e->getMessage());
    	return -1;
	}
}


function updateSecrets($db, $ps_id, $secrets, $tokens) {
	try {
		Log::write("updateSecrets data=" . var_export($secrets,true));

        $confirm = array();
        $db->beginTransaction();

        if (checkUserExist($ps_id, $db) == false) {
            return -1;
        }

		for($i=0;$i<count($secrets);$i++) {
			$secret = $secrets[$i];
			$verify = $db->prepare("SELECT user_id, sn_id, sn_type FROM SnInfo WHERE sn_id IN (SELECT sn_id FROM Bindings WHERE object_id=:oid)");
			$verify->bindParam(":oid",$secret->object_id);
			$verify->execute();
			$social = $verify->fetchAll();
			$verify=null;
			
			$social_verification=true;
			foreach($social as $id) {
				if($ps_id != $id['user_id']) {
					Log::write("Object to update doesn't match registered user. Skipping secret");
					$confirm[] = array("object-id" => $secret->object_id);
					$social_verification=false;
					break;
				}
			}
			if(!$social_verification) {
				continue;
			}

			$ts_update = $db->prepare("UPDATE SecretInfo SET secret_ts=datetime('now') WHERE object_id=:oid ");
			$ts_update->bindParam(":oid",$secret->object_id);
			$ts_update->execute();
			$ts_update=null;

			$update_str = "";
			Log::write("Current update_str var: " . $update_str);
			if(isset($secret->value)) {
				$update_str .= ("secret_value='" . $secret->value . "', ");
				Log::write("Current update_str var: " . $update_str);
			}
			if(isset($secret->type)) {
				$update_str .= ("secret_type=" . $secret->type . ", ");
				Log::write("Current update_str var: " . $update_str);
			}
			if(isset($secret->algorithm)) {
				$update_str .= ("secret_algorithm='" . $secret->algorithm . "', ");
				Log::write("Current update_str var: " . $update_str);
			}
            if(isset($secret->description)) {
                $update_str .= ("description='" . $secret->description . "', ");
            	Log::write("Current update_str var: " . $update_str);
			}
			if(isset($secret->created)) {
                $update_str .= ("secret_ts=datetime('" . $secret->created . "'), ");
            	Log::write("Current update_str var: " . $update_str);
			}
            if(isset($secret->expires)) {
                $update_str .= ("validity=datetime('" . $secret->expires . "'), ");
            	Log::write("Current update_str var: " . $update_str);
			}
			Log::write("Final update_str var: " . $update_str);
			if(strlen($update_str) > 3) {
				Log::write("Final coma: " . $update_str[strlen($update_str)-2] . " length: " . strlen($update_str));
				if(strlen($update_str) > 0 && $update_str[strlen($update_str)-2] == ",") {
					Log::write("Replacing final coma");
					$update_str = substr_replace($update_str,"",-2);
				}
			}
			Log::write("Corrected final update_str var: " . $update_str);
			if($update_str != "") {
				$secret_update = $db->prepare("UPDATE SecretInfo SET " . $update_str . " WHERE object_id=:oid");
				$secret_update->bindParam(":oid",$secret->object_id);
				$secret_update->execute();
				$secret_update=null;
			}

			if(!isset($secret->share)) {
				$secret->share = "Friends";
			}
			$binding = $db->prepare("SELECT bid, sn_id, sn_type FROM Bindings WHERE object_id=:oid");
			$binding->bindParam(":oid",$secret->object_id);
				$binding->execute();
			$bids = $binding->fetchAll();
			$binding=null;
			Log::write("updateSecrets: bid=$bids policy=" . $secret->share . " Calling setPolicy()");
			foreach($bids as $b) {
				if(array_key_exists($b['sn_type'],$tokens)) {
					$friends = null;
					if($b['sn_type'] == 1) { // FACEBOOK
						Log::write("Getting Facebook friends: sn=" . var_export($sn,true) . " tokens: " . var_export($tokens,true));
						$friends = FacebookHandler::getFriends($b['sn_id'], $tokens[$b['sn_type']]);
					} else if($sn_type == 2) { // LINKEDIN
						$friends = LinkedInHandler::getContacts($tokens[$sn_type]);
					}
                	if(setPolicy($db, $b['bid'], $b['sn_type'], $b['sn_id'], $secret->share, $friends) == -1) {
						Log::write("Store secrets. Error while setting sharing policies");
                                        	//$db->rollBack();
						return -1;
					}
				}
			}
		}

        $db->commit();

        return $confirm;

    } catch(PDOException $e) {
        Log::write('updateSecrets exception : ' . $e->getMessage());
        return -1;
    } catch(FacebookApiException $e) {
        Log::write('Facebook API exception: ' . $e->getMessage());
        $db->rollBack();
        return -1;
    }
}

function removeSecrets($db, $ps_id, $secrets, $token) {
	try {
		Log::write("removeSecrets data=" . var_export($secrets,true));
        $db->beginTransaction();

        for($i=0;$i<count($secrets);$i++) {
            $secret = $secrets[$i];

            // delete the secret info
            deleteSecretInfo($db, $secret);

			$bindings = $db->prepare("SELECT bid FROM Bindings WHERE object_id=:oid AND assertedby=0");
			$bindings->bindParam(":oid",$secret);
			$bindings->execute();

			while(($row = $bindings->fetch())) {
                deleteAllowedViewersByBid($db, $row['bid']);
                deleteAclInfo($db, $row['bid']);
			}

			$bindings_delete = $db->prepare("DELETE FROM Bindings WHERE object_id=:oid AND assertedby=0");
			$bindings_delete->bindParam(":oid",$secret);
			$bindings_delete->execute();
        }

        $db->commit();

        return 0;

	} catch(PDOException $e) {
        Log::write('removeSecret exception : ' . $e->getMessage());
        $db->rollBack();
        return -1;
	}
}

/*!
 * Retrieve all the available secrets for the given user
 */
function getAvailableSecrets($db, $ps_id, $submitted_tokens) {
	try {
		$secrets = array();
		$db->beginTransaction();

		$social_info = array();

        $snInfoRows = getSnInfoByPsId($db, $ps_id);
		$used_networks = array_keys((array)$submitted_tokens);
		Log::write("Submitted tokens: " . var_export($submitted_tokens,true) . " network ids: " . var_export($used_networks,true));

        foreach ($snInfoRows as $row) {
			Log::write("Checking if " . $row['sn_type'] . " is in: " . var_export($used_networks,true));
			if (in_array($row['sn_type'], $used_networks)) {
				$social_info[$row['sn_type']] = $row['sn_id'];
			}
		}
		$social_query=null;
		Log::write("Obtained social identities: " . var_export($social_info,true));
		
		$sn_id_set = "'" . implode("','",array_values($social_info)) . "'";
		$sn_type_set = implode(',',array_keys($social_info));
		Log::write("SN id set: $sn_id_set sn type set: $sn_type_set");

		$query = $db->prepare("SELECT DISTINCT secret_type, secret_algorithm, secret_value, 
                                                description, validity, secret_ts, active, 
                                                platform_id, platform_app_id, sensitivity, specificity, 
                                                B.binding_type, B.object_id, A.policy,
                                                B.sn_id, B.sn_type, SN.sn_name, 
                                                AV.relation 
                                FROM SecretInfo S NATURAL JOIN Bindings B 
                                    JOIN SnInfo SN ON (B.sn_id=SN.sn_id AND B.sn_type=SN.sn_type) 
                                    JOIN AclInfo A ON (B.bid=A.bid),
                                    AllowedViewers AV
                                WHERE (AV.bid=B.bid) AND (AV.sn_id IN ($sn_id_set)) AND (AV.sn_type IN ($sn_type_set))");
		$query->execute();
		
		$retrieved_objects = array();
        $data = $query->fetchAll();
        foreach ($data as $row) {
            if ($row['relation'] == ViewerRelation::DIRECT_FRIEND) {
                if(array_key_exists($row['object_id'],$retrieved_objects)) {
                    $object = $retrieved_objects[$row['object_id']];
                    $ids = $object['social-identities'];
                    $ids[] = createSocialIdentityObjectForClient($row['sn_id'], $row['sn_name'], $row['sn_type']);;
                    $object['social-identities'] = $ids;
                    $retrieved_objects[$row['object_id']] = $object;
                } else {
                    $object = createSecretInfoObjectForClient($row, $social_info, false);
                    $retrieved_objects[$row['object_id']] = $object;
                }    
            }
		}
		Log::write("Number of direct secret: " . count($retrieved_objects));
        // put the retrieved object to the final list
        foreach($retrieved_objects as $i) {
            array_push($secrets, $i);
        }

        // If FOF is enabled, get the hash of SecretInfo of direct friends and friends of friends
        if (FOF_ENABLED) {
        	$fofSecrets = getAvailableFoFSecrets($social_info, $secrets, $data);
        	Log::write("Number of hash secret: " . count($fofSecrets));
            $secrets = array_merge($secrets, $fofSecrets);
        }

        Log::write("Number of retrieved objects: " . count($secrets));

		$query=null;
		$db->commit();
		return $secrets;
	} catch(PDOException $e) {
		Log::write("getAvailableSecrets exception: " . $e->getMessage());
		return -1;
	}
}

/*date_default_timezone_set("Europe/Helsinki");
$db = openDb();

$tokens = array(
	"1" => "12231232"
);
$ps_id = 1;

$secret = getAvailableSecrets($db, $ps_id, $tokens);
echo count($secret) . ""*/
//var_dump($secret);

/*$db = openDb();
$bid = 4;
$sn_id = "100005234659320";
$policy = SharingPolicy::FRIENDS;
$friends = array (
	(object) array("id" => "100007736419092", "name" => "Thanh Tien Bui")
); 
setPolicy($db, $bid, 1, $sn_id, $policy, $friends);*/

?>
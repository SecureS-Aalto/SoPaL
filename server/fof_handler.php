<?php

require_once './dbhelper.php';
require_once './constants.php';
require_once './tools.php';

/*!
 * Create fake SecretInfo for the given user
 */
function createFakeSecretInfo($db, $createNewSnInfo, $sn_type, $sn_id, $sn_name, 
                                        $platformId, $platformAppId) {
    // create new SnInfo if needed
    if ($createNewSnInfo) {
        createNewSnInfoEntry($db, $sn_type, $sn_id, $sn_name, null, "FALSE");        
    }
    
    // create new SecreInfo
    $objectId = createNewSecretInfoEntry($db, 
                                        DataType::CROWDAPP, 
                                        Algorithm::PLAIN, 
                                        NonceGenerator::newNonce(),
                                        Description::SERVER_GENERATED, 
                                        null, 
                                        DataSensitivity::PRIVATE_SENS, 
                                        Specificity::USER_SPECIFIC, 
                                        $platformId, 
                                        $platformAppId);
                    
    // create new bindings
    $bid = createNewBindingEntry($db, 
                                    $objectId, 
                                    0, // asserted by
                                    $sn_id,
                                    $sn_type,
                                    BindingType::USER_ASSERTED); //binding type

    // create new aclinfo for the new binding
    createNewAclInfoEntry($db, $bid, SharingPolicy::FRIENDS);

    return $bid;
}

/*!
 * Distribute the SecretInfo if FOF is enabled
 */
function distributeSecret($db, $init_bid, $init_sn_id, $init_sn_type, 
                                $platform_id, $platform_app_id, $friends ) {

    Log::write("Distribute Secret");
    try {
        // distribute the secret info to the direct friends
        distributeDirectFriends($db, $init_bid, $init_sn_id, $init_sn_type, 
                                        $platform_id, $platform_app_id, $friends);
        // get the list of friends of friends
        $friendsOfFriends = getFriendsOfFriends($db, $init_sn_id, $init_sn_type, 
                                                        $platform_id, $platform_app_id, $friends);
        // distribute the secret info to the friends of friends
        distributeFriendsOfFriends($db, $init_bid, $init_sn_id, $init_sn_type, 
                                            $platform_id, $platform_app_id, $friendsOfFriends);

        //Update the existed active users with the friends list of the new user
        updateExistedActiveUsers($db, $init_bid, $init_sn_type, 
                                        $platform_id, $platform_app_id, $friends);

        return 0;
    } catch (Exception $e) {
        Log::write('FOF Distribute Secret Exception: ' . $e->getMessage());
        return -1;
    }
}

/*!
 * Update the existed active users with the friends list of the new user
 */
function updateExistedActiveUsers($db, $init_bid, $init_sn_type, 
                                    $platform_id, $platform_app_id, $friends) {
    try {
        Log::write("FOF: Updating existed active users");

        $tmp = array_map(function($item) { return $item['sn_id']; }, $friends);
        $friends_id = "'" . implode("','",$tmp) . "'";

        $query = $db->prepare("SELECT sn_id from SnInfo 
                                    WHERE sn_type=:sn_type 
                                        AND sn_id in ($friends_id)
                                        AND active_user=:active_user");
        $query->bindParam(":sn_type", $init_sn_type);
        $query->bindValue(":active_user", "TRUE");
        $query->execute();
        $active_friends = $query->fetchAll();
        if ($active_friends === false || count($active_friends) == 0) {
            return;
        }

        $query->bindValue(":active_user", "FALSE");
        $query->execute();
        $unactive_friends = $query->fetchAll();    
        if ($unactive_friends === false || count($unactive_friends) == 0) {
            return;
        }
        
        for ($i = 0; $i < count($unactive_friends); $i++) {
            $uf_id = $unactive_friends[$i]['sn_id'];
            $unactive_friends[$i]['bid'] = getSecretInfo($db, $uf_id, $init_sn_type, 
                                                $platform_id, $platform_app_id);
        }    

        Log::write(var_export($active_friends, true));
        Log::write(var_export($unactive_friends, true));

        foreach($active_friends as $af) {
            $af_id = $af['sn_id'];
            $af_bid = getSecretInfo($db, $af_id, $init_sn_type, $platform_id, $platform_app_id);
            Log::write("AF BID: " . $af_bid);
            if ($af_bid == -1) {
                continue;
            }
            foreach ($unactive_friends as $uf) {
                $uf_id = $uf['sn_id'];
                $uf_bid = $uf['bid'];

                $query = $db->prepare("SELECT COUNT(*) from AllowedViewers 
                                                        where bid=:bid AND sn_id=:sn_id 
                                                            AND sn_type=:sn_type");
                $query->bindParam(":sn_type", $init_sn_type);
                $query->bindParam(":bid", $af_bid);
                $query->bindParam(":sn_id", $uf_id);
                $query->execute();

                $alreadyConnected = $query->fetchColumn();    
                Log::write("UF: " . $uf_id . " " . $alreadyConnected);
                if ($alreadyConnected == 0) {
                    // create new AllowedViewer entry for the friend 
                    createNewAllowedViewerEntry($db, $af_bid, $uf_id, $init_sn_type, 
                                                            ViewerRelation::FRIEND_OF_FRIEND);    

                    // create allowedViewer for the initiator
                    createNewAllowedViewerEntry($db, $uf_bid, $af_id, $init_sn_type, 
                                                            ViewerRelation::FRIEND_OF_FRIEND);    
                }
            }            
        }

    } catch (Exception $e) {
        throw $e;
    }
}

/*!
 * Distribute SecretInfo to the direct friends of the given user
 */
function distributeDirectFriends($db, $init_bid, $init_sn_id, $sn_type, 
                                        $platform_id, $platform_app_id, 
                                        $friends) {
    try {
        Log::write("FOF: Distributing to direct friends");
        foreach ($friends as $f) {
            $friend_id = $f['sn_id'];
            $friend_name = $f['sn_name'];

            // get the SnInfo of the friend
            $friend_sn_info = getSnInfo($db, $friend_id, $sn_type);

            // if the SnInfo doesn't exist or it is unactive user
            if ($friend_sn_info === null || $friend_sn_info['active_user'] == "FALSE") {
                // find the secretinfo with the same platform_app and platform_id if user existed
                if ($friend_sn_info !== null) {
                    $friend_bid = getSecretInfo($db, $friend_id, $sn_type, 
                                                        $platform_id, $platform_app_id);
                    $create_new_sn_info = false; 
                } else {
                    $friend_bid = -1;
                    $create_new_sn_info = true; 
                }
                // create fake record for the user, if it doesn't exist
                if ($friend_bid == -1) {
                    $friend_bid = createFakeSecretInfo($db, $create_new_sn_info, 
                                                            $sn_type, $friend_id, $friend_name, 
                                                            $platform_id, $platform_app_id);
                }
                // create allowedViewer for the initiator
                createNewAllowedViewerEntry($db, $friend_bid, $init_sn_id, $sn_type, 
                                                        ViewerRelation::DIRECT_FRIEND);    
            }
        }
    } catch (Exception $e) {
        throw $e;
    }
}

/*!
 * Distribute SecretInfo to the friends of friends of the given user
 */
function distributeFriendsOfFriends($db, $init_bid, $init_sn_id, $sn_type, 
                                            $platform_id, $platform_app_id, 
                                            $friendsOfFriends) {
    try {
        Log::write("FOF: Distributing to friends of friends");
        foreach ($friendsOfFriends as $f) {
            $friend_id = $f['sn_id'];

            // create new AllowedViewer entry for the friend 
            createNewAllowedViewerEntry($db, $init_bid, $friend_id, $sn_type, 
                                                    ViewerRelation::FRIEND_OF_FRIEND);    

            // find the secretinfo with the same platform_app and platform_id
            $friend_bid = getSecretInfo($db, $friend_id, $sn_type, 
                                                $platform_id, $platform_app_id);

            // create allowedViewer for the initiator
            createNewAllowedViewerEntry($db, $friend_bid, $init_sn_id, $sn_type, 
                                                    ViewerRelation::FRIEND_OF_FRIEND);    
        }
    } catch (Exception $e) {
        throw $e;
    }
}

/*!
 * Get the list of friends of friends of the given user
 */
function getFriendsOfFriends($db, $init_sn_id, $sn_type, 
                                    $platform_id, $platform_app_id, $friends) {
    try {
        $friends_of_friends = array();

        foreach ($friends as $f) {
            $friend_id = $f['sn_id'];

            // get the SnInfo of the direct friend
            $friend_sn_info = getSnInfo($db, $friend_id, $sn_type);

            // check if he is active user, get the list of his friends from the database
            if ($friend_sn_info !== null && $friend_sn_info['active_user'] == "TRUE") {
                $tmp_fof = getLocalMemberList($db, $friend_id, $sn_type, SharingPolicy::FRIENDS, 
                                                            $platform_id, $platform_app_id);

                // merge with the list of fof
                $friends_of_friends = array_merge($friends_of_friends, $tmp_fof);
            }
        }

        // remove duplicates
        $friends_of_friends = array_unique($friends_of_friends, SORT_REGULAR);

        // remove the same people in the direct friends list
        // sort the direct friends list 
        if (count($friends) > 0) {
            usort($friends, "cmpId");
        }

        // get only the ones which are not in the direct friends list
        $final_list = getDifferentMembers($friends_of_friends, $friends);
        // remove the init_sn_id from the list if any
        removeSpecificMember($final_list, $init_sn_id);

        return $final_list;

    } catch (Exception $e) {
        throw $e;
    }
}

/*!
 * Get the secrets for FOF handler including: 
 * 		- the hash of the secret of direct friends
 * 		- the hash of the secret of friends of friends
 */
function getAvailableFoFSecrets($social_info, $directFriendsSecrets, $data) {
	$secrets = array();

	// calculate the hash value of SecretInfo of direct friends
    foreach($directFriendsSecrets as $i) {
        $hashObject = $i;
        $hashObject['algorithm'] = Algorithm::PLAIN_SHA1;
        $hashObject['value'] = sha1($i['value']);
        array_push($secrets, $hashObject);
    }            
    Log::write("Number of hash direct secrets: " . count($secrets));

    $tmpList = array();
    // calculate the hash value of SecretInfo of friends of friends
    foreach ($data as $row) {
        if ($row['relation'] == ViewerRelation::FRIEND_OF_FRIEND) {

        	if(array_key_exists($row['object_id'], $tmpList)) {
                $object = $tmpList[$row['object_id']];
                $ids = $object['social-identities'];
                $ids[] = createSocialIdentityObjectForClient($row['sn_id'], $row['sn_name'], $row['sn_type']);;
                $object['social-identities'] = $ids;
                $tmpList[$row['object_id']] = $object;
            } else {
            	$row['secret_algorithm'] = Algorithm::PLAIN_SHA1;
	            $row['secret_value'] = sha1($row['secret_value']);

	            $hashObject = createSecretInfoObjectForClient($row, $social_info, true);
	            $tmpList[$row['object_id']] = $hashObject;
            }    
        }
    }	

    Log::write("Number of hash fof secrets: " . count($tmpList));
    // put the retrieved object to the final list
    foreach($tmpList as $i) {
        array_push($secrets, $i);
    }

    return $secrets;
}

/*!
 * Remove the relations that belongs to the old policy but not 
 * the new policy, or the relations that doesn't belong to the policy 
 * anymore, such as when users do unfriend.
 */
function removeOldFofRelation($db, $sn_id, $sn_type, $oldPolicy,
                                            $platform_id, $platform_app_id) {
    try {
        $db->beginTransaction();

        Log::write("Removing old fof relations");
        $query = $db->prepare("DELETE FROM AllowedViewers
                                WHERE sn_id=:my_id AND sn_type=:sn_type AND relation=:relation
                                    AND bid in (SELECT B.bid FROM Bindings B NATURAL JOIN AclInfo A,
                                                                SecretInfo S
                                                    WHERE B.sn_type=:sn_type AND A.policy=:policy
                                                            AND S.platform_id=:platform_id
                                                            AND S.platform_app_id=:platform_app_id)"); 
        $query->bindParam(":my_id", $sn_id);
        $query->bindParam(":sn_type", $sn_type);
        $query->bindParam(":policy", $oldPolicy);
        $query->bindValue(":relation", ViewerRelation::FRIEND_OF_FRIEND);
        $query->bindParam(":platform_id", $platform_id);
        $query->bindParam(":platform_app_id", $platform_app_id);
        $query->execute();

        return 0;

    } catch (Exception $e) {
        Log::write('Exception: ' . $e->getMessage());
        $db->rollBack();
        return -1;
    }
}

/*$db = openDb();
$init_bid = -1;
$init_sn_type = 1;
$init_sn_id = "100003189208717";
$platform_id = 1;
$platform_app_id = "com.futurice.scampi.peopleinhere;308201e53082014ea0030201020204503d2e6f300d06092a864886f70d01010505003037310b30090603550406130255533110300e060355040a1307416e64726f6964311630140603550403130d416e64726f6964204465627567301e170d3132303832383230343734335a170d3432303832313230343734335a3037310b30090603550406130255533110300e060355040a1307416e64726f6964311630140603550403130d416e64726f696420446562756730819f300d06092a864886f70d010101050003818d0030818902818100bb735103883a67f89963a8f97fa869c19a8e393f079f2941d7e7d7b65413e249a5bce4549d3c01abcc0b5655aaee5376c23c1661570fe2b4c6f58aab0c81653bc96e6503139ae97a5cf38aa7ec42689db673deb5ca0ef72600cc1f6984aa5ba01c79a8d6cc43af78c7eb3990a20041312899c8338dd6d901e13dcc0e3505b3030203010001300d06092a864886f70d010105050003818100a7239c88a4fc2430d586ccc7462d6bf3469a2fb69020d6847e46eed9aa3cdfd2a7ed1e50a44fbe6d837fda3b0929ce81a932aaee0a8d2545146b6da946f692890448096d83e19473448f6cde586d162265e1b5e40c7b983e26c080bfc135aa0632c3a44e3c2b7a7787218fbcc3cc9149ae4450bfc94091273c9cdf087746d82a";
$friends = array(
    array('sn_id' => "100000749771564"),
    array('sn_id' => "100002635894528")
);

distributeSecret($db, $init_bid, $init_sn_id, $init_sn_type, 
                                $platform_id, $platform_app_id, $friends );*/


/*$f = getLocalFriendsList($db, "1183488523", 1, 1, "org.sesy.tetheringapp;3082030d308201f5a00302010202047a8344f3300d06092a864886f70d01010b05003037310b30090603550406130255533110300e060355040a1307416e64726f6964311630140603550403130d416e64726f6964204465627567301e170d3134303632363131313933325a170d3434303631383131313933325a3037310b30090603550406130255533110300e060355040a1307416e64726f6964311630140603550403130d416e64726f696420446562756730820122300d06092a864886f70d01010105000382010f003082010a0282010100c6c2122e9679860209ceff128d6cb77a12ae7251bccf0fb79d205b500bee89ed04ecfae474002fd92b5c817fdc5e2e2f002f28545eb504b9f5cc7475814ddfd4139e8b2b57d5dee234c4c2f0b8a113f2bcb0c8777429b98931f97e0f03b2a974bb3fa63b0ee7a4497b0abab11c166cde6495ba92fc24a129534528920c4ddcdbafb19066192d32a831c72e89dd9a4dd8cfec58c8bdf6c7a6ca56efd1b1727dc89ac4eb547269478d790469cf80183efbce8a55dcb4abe6c877972616e4a01aaefade1f311ec44a094fa6ad3c90f1d3080f489df936c9e1937a7780f1ec38230c0748f4cc352df8036117effa1fe3dfd8ba2698f5b2dcfab2d5e873d08b2d924d0203010001a321301f301d0603551d0e04160414221245cb6a30b478e2e348281f4b2f07a4f37cfc300d06092a864886f70d01010b0500038201010062bd0ab11d61ac26dd17c76213db17b0ee8246f9795d5b0b7396a7c5ef6defd2171088147ed53101977e7009d5fb84530c1ae9806976e79efdcd1cc6a3f1bb93f1b35bd71fc0593d6e1c901fa4539a1e9ca3b71bc698ece71265f7b228b58e73584bcd76f14926c3f35a79146cdbb8204e3ad0e2f4739527b3bf84b7c4af80d2fb2c9e82b17ce80cd51ed3e9017cff43b7dbf4aee638cfdd1f5c41865bf20ffe2bbf8a54e03ac51542589faa752253446e0fa9af58daaa6320bec35f884029258fdf482e229ed5af49b689090db2cc76ab23c249a365a218def5d1516d7dfece6d0164820d2a3c3a6fe367c08813777ce4439873de8590d9960fbfad2208d136");
var_dump($f);
echo $f[0]['sn_id'];*/

/*$db = openDb();
$friends = FacebookHandler::getFriends(1,
    "CAAGnoiUrYcoBAFKu5QKsZBcFRHhvVmZBvKLPEWk9axWE6aWTvVuBkq6eWDvFNcUtSLNhQKiAN0hIpZBTvWv53tC49s9yuwRoi6mXZC9iTERaqMVdbZCPqHrEB2lpySn7FjBsQrRTzZCKhAzNmbXTxDOVVUQUNJM8G34KkPSf99OZCOrAdruSvZB3p20KAIAKIvFIBEgykZC6c22SsUDgYlVhZAfdPqbOZBFeUDWDgQZADopnuwZDZD");

var_dump($friends);
$fof = getFriendsOfFriends($db, "100007736419092", 1, 
                                    1, "com.futurice.scampi.peopleinhere;3082030d308201f5a00302010202047a8344f3300d06092a864886f70d01010b05003037310b30090603550406130255533110300e060355040a1307416e64726f6964311630140603550403130d416e64726f6964204465627567301e170d3134303632363131313933325a170d3434303631383131313933325a3037310b30090603550406130255533110300e060355040a1307416e64726f6964311630140603550403130d416e64726f696420446562756730820122300d06092a864886f70d01010105000382010f003082010a0282010100c6c2122e9679860209ceff128d6cb77a12ae7251bccf0fb79d205b500bee89ed04ecfae474002fd92b5c817fdc5e2e2f002f28545eb504b9f5cc7475814ddfd4139e8b2b57d5dee234c4c2f0b8a113f2bcb0c8777429b98931f97e0f03b2a974bb3fa63b0ee7a4497b0abab11c166cde6495ba92fc24a129534528920c4ddcdbafb19066192d32a831c72e89dd9a4dd8cfec58c8bdf6c7a6ca56efd1b1727dc89ac4eb547269478d790469cf80183efbce8a55dcb4abe6c877972616e4a01aaefade1f311ec44a094fa6ad3c90f1d3080f489df936c9e1937a7780f1ec38230c0748f4cc352df8036117effa1fe3dfd8ba2698f5b2dcfab2d5e873d08b2d924d0203010001a321301f301d0603551d0e04160414221245cb6a30b478e2e348281f4b2f07a4f37cfc300d06092a864886f70d01010b0500038201010062bd0ab11d61ac26dd17c76213db17b0ee8246f9795d5b0b7396a7c5ef6defd2171088147ed53101977e7009d5fb84530c1ae9806976e79efdcd1cc6a3f1bb93f1b35bd71fc0593d6e1c901fa4539a1e9ca3b71bc698ece71265f7b228b58e73584bcd76f14926c3f35a79146cdbb8204e3ad0e2f4739527b3bf84b7c4af80d2fb2c9e82b17ce80cd51ed3e9017cff43b7dbf4aee638cfdd1f5c41865bf20ffe2bbf8a54e03ac51542589faa752253446e0fa9af58daaa6320bec35f884029258fdf482e229ed5af49b689090db2cc76ab23c249a365a218def5d1516d7dfece6d0164820d2a3c3a6fe367c08813777ce4439873de8590d9960fbfad2208d136", 
                                    $friends);
var_dump($fof);*/
?>
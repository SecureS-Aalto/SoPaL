<?php

require_once './facebook/src/facebook.php';
require_once './socialnetworks/facebookhandler.php';
require_once './socialnetworks/linkedinhandler.php';
require_once './constants.php';
require_once './config.php';
require_once './tools.php';

function openDb() {
	$db = new PDO('sqlite:' . DATABASE);
	$db->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
	return $db;
}

function createNewUserInfoEntry($db) {
    $db->exec("INSERT INTO UserInfo (num_sn) VALUES(1)");
    $query = $db->prepare("SELECT last_insert_rowid()");
    $query->execute();
    $psId = $query->fetchColumn();

    return $psId;
}

function createNewSecretInfoEntry($db, 
                            $secretType, $secretAlgo, $secretValue,
                            $description, $active, $sensitivity, $specificity, 
                            $platformId, $platformAppId) {
    $query = $db->prepare("INSERT OR REPLACE INTO SecretInfo 
                                        (secret_type, secret_algorithm, secret_value, 
                                        description, validity, secret_ts, active, 
                                        sensitivity, specificity, platform_id, platform_app_id) 
                                VALUES(:type, :algo, :value, :descr, :expires, :created, :active,
                                        :sensitive,:specific,:platform_id,:platform_app_id)");
    $query->bindValue(":type", $secretType);
    $query->bindValue(":algo", $secretAlgo);
    $query->bindValue(":value", $secretValue);
    $query->bindValue(":descr", $description);

    $curTime = round(microtime(true) * 1000); // in milliseconds
    $query->bindParam(":created", msToTimestamp($curTime));

    $expiredTime = $curTime + BEARER_CAPABILITY_LIFETIME;
    $query->bindParam(":expires", msToTimestamp($expiredTime));

    $query->bindValue(":active", $active);
    $query->bindValue(":sensitive", $sensitivity);
    $query->bindValue(":specific", $specificity);
    $query->bindValue(":platform_id", $platformId);
    $query->bindValue(":platform_app_id", $platformAppId);
    $query->execute();

    // get the id
    $query = $db->prepare("SELECT last_insert_rowid()");
    $query->execute();
    $objectId = $query->fetchColumn();
    
    return $objectId;
}

function getPolicy($db, $bid) {
    $query = $db->prepare("SELECT policy FROM AclInfo WHERE bid=:bid");
    $query->bindParam(":bid",$bid);
    $query->execute();
    $res = $query->fetchColumn();
    return $res;
}

function createNewAclInfoEntry($db, $bid, $policy) {
    $query = $db->prepare("INSERT OR REPLACE INTO AclInfo (bid,policy) VALUES (:bid,:policy)");
    $query->bindParam(":bid",$bid);
    $query->bindParam(":policy",$policy);
    $query->execute();
}

function createNewBindingEntry($db, 
                $objectId, $assertedBy, $sn_id, $sn_type, $bindingType) {
    $query = $db->prepare("INSERT OR IGNORE INTO Bindings 
                                    (binding_type, object_id, assertedby, sn_id, sn_type) 
                                VALUES(:binding_type, :oid, :asserted_by, :sn_id, :sn_type)");
    $query->bindParam(":oid",$objectId);
    $query->bindParam(":binding_type", $bindingType);
    $query->bindParam(":asserted_by", $assertedBy);
    $query->bindParam(":sn_type",$sn_type);
    $query->bindParam(":sn_id",$sn_id);
    $query->execute();

    $query = $db->prepare("SELECT last_insert_rowid()");
    $query->execute();
    $bid = $query->fetchColumn();
    
    return $bid;
}

function createNewAllowedViewerEntry($db, $bid, $sn_id, $sn_type, $relation) {
    
    $query = $db->prepare("INSERT OR IGNORE INTO AllowedViewers (bid, sn_id, sn_type, relation) 
                                            VALUES(:bid, :sn_id, :sn_type, :relation)");
    $query->bindParam(":bid",$bid);
    $query->bindParam(":sn_id", $sn_id);
    $query->bindParam(":sn_type",$sn_type); // 1 is for Facebook
    $query->bindParam(":relation", $relation);
    $query->execute();    
}

function deleteAllowedViewersByBid($db, $bid) {
    $query = $db->prepare("DELETE FROM AllowedViewers WHERE bid=:bid");
    $query->bindParam(":bid", $bid);
    $query->execute();
}

function deleteAclInfo($db, $bid) {
    $query = $db->prepare("DELETE FROM AclInfo WHERE bid=:bid");
    $query->bindParam(":bid", $bid);
    $query->execute();
}

function deleteSecretInfo($db, $oid) {
    $query = $db->prepare("DELETE FROM SecretInfo WHERE object_id=:oid");
    $query->bindParam(":oid", $oid);
    $query->execute();
}

function createNewSnInfoEntry($db, $sn_type, $sn_id, $sn_name, $userId, $active) {
    $query = $db->prepare("INSERT OR REPLACE INTO SnInfo (user_id, sn_type, sn_id, sn_name, active_user) 
                                    VALUES(:user_id, :sn_type, :sn_id, :sn_name, :active_user)");
    $query->bindParam(":user_id", $userId);
    $query->bindParam(":sn_id", $sn_id);
    $query->bindParam(":sn_type", $sn_type);
    $query->bindParam(":sn_name", $sn_name);
    $query->bindValue(":active_user", $active);
    $query->execute();
}


function getSnInfo($db, $sn_id, $sn_type) {
    $query = $db->prepare("SELECT user_id, active_user, token 
                                FROM SnInfo WHERE sn_id=:sn_id AND sn_type=:sn_type");
    $query->bindParam(":sn_id", $sn_id);
    $query->bindParam(":sn_type", $sn_type);
    $query->execute();
    $res = $query->fetch();

    Log::write("getSnInfo returns: " . var_export($res, true));

    if ($res === false) {
        return null;
    }
    return $res;
}

/*!
 * Get the sn_id of a SnInfo given sn_type and user_id
 */
function getSnInfoId($db, $sn_type, $ps_id) {
    Log::write('Get SnInfoId : ' . $sn_type . " " . $ps_id);
    $query = $db->prepare("SELECT sn_id FROM SnInfo WHERE sn_type=:sn_type and user_id=:ps_id");
    $query->bindParam(":sn_type", $sn_type);
    $query->bindParam(":ps_id", $ps_id);
    $query->execute();
    $res = $query->fetch();

    if ($res === false) {
        return -1;
    }
    return $res['sn_id'];
}

/*!
 * Get a list of SnInfo of the given userId
 */
function getSnInfoByPsId($db, $psId) {
    $query = $db->prepare("SELECT * FROM SnInfo WHERE user_id=:id");
    $query->bindParam(":id", $psId);
    $query->execute();
    $res = $query->fetchAll();

    if ($res === false) {
        return null;
    }
    return $res;
}

function checkUserExist($ps_id, $db) {
    $ps_check = $db->prepare("SELECT ps_id FROM UserInfo WHERE ps_id=:id");
    $ps_check->bindParam(":id",$ps_id);
    $ps_check->execute();
    $obtained_ps = $ps_check->fetch();
    $ps_check=null;
    Log::write("PS id in database: " . $obtained_ps['ps_id'] . " given PS id: " . $ps_id);
    if($obtained_ps['ps_id'] != $ps_id) {
        Log::write("Transaction aborted. Given PeerShare ID doesn't exist");
        $db->rollBack();
        return false;
    }
    return true;
}

/*!
 * Update the token of the given SnInfo
 */
function updateSnInfoToken($db, $sn_type, $sn_id, $token) {
    $query = $db->prepare("UPDATE SnInfo SET token=:token 
                                WHERE sn_type=:sn_type and sn_id=:sn_id");
    $query->bindParam(":token", $token);
    $query->bindParam(":sn_type", $sn_type);
    $query->bindParam(":sn_id", $sn_id);
    $query->execute();
}

/*!
 * Check a secret info of the given existed in the given platform
 */
function getSecretInfo($db, $sn_id, $sn_type, $platformId, $platformAppId) {
    $query = $db->prepare("SELECT bid FROM SecretInfo, Bindings
                            WHERE Bindings.sn_id=:sn_id AND Bindings.sn_type=:sn_type
                                    and SecretInfo.object_id=Bindings.object_id
                                    and SecretInfo.platform_id=:platform_id
                                    and SecretInfo.platform_app_id=:platform_app_id");
    $query->bindParam(":platform_id", $platformId);
    $query->bindParam(":platform_app_id", $platformAppId);
    $query->bindParam(":sn_id", $sn_id);
    $query->bindParam(":sn_type", $sn_type);
    $query->execute();
    $res = $query->fetch();
    if ($res !== false) {
        return $res['bid'];
    } else {
        return -1;
    }
}

/*!
 * Create the Social Identity Object to return to the client
 */
function createSocialIdentityObjectForClient($sn_id, $sn_name, $sn_type) {
    return array(
        'social-id' => $sn_id, 
        'social-name' => $sn_name, 
        'network-id' => $sn_type
    );
}

/*!
 * Create the Secret Info Object to return to the client
 */
function createSecretInfoObjectForClient($data, $client_social_info, $hide_identity) {
    if ($hide_identity) {
        $identity = createSocialIdentityObjectForClient($data['sn_id'], "Anon", $data['sn_type']);
    } else {
        $identity = createSocialIdentityObjectForClient($data['sn_id'], $data['sn_name'], $data['sn_type']);
    }

    $object = array("type" => $data['secret_type'],
                    "algorithm" => $data['secret_algorithm'],
                    "value" => $data['secret_value'],
                    "description" => $data['description'],
                    "created" => strtotime($data['secret_ts']),
                    "expires" => strtotime($data['validity']),
                    "social-identities" => array($identity),
                    "binding_type" => $data['binding_type'],
                    "platform_id" => $data['platform_id'],
                    "platform_app_id" => $data['platform_app_id'],
                    "sensitivity" => $data['sensitivity'],
                    "specificity" => $data['specificity']);
    
    // if it's client's secret, add object_id
    if (in_array($data['sn_type'], array_keys($client_social_info)) 
            && (in_array($data['sn_id'], array_values($client_social_info)))) {
        Log::write("Adding additional information for secret author");
        $object = array_merge($object, array("object_id" => $data['object_id'], 
                                                "policy" => $data['policy']));
    }
    return $object;
}

/*!
 * Get platform_id and platform_app_id of the given secret
 */
function getPlatformInfo($db, $bid) {
    
    $query = $db->prepare("SELECT platform_app_id, platform_id FROM SecretInfo, Bindings 
                                    WHERE Bindings.bid=:bid 
                                            and SecretInfo.object_id=Bindings.object_id");
    $query->bindParam(":bid", $bid);
    $query->execute();
    $res = $query->fetch();
    return $res;
}

/*!
 * Get the list of friends of the given user with the local data only.
 * The returned list is sorted in ascending order of sn_id.
 */
function getLocalMemberList($db, $sn_id, $sn_type, $policy, $platform_id, $platform_app_id) {

    $query = $db->prepare("SELECT sn_id FROM AllowedViewers 
                            WHERE sn_type=:sn_type 
                                AND relation=:relation
                                AND bid in (SELECT B.bid from Bindings B, SecretInfo S, AclInfo A
                                            WHERE B.sn_id=:sn_id AND B.sn_type=:sn_type 
                                                AND B.bid=A.bid AND A.policy=:policy
                                                AND S.object_id=B.object_id
                                                AND S.platform_app_id=:platform_app_id 
                                                AND S.platform_id=:platform_id)
                            ORDER BY sn_id ASC");

    $query->bindParam(":sn_type", $sn_type);
    $query->bindParam(":sn_id", $sn_id);
    $query->bindParam(":policy", $policy);
    $query->bindParam(":platform_app_id", $platform_app_id);
    $query->bindParam(":platform_id", $platform_id);
    $query->bindValue(":relation", ViewerRelation::DIRECT_FRIEND);
    $query->execute();
    $res = $query->fetchAll();
    if ($res === false) {
        return array();
    } else {
        return $res;
    }
}

/*!
 * Function used to sort a list of friends according to sn_id
 */
function cmpId($a, $b) {
    return ($a['sn_id'] < $b['sn_id']) ? -1 : 1;
}

/*!
 * Extract the members that are in the first list, but not in the 
 * second list. Both lists need to be sorted in ascending order of sn_id.
 * @param  $fList the first list
 * @param  $sList the second list
 */
function getDifferentMembers($fList, $sList) {
    $final_list = array();

    $iF = 0;
    $nF = count($fList);    // length of fList
    $iS = 0;
    $nS = count($sList);    // length of sList
    while ($iS < $nS && $iF < $nF) {
        if ($fList[$iF]['sn_id'] < $sList[$iS]['sn_id']) {
            array_push($final_list, $fList[$iF]);        
            $iF++;
        } else if ($fList[$iF]['sn_id'] == $sList[$iS]['sn_id']) {
            $iF++;
        } else {
            $iS++;    
        }
    }
    while ($iF < $nF) {
        array_push($final_list, $fList[$iF]);        
        $iF++;
    }

    return $final_list;
}

/*!
 * Remove the member with the given sn_id in the friend list
 */
function removeSpecificMember(&$fList, $sn_id) {
    $nF = count($fList);
    for ($i = 0; $i < $nF; $i++) {
        if ($fList[$i]['sn_id'] == $sn_id) {
            array_splice($fList, $i, 1);
            return;
        }
    }
}

/*!
 * Delete the direct one-way relationship between two users: from friend_id to my_id
 * with the given policy and relation type.
 */
function deleteOneWayRelation($db, $my_id, $friend_id, $sn_type, $policy, $relation,
                                    $platform_id, $platform_app_id) {
    $query = $db->prepare("DELETE FROM AllowedViewers
                           WHERE sn_id=:my_id AND sn_type=:sn_type AND relation=:relation
                                AND bid in (SELECT B.bid FROM Bindings B NATURAL JOIN AclInfo A,
                                                                SecretInfo S
                                                WHERE B.sn_id=:friend_id AND B.sn_type=:sn_type
                                                    AND A.policy=:policy
                                                    AND S.platform_id=:platform_id
                                                    AND S.platform_app_id=:platform_app_id)"); 
    $query->bindParam(":my_id", $my_id);
    $query->bindParam(":friend_id", $friend_id);
    $query->bindParam(":sn_type", $sn_type);
    $query->bindParam(":policy", $policy);
    $query->bindParam(":relation", $relation);
    $query->bindParam(":platform_id", $platform_id);
    $query->bindParam(":platform_app_id", $platform_app_id);
    $query->execute();
}

/*date_default_timezone_set("Europe/Helsinki");
$db = openDb();
$ps_id = 2;
$submitted_tokens = array(
    "1" => "1212321321321321321323",
    "2" => "1123123122122222213123"
);
$res = getAvailableSecrets($db, $ps_id, $submitted_tokens);
echo count($res);
var_dump($res);*/

/*$db = openDb(); 
$friends = array(
    array("id" => "10152457728292200", "name" => "Duc Anh Bui"),
    array("id" => "4412628611411", "name" => "Thanh Bui")
);
$fof = getFriendsOfFriends($db, "11111111", 1, 1, "com.futurice.scampi.peopleinhere;3082030d308201f5a00302010202047a8344f3300d06092a864886f70d01010b05003037310b30090603550406130255533110300e060355040a1307416e64726f6964311630140603550403130d416e64726f6964204465627567301e170d3134303632363131313933325a170d3434303631383131313933325a3037310b30090603550406130255533110300e060355040a1307416e64726f6964311630140603550403130d416e64726f696420446562756730820122300d06092a864886f70d01010105000382010f003082010a0282010100c6c2122e9679860209ceff128d6cb77a12ae7251bccf0fb79d205b500bee89ed04ecfae474002fd92b5c817fdc5e2e2f002f28545eb504b9f5cc7475814ddfd4139e8b2b57d5dee234c4c2f0b8a113f2bcb0c8777429b98931f97e0f03b2a974bb3fa63b0ee7a4497b0abab11c166cde6495ba92fc24a129534528920c4ddcdbafb19066192d32a831c72e89dd9a4dd8cfec58c8bdf6c7a6ca56efd1b1727dc89ac4eb547269478d790469cf80183efbce8a55dcb4abe6c877972616e4a01aaefade1f311ec44a094fa6ad3c90f1d3080f489df936c9e1937a7780f1ec38230c0748f4cc352df8036117effa1fe3dfd8ba2698f5b2dcfab2d5e873d08b2d924d0203010001a321301f301d0603551d0e04160414221245cb6a30b478e2e348281f4b2f07a4f37cfc300d06092a864886f70d01010b0500038201010062bd0ab11d61ac26dd17c76213db17b0ee8246f9795d5b0b7396a7c5ef6defd2171088147ed53101977e7009d5fb84530c1ae9806976e79efdcd1cc6a3f1bb93f1b35bd71fc0593d6e1c901fa4539a1e9ca3b71bc698ece71265f7b228b58e73584bcd76f14926c3f35a79146cdbb8204e3ad0e2f4739527b3bf84b7c4af80d2fb2c9e82b17ce80cd51ed3e9017cff43b7dbf4aee638cfdd1f5c41865bf20ffe2bbf8a54e03ac51542589faa752253446e0fa9af58daaa6320bec35f884029258fdf482e229ed5af49b689090db2cc76ab23c249a365a218def5d1516d7dfece6d0164820d2a3c3a6fe367c08813777ce4439873de8590d9960fbfad2208d136", $friends);
//var_dump($fof);
echo "length: " . count($fof);*/

//$friends = FacebookHandler::getMutualFriends("10152457728292200", "CAAGnoiUrYcoBAO6CkJMZA76CYXTuJ8qZCvtjNRjrDAudRpDOZBzr6rEq0GJGLEU85Kh0nl5Xfba6xlfatCGGmra86daPpoURlHsSzZCoDMnb9c0Ie59bLdcQWP5tClOo5LRLftgp20Qf66vx5jAT50jLrHHtDS7gjlXO1XHoa6PspSV9jvwQ3zVR3nlf4fDpEvrIyWW7Az4y5N7x8dZC5t2wJR4M8H7JmFs4VEVD87AZDZD", "100005234659320");

?>

<?php

function prepareJSON($input) {
    
    //This will convert ASCII/ISO-8859-1 to UTF-8.
    //Be careful with the third parameter (encoding detect list), because
    //if set wrong, some input encodings will get garbled (including UTF-8!)
    $imput = mb_convert_encoding($input, 'UTF-8', 'ASCII,UTF-8,ISO-8859-1');
    
    //Remove UTF-8 BOM if present, json_decode() does not like it.
    if(substr($input, 0, 3) == pack("CCC", 0xEF, 0xBB, 0xBF)) $input = substr($input, 3);
    
    return $input;
}

/*!
 * Convert milliseconds to timestamp
 */
function msToTimestamp($milliseconds) {
    $seconds = round($milliseconds/1000, 0, PHP_ROUND_HALF_DOWN);
    $remainingMilli = $milliseconds % 1000;
    $timestamp = gmdate("Y-m-d H:i:s", $seconds);
    $timestamp .= "." . sprintf("%03d", $remainingMilli);
    return $timestamp;
}

// To calculate intersection of complex arrays
function compareDeepValue($val1, $val2) {
    return strcmp($val1['sn_id'], $val2['sn_id']);
}

class NonceGenerator {
    public static function newNonce() {
        $curTime = round(microtime(true) * 1000); // in milliseconds
        $nonce = sha1(mt_rand() / mt_getrandmax() * $curTime);
        return $nonce;
    }
}

class Log {
    public static function write($msg) {
        error_log($msg . "\n", 3, "./log.txt");
    }
}

?>

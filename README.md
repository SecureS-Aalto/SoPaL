# SoPaL

## 1. Integrating SoPaL server

Note: LinkedIn disabled access to Connections API for non-enterprise applications as of May 2015. Making effectively LinkedIn server integration useless.

Installation requirements: PHP server, PDO compatible SQL engine (e.g., we use Sqlite3 and PostgreSQL), cURL library.

1. Please create a Facebook application.
2. Please use appID and appSecret of your Facebook application and put it inside $conf variable in server/socialnetworks/facebookhandler.php.
3. Configure your PHP server to serve your requests for PHP files inside "server" directory.

## 2. Integrating SoPaL client (Android devices only)

Requirements: integrate Facebook login functionality into your application

1. Copy content of "Android" directory to your project as top level directories inside your "src" directory.
2. Replace "res/values/strings.xml": *registerURL*, *unregisterURL*, *uploadURL*, *downloadURL* with your PHP URL. Below are settings for our original app:

```
    <string name="registerURL">https://se-sy.org/foftest/p2dserver/register.php</string>
    <string name="unregisterURL">https://se-sy.org/foftest/p2dserver/unregister.php</string>
    <string name="uploadURL">https://se-sy.org/foftest/p2dserver/upload.php</string>
    <string name="downloadURL">https://se-sy.org/foftest/p2dserver/download.php</string>
```

<!--
To change this template, choose Tools | Templates
and open the template in the editor.
-->
<html>
    <head>
	<meta name="viewport" content="width=360, initial-scale=1, maximum-scale=1"/>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<link rel="stylesheet" type="text/css" href="background.css" media="screen" />
        <title>Bindings</title>
	<script type="text/javascript" src="util.js"></script>
    </head>
    <body id="body">
        <h1>Bindings<br></h1>
        <p>This application will register your device address with your facebook id.<br>
           Note: Clicking on the button below will send your device mac address to this app.<br>
        </p>

        <script type="text/javascript" src="util.js"></script>
        <input id="fbLoginButton" type="button" onclick="redirectToFacebook();" value="Facebook" />

        <p> Link to privacy statement</p>
	<script type="text/javascript">
	function setSize(w,h) {
		var body = document.getElementById("body");
		body.style.width = w;
		body.style.height = h;
	}
	</script>
    </body>
</html>

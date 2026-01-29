javac -cp "/sdcard/ttt/core.jar" src/my/plugin/WebServerPlugin.java
jar cf WebServerPlugin.jar -C src . 
jar uf WebServerPlugin.jar plugin.yml

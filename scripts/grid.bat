@ECHO OFF

"%JAVA_HOME%\bin\java.exe" -Xms32M -Xmx768M -classpath "$INSTALL_PATH\bundles\org.apache.commons.logging_1.1.1.v201101211721.jar:$INSTALL_PATH\ext\log4j-1.2.17.jar:$INSTALL_PATH\lib;$INSTALL_PATH\ext\gffs-basics.jar" "-Dlog4j.configuration=$CLIENT_LOG4JCONFIG" "-Djava.library.path=$INSTALL_PATH\%{GENII_JNI_PATH}" "-Dedu.virginia.vcgr.genii.install-base-dir=$INSTALL_PATH" edu.virginia.vcgr.appmgr.launcher.ApplicationLauncher "$INSTALL_PATH\ext\genii-client-application.properties" %*


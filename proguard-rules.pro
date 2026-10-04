# Zeilennummern für lesbare Stacktraces aus Release-Crashberichten behalten.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# JGit lädt seine Meldungstexte per Reflection: TranslationBundle füllt die öffentlichen Felder von
# JGitText & Co. anhand des Feldnamens. Ohne diese Regeln benennt oder entfernt R8 die Felder, und
# JGit scheitert beim ersten Zugriff auf einen Text.
-keep class org.eclipse.jgit.internal.JGitText { *; }
-keep class org.eclipse.jgit.nls.** { *; }
-keepclassmembers class * extends org.eclipse.jgit.nls.TranslationBundle { *; }
-keep class org.eclipse.jgit.internal.**.*Text { *; }

# JGit und MINA SSHD finden Implementierungen über ServiceLoader (META-INF/services).
-keep class org.eclipse.jgit.transport.** implements org.eclipse.jgit.transport.TransportProtocol { *; }
-keep class org.apache.sshd.** { *; }
-keep class org.eclipse.jgit.transport.sshd.** { *; }
# Ed25519: MINA SSHD erkennt die Bibliothek per Class.forName und lädt ihren Security-Provider per Namen.
-keep class net.i2p.crypto.eddsa.** { *; }

# JVM-Pakete, die es auf Android nicht gibt und die nur in nicht genutzten Pfaden vorkommen.
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.osgi.**
-dontwarn javax.security.auth.login.**
-dontwarn sun.security.**
-dontwarn com.sun.jna.**
-dontwarn org.bouncycastle.**
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn org.slf4j.impl.**
-dontwarn java.rmi.**
# Nur in JGits GC$PidLock; ein GC über gc() wird in der App nie gestartet (siehe AppSystemReader).
-dontwarn java.lang.ProcessHandle
-dontwarn javax.security.auth.callback.**
# SSH-Agent über Tomcat-APR und PKCS#11 werden nicht genutzt (keine nativen Bibliotheken auf Android).
-dontwarn org.apache.tomcat.jni.**

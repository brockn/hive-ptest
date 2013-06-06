# About

Hive-ptest is a parallel testing framework for hive.

# Building

Approvals is required and must be manually installed:

http://sourceforge.net/projects/approvaltests/files/ApprovalTests.Java/

Example:

    (cd /tmp && wget http://people.apache.org/~brock/ApprovalTests.013/ApprovalTests.jar)
    mvn install:install-file -Dfile=/tmp/ApprovalTests.jar -DgroupId=approvaltests \
       -DartifactId=ApprovalsJava -Dversion=013 -Dpackaging=jar
    mvn clean package


# Configuring

* Create a user such as hiveptest on the master and all slaves.
* Setup passwordless ssh form the master to all slaves.
* Ensure that SSH connection attempts won't fail.

On all slaves add the following to /etc/ssh/sshd_config:

    MaxAuthTries 100
    MaxSessions 100
    MaxStartups 100

# Install git, java, ant, and maven

Recent version os java, ant and maven should be installed. Additionally environment variables
such as MAVEN_OPTS and ANT_OPTS should be configured with large leap sizes:

    $ for item in java maven ant; do echo $item; cat /etc/profile.d/${item}.sh;done
    java
    export JAVA_HOME=$(readlink -f /usr/java/default)
    export PATH=$JAVA_HOME/bin:$PATH
    maven
    export M2_HOME=$(readlink -f /usr/local/apache-maven)
    export PATH=$M2_HOME/bin:$PATH
    export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=256M"
    ant
    export ANT_HOME=$(readlink -f /usr/local/apache-ant)
    export PATH=$ANT_HOME/bin:$PATH
    export ANT_OPTS="-Xmx1g -XX:MaxPermSize=256m"

# Ensure umask is setup to 0022

    $ cat /etc/profile.d/umask.sh 
    umask 0022

# Configure properties file

See conf/example-apache-trunk.properties

# Execute


    java -Xms4g -Xmx4g -cp conf/:/home/hiveptest/hive-ptest/target/hive-test-1.0-jar-with-dependencies.jar org.apache.hive.ptest.RunTests --properties apache-trunk.properties

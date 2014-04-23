#!/bin/bash
# WARNING:
# *** do NOT use TABS for indentation, use SPACES
# *** TABS will cause errors in some linux distributions

# SCRIPT CONFIGURATION:
share_dir=/usr/local/share/mtwilson/util
apiclient_dir=/usr/local/share/mtwilson/apiclient
setupconsole_dir=/opt/intel/cloudsecurity/setup-console
apiclient_java=${apiclient_dir}/java
env_dir=/usr/local/share/mtwilson/env
conf_dir=/etc/intel/cloudsecurity
#apiclient_shell=${apiclient_dir}/shell
#mysql_required_version=5.0
#glassfish_required_version=4.0
#java_required_version=1.7.0_51

# FUNCTION LIBRARY and VERSION INFORMATION
if [ -f ${share_dir}/functions ]; then  . ${share_dir}/functions; else echo "Missing file: ${share_dir}/functions";   exit 1; fi
if [ -f ${share_dir}/version ]; then  . ${share_dir}/version; else  echo_warning "Missing file: ${share_dir}/version"; fi
if [ ! -d ${env_dir} ]; then mkdir -p ${env_dir}; fi
shell_include_files ${env_dir}/*
if [[ "$@" != *"ExportConfig"* ]]; then   # NEED TO DEBUG FURTHER. load_conf runs ExportConfig and if that same command is passed in from 'mtwilson setup', it won't work
  load_conf 2>&1 >/dev/null
  if [ $? -ne 0 ]; then
    if [ $? -eq 2 ]; then echo_failure -e "Incorrect encryption password. Please verify \"MTWILSON_PASSWORD\" variable is set correctly."; fi
    exit -1
  fi
fi
load_defaults 2>&1 >/dev/null
#if [ -f /root/mtwilson.env ]; then  . /root/mtwilson.env; fi
if [ -f ${apiclient_dir}/apiclient.env ]; then  . ${apiclient_dir}/apiclient.env; fi

# ensure we have some global settings available before we continue so the rest of the code doesn't have to provide a default
#export DATABASE_VENDOR=${DATABASE_VENDOR:-postgres}
#export WEBSERVER_VENDOR=${WEBSERVER_VENDOR:-glassfish}

# XXX TODO can remove this later; converting  all package_required_version variable names to be upper case, which can be defined in mtwilson.env or for which we have a default
if using_mysql; then
    export mysql_required_version=${MYSQL_REQUIRED_VERSION:-5.0}
elif using_postgres; then
    export postgres_required_version=${POSTGRES_REQUIRED_VERSION:-9.3}
fi
if using_glassfish; then
    export GLASSFISH_REQUIRED_VERSION=${GLASSFISH_REQUIRED_VERSION:-4.0}
elif using_tomcat; then
    export tomcat_required_version=${TOMCAT_REQUIRED_VERSION:-7.0}
fi
export JAVA_REQUIRED_VERSION=${JAVA_REQUIRED_VERSION:-1.7.0_51}
export java_required_version=${JAVA_REQUIRED_VERSION}

call_apiclient() {
  if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; return 1; fi
  APICLIENT_JARS=$(JARS=(${apiclient_java}/*.jar); IFS=:; echo "${JARS[*]}")
  mainclass=com.intel.mtwilson.client.TextConsole
  $java -cp "$APICLIENT_JARS" -Dlogback.configurationFile=${conf_dir}/logback.xml $mainclass $@
}

find_ctl_commands() {
  pcactl=`which pcactl 2>/dev/null`
  msctl=`which msctl 2>/dev/null`
  wlmctl=`which wlmctl 2>/dev/null`
  asctl=`which asctl 2>/dev/null`
  mpctl=`which mtwilson-portal 2>/dev/null`
}

mtwilson_saml_cert_report() {
  local keystore="$SAML_KEYSTORE_FILE"   #`read_property_from_file saml.keystore.file ${conf_dir}/attestation-service.properties`
  local keystorePassword=$SAML_KEYSTORE_PASSWORD   #`read_property_from_file saml.keystore.password ${conf_dir}/attestation-service.properties`
  local keyalias=$SAML_KEY_ALIAS   #`read_property_from_file saml.key.alias ${conf_dir}/attestation-service.properties`
  java_keystore_cert_report "${conf_dir}/$keystore" "$keystorePassword" "$keyalias"
}


# called by installer to automatically configure the server for localhost integration,
# if the customer has enabled this option
# arguments:   currently supports just one optional argument, IP address or hostname, to enable as trusted IN ADDITION TO 127.0.0.1
mtwilson_localhost_integration() {
  local iplist;
  if [ -n "$1" ]; then
    iplist="127.0.0.1,$1"
  else
    iplist="127.0.0.1"
  fi
  update_property_in_file mtwilson.api.trust /etc/intel/cloudsecurity/management-service.properties "$iplist"
  update_property_in_file mtwilson.ssl.required /etc/intel/cloudsecurity/management-service.properties "false"
}


setup() {
  java_detect; java_env_report > ${env_dir}/java
  if using_glassfish; then
    glassfish_detect; glassfish_env_report > ${env_dir}/glassfish
  elif using_tomcat; then
    tomcat_detect; tomcat_env_report > ${env_dir}/tomcat
  fi
  find_ctl_commands

  # Set the "setup" flag so that service setup commands to not attempt to re-deploy their application (in order to preserve any customized version of the app that has been deployed to glassfish directly)
  export MTWILSON_SETUP_NODEPLOY=1

  # Gather default configuration
  MTWILSON_SERVER_IP_ADDRESS=${MTWILSON_SERVER_IP_ADDRESS:-$(hostaddress)}
  MTWILSON_SERVER=${MTWILSON_SERVER:-$MTWILSON_SERVER_IP_ADDRESS}

  # Prompt for installation settings
  echo "Please enter the IP Address or Hostname that will identify the Mt Wilson server.
This address will be used in the server SSL certificate and in all Mt Wilson URLs,
such as https://${MTWILSON_SERVER:-127.0.0.1}.
Detected the following options on this server:"
  IFS=$'\n'; echo "$(hostaddress_list)"; IFS=' '; hostname;
  prompt_with_default MTWILSON_SERVER "Mt Wilson Server:"
  export MTWILSON_SERVER
  echo
  if using_mysql; then
    mysql_userinput_connection_properties
    export MYSQL_HOSTNAME MYSQL_PORTNUM MYSQL_DATABASE MYSQL_USERNAME MYSQL_PASSWORD
  elif using_postgres; then
    postgres_userinput_connection_properties
    export POSTGRES_HOSTNAME POSTGRES_PORTNUM POSTGRES_DATABASE POSTGRES_USERNAME POSTGRES_PASSWORD
    echo "$POSTGRES_HOSTNAME:$POSTGRES_PORTNUM:$POSTGRES_DATABASE:$POSTGRES_USERNAME:$POSTGRES_PASSWORD" > $HOME/.pgpass
    chmod 0600 $HOME/.pgpass
  fi

  # Attestation service auto-configuration
  export PRIVACYCA_SERVER=${MTWILSON_SERVER}

  # TODO should be completely removed because functions to create certificates are moving to the java tool
  # ## TODO: prompt for server settings (move from mtwilson-server installer setup.sh to here)
  # create glassfish ssl cert (TODO: move to a "glassfish" control script that should be created to make glassfish management easier... so instead of "mtwilson glassfish-stop" it would be just "glassfish stop"  etc)
  if using_glassfish; then
    if [ -n "${MTWILSON_SERVER}" ]; then
      glassfish_create_ssl_cert "${MTWILSON_SERVER}"
    else
      glassfish_create_ssl_cert_prompt
    fi
  fi

  # new setup commands in mtwilson 2.0
  # mtwilson setup setup-manager update-extensions-cache-file
  # mtwilson setup setup-manager create-admin-user
  call_tag_setupcommand setup-manager update-extensions-cache-file --force --no-ext-cache
  # NOTE:  in order to run create-admin-user successfully you MUST have
  #        the environment variable MC_FIRST_PASSWORD defined; this is already
  #        done when running from the installer but if user runs 'mtwilson setup'
  #        outside the installer the may have to export MC_FIRST_PASSWORD first
  call_tag_setupcommand setup-manager create-certificate-authority-key create-admin-user

  call_setupcommand EncryptDatabase

  # setup web services:
  if [ -n "$pcactl" ]; then $pcactl setup; $pcactl restart; fi
  if [ -n "$asctl" ]; then $asctl setup; fi
  if [ -n "$msctl" ]; then $msctl setup; fi
  if [ -n "$wlmctl" ]; then $wlmctl setup; fi
  if [ -n "$mpctl" ]; then $mpctl setup; fi

  # java setup tool - right now just checks database encryption, in the future it will take over some of the setup functions from the *ctl scripts which can be done in java
  shift
}

all_status() {
  if using_glassfish; then
    glassfish_running_report
  elif using_tomcat; then
    tomcat_running_report
  fi
  
  find_ctl_commands
  if [ -n "$pcactl" ]; then $pcactl status; fi
  if [ -n "$asctl" ]; then $asctl status; fi
  if [ -n "$mpctl" ]; then $mpctl status; fi
}

setup_env() {
  local datestr=`date +%Y-%m-%d.%H%M`
  echo "# environment on ${datestr}"
  java_detect > /dev/null
  echo "JAVA_HOME=$JAVA_HOME"
  echo "java_bindir=$java_bindir"
  echo "java=$java"
  #export JAVA_HOME java_bindir java
  if using_mysql; then
    mysql_detect > /dev/null
    echo "mysql=$mysql"
    #export mysql
  elif using_postgres; then
    postgres_detect > /dev/null
    #echo "postgres=$psql"
    #the actuall veriable for postgres commands is psql
    echo "psql=$psql"
  fi
  echo "WEBSERVER_VENDOR=$WEBSERVER_VENDOR"
  echo "DATABASE_VENDOR=$DATABASE_VENDOR"
  if using_glassfish; then
    glassfish_detect > /dev/null
    echo "GLASSFISH_HOME=$GLASSFISH_HOME"
    echo "glassfish_bin=$glassfish_bin"
    echo "glassfish=\"$glassfish\""
    #export GLASSFISH_HOME glassfish_bin glassfish
  elif using_tomcat; then
    tomcat_detect > /dev/null
    echo "TOMCAT_HOME=$TOMCAT_HOME"
    echo "tomcat_bin=$tomcat_bin"
    echo "tomcat=\"$tomcat\""
  fi
  echo "MTWILSON_SERVER=$MTWILSON_SERVER"
  echo "DEFAULT_API_PORT=$DEFAULT_API_PORT"
}

RETVAL=0

# See how we were called.
case "$1" in
  version)
        echo "MtWilson Linux Utility"
        echo "Version ${VERSION}"
        echo "Build ${BUILD}"
        ;;
  setup-env)
        setup_env
        ;;
  start)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        if using_glassfish; then
          glassfish_start
        elif using_tomcat; then
          tomcat_start
        fi
        ;;
  stop)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        if using_glassfish; then
          glassfish_stop
          #glassfish_shutdown
        elif using_tomcat; then
          tomcat_stop
          #tomcat_shutdown
        fi
        ;;
  restart)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        if using_glassfish; then
          glassfish_restart
        elif using_tomcat; then
          tomcat_restart
        fi
        ;;
  glassfish-detect)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        glassfish_detect ${2:-$GLASSFISH_REQUIRED_VERSION}
        glassfish_env_report
        ;;
  glassfish-start)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        glassfish_require
        glassfish_start_report
        ;;
  glassfish-stop)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        glassfish_require
        glassfish_shutdown
        ;;
  glassfish-restart)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        glassfish_require
        glassfish_restart
        ;;
  glassfish-status)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        glassfish_require
        glassfish_running_report
        glassfish_sslcert_report
        ;;
  glassfish-sslcert)
        #echo_warning "This feature has been disabled: mtwilson glassfish-sslcert"
        glassfish_create_ssl_cert_prompt
        ;;
  glassfish-enable-logging)
        glassfish_enable_logging
        ;;
  tomcat-detect)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        tomcat_detect ${2:-$TOMCAT_REQUIRED_VERSION}
        tomcat_env_report
        ;;
  tomcat-start)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        tomcat_require
        tomcat_start_report
        ;;
  tomcat-stop)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        tomcat_require
        tomcat_shutdown
        ;;
  tomcat-restart)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        tomcat_require
        tomcat_restart
        ;;
  tomcat-status)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        tomcat_require
        tomcat_running_report
        tomcat_sslcert_report
        ;;
  tomcat_sslcert)
        tomcat_create_ssl_cert_prompt
        ;;  
  mysql-detect)
        mysql_detect $2
        mysql_env_report
        ;;
  mysql-sslcert)
        mysql_create_ca
        mysql_create_ssl "MySQL Server"
        mysql_create_ssl "Attestation Service"
        mysql_create_ssl "Management Service"
        mysql_create_ssl "Whitelist Service"
        mysql_create_ssl "Trust Dashboard"
        ;;
  java-detect)
        java_detect $2
        java_env_report
        ;;
  localhost-integration)
        mtwilson_localhost_integration $@
        ;;
  #api)
  #      shift
  #      call_apiclient $@
  #      ;;
  setup)
        if [ $# -gt 1 ]; then
          shift
          # first look for known old setup commands:
          if [ "$1" = "InitDatabase" ] || [ "$1" = "BootstrapUser" ] || [ "$1" = "EncryptDatabase" ] || [ "$1" = "ImportConfig" ] || [ "$1" = "ExportConfig" ] || [ "$1" = "V2" ]; then
            call_setupcommand $@
          else            
            # for everything else, try the new setup commands first,
            # if there return code is 1 it means  the command was not found,
            # and in that case we try the old setup commands
            call_tag_setupcommand $@
            if [ $? == 1 ]; then call_setupcommand $@; fi
          fi
        else
          if [ -f /root/mtwilson.env ]; then  . /root/mtwilson.env; fi
          setup
        fi
        ;;
  fingerprint)
        # show server ssh fingerprint, glassfish ssl fingerprint, and saml cert fingerprint
        echo "== SSH HOST KEYS =="
        ssh_fingerprints
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        if using_glassfish; then
          glassfish_require
          echo "== GLASSFISH SSL CERTIFICATE =="
          glassfish_sslcert_report
        fi
        echo "== MT WILSON SAML CERTIFICATE =="
        mtwilson_saml_cert_report
        ;;
  status)
        all_status
        ;;
  erase-data)
        #load_default_env 1>/dev/null
        erase_data
        #call_setupcommand EraseLogs
        #call_setupcommand EraseHostRegistrationData
        #call_setupcommand EraseWhitelistData
        
        ;;
  erase-users)
        #load_default_env 1>/dev/null
        if [[ -z "$MC_FIRST_USERNAME" && "$@" != *"--all"* && "$@" != *"--user"* ]]; then
          echo_warning "Unable to detect mtwilson portal admin username. Please specify the admin username."
          prompt_with_default MC_FIRST_USERNAME "Mtwilson Portal Admin Username:" "admin"
        fi
        export MC_FIRST_USERNAME=$MC_FIRST_USERNAME
        call_setupcommand EraseUserAccounts $@
        ;;
  change-db-pass)
        if [ $# -ne 1 ]; then
          echo_failure "Usage: $0 $1"; exit -1;
        fi
        change_db_pass
        ;;
  uninstall)
        if no_java ${java_required_version:-1.7}; then echo "Cannot find Java ${java_required_version:-1.7} or later"; exit 1; fi
        #load_default_env 1>/dev/null
        if using_glassfish; then
          glassfish_require
          echo "Stopping Glassfish..."
          glassfish_shutdown
          #rm -rf /usr/share/glassfish4
        elif using_tomcat; then
          tomcat_require
          echo "Stopping Tomcat..."
          tomcat_shutdown
          #rm -rf "$TOMCAT_HOME"
        fi
        # application files
        echo "Removing mtwilson applications from webserver..."
        webservice_uninstall HisPrivacyCAWebServices2
        webservice_uninstall mtwilson-portal
        webservice_uninstall mtwilson
        webservice_uninstall AttestationService 2>&1 > /dev/null
        webservice_uninstall ManagementService 2>&1 > /dev/null
        webservice_uninstall WLMService 2>&1 > /dev/null

        echo "Removing Mt Wilson applications in /opt/intel/cloudsecurity..."
        rm -rf /opt/intel/cloudsecurity
        echo "Removing Mt Wilson utilities in /usr/local/share/mtwilson..."
        rm -rf /usr/local/share/mtwilson
        # configuration files
        echo "Removing Mt Wilson configuration in /etc/intel/cloudsecurity..."
        rm -rf /etc/intel/cloudsecurity
        # data files
        echo "Removing Mt Wilson data in /var/opt/intel..."
        rm -rf /var/opt/intel
        # control scripts
        echo "Removing Mt Wilson control scripts..."
        echo mtwilson-portal asctl wlmctl msctl pcactl mtwilson | tr ' ' '\n' | xargs -I file rm -rf /usr/local/bin/file
            # only remove the config files we added to conf.d, not anything else
            echo "Removing mtwilson monit config files"
            rm -fr /etc/monit/conf.d/*.mtwilson
            echo "Restarting monit after removing configs"
            service monit stop &> /dev/null
            echo "Removing mtwilson logrotate files"
            rm -fr /etc/logrotate.d/mtwilson
        # java:  rm -rf /usr/share/jdk1.7.0_51
        # Finally, clear variables so that detection will work properly if mt wilson is re-installed  XXX TODO need to export
        java_clear; export JAVA_HOME=""; export java=""; export JAVA_VERSION=""
        tomcat_clear; export TOMCAT_HOME=""; export tomcat_bin=""; export tomcat=""
        glassfish_clear; export GLASSFISH_HOME=""; export glassfish_bin=""; export glassfish=""
        postgres_clear; export POSTGRES_HOME=""; export psql=""
        mysql_clear; export MYSQL_HOME=""; export mysql=""
        echo_success "Done"
        if [ -n "$INSTALLED_MARKER_FILE" ]; then
          rm -fr $INSTALLED_MARKER_FILE
        fi
        ;;
  help)
        ;&
  *)
        echo -e "Usage: mtwilson {change-db-pass|erase-data|erase-users|fingerprint|help|\n" \
          "\t\tglassfish-detect|glassfish-enable-logging|glassfish-sslcert|glassfish-status|\n" \
          "\t\tjava-detect|mysql-detect|mysql-sslcert|tomcat-detect|tomcat_sslcert|tomcat-status|\n" \
          "\t\trestart|setup|start|status|stop|uninstall|version}"
        exit 1
esac

exit $RETVAL
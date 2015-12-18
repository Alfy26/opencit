@echo off
setlocal enabledelayedexpansion

REM # SCRIPT CONFIGURATION:
set package_name=trustagent

REM the package directory should be where the trust agent installed (e.g. package_dir=C:\Program Files (x86)\Intel\trustagent)
set package_bin=%~dp0
for %%a in ("%package_bin:~0,-1%") do set package_dir=%%~dpa

REM echo. %package_bin%
echo. ==Trust Agent located at: %package_dir%

REM ==set PATH for the current cmd 
set PATH=%PATH%;%package_bin%
REM ==set global PATH
REM setx PATH "%PATH%;%package_bin" /M

set intel_conf_dir=%package_dir%\configuration
set package_config_filename=%intel_conf_dir%\%package_name%.properties
set package_env_filename=%package_dir%\%package_name%.env
set package_version_filename=%package_dir%\env.d\trustagent.version
set ASSET_TAG_SETUP="y"
set trustagent_cmd=%package_dir%\bin\tagent.cmd
set bootdriver_dir=%package_dir%\bootdriver

set logfile=%package_dir%\logs\install.log

REM # FUNCTION LIBRARY, VERSION INFORMATION, and LOCAL CONFIGURATION
ECHO. ==Configure trust agent version and environment variables==
cd %package_dir%
IF EXIST "%package_dir%\version" (
  REM echo. "Version file found"
  for /f  "tokens=*" %%a in (version) do (
    set %%a
  )
) ELSE (
  echo. Version file not found
)

ECHO. ==Configure trust agent environment variables from trustagent.env
IF EXIST "%package_dir%\trustagent.env" (
  REM echo. trustagent.env found
  for /f  "USEBACKQ tokens=*" %%a in ("%package_dir%\trustagent.env") do (
    echo.   %%a
    set %%a
  )
) ELSE (
  echo. Missing file: trustagent.env
)

REM # this is a list of all the variables we expect to find in trustagent.env
set TRUSTAGENT_ENV_VARS=MTWILSON_API_URL MTWILSON_TLS_CERT_SHA1 MTWILSON_API_USERNAME MTWILSON_API_PASSWORD TPM_OWNER_SECRET TPM_SRK_SECRET AIK_SECRET AIK_INDEX TPM_QUOTE_IPV4 TRUSTAGENT_HTTP_TLS_PORT TRUSTAGENT_TLS_CERT_DN TRUSTAGENT_TLS_CERT_IP TRUSTAGENT_TLS_CERT_DNS TRUSTAGENT_KEYSTORE_PASSWORD DAA_ENABLED TRUSTAGENT_PASSWORD JAVA_REQUIRED_VERSION HARDWARE_UUID

REM export_vars $TRUSTAGENT_ENV_VARS
FOR %%a in (%TRUSTAGENT_ENV_VARS%) do (
REM  ECHO. %%a
)

REM # before we start, clear the install log
REM ECHO. ==Before start, clear the installation log file %logfile%==
> "%logfile%" echo. %date%
>> "%logfile%" echo. %time%

REM # Automatic install steps:
REM # 1. Install prereqs
REM # 2. Backup old files
REM # 3. Install Measurement Agent
REM # 4. Create directory structure
REM # 5. Install Mt Wilson Linux utilities (and use them in this script)
REM # 6. Install JDK
REM # 7. Compile TPM commands
REM # 8. Install Trust Agent files

REM ##### install prereqs
REM auto_install "TrustAgent requirements" "APPLICATION"

REM ##### backup old files

REM # backup configuration directory before unzipping our package
ECHO. ==Backup configuration directory==
IF EXIST "%intel_conf_dir%\" (
  xcopy "%intel_conf_dir%" "%intel_conf_dir%.bak" /E /I /Y /Q > nul
)

REM FIXIT ##### stop existing trust agent if running
REM # before we stop the trust agent, remove it from the monit config (if applicable)

REM # bug #288 we do not uninstall previous version because there are files including trustagent.jks  under the /opt tree and we need to keep them during an upgrade
REM # But if trust agent is already installed and running, stop it now (and start the new one later)
REM existing_tagent=`which tagent 2>/dev/null`
REM if [ -f "$existing_tagent" ]; then
REM  echo "Stopping trust agent..."
REM   $existing_tagent stop
REM fi

REM FIXIT groupadd trustagent >> $logfile  2>&1
REM FIXIT useradd -d /opt/trustagent -r -s /bin/false -g trustagent trustagent >> $logfile  2>&1

REM FIXIT hex2bin_install() {
REM  return_dir=`pwd`
REM  cd hex2bin
REM  make && cp hex2bin /usr/local/bin
REM  cd $return_dir
REM }
REM hex2bin_install

REM hex2bin=`which hex2bin 2>/dev/null`
REM if [[ ! -h "${package_dir}/bin/hex2bin" ]]; then
REM  ln -s "$hex2bin" "${package_dir}/bin"
REM fi

REM ##Private Java install $JAVA_PACKAGE
ECHO. ==Unpack JAVA JRE==
  cd "%package_dir%\jre"
  jre.exe -qo > nul
  set JAVA_HOME=%package_dir%\jre
  cd "%package_bin%" 

REM patch java.security file
ECHO. ==Patch java.security file==
if exist "%JAVA_HOME%\lib\security\java.security" (
  REM echo. ==Replacing java.security file, existing file will be backed up==
  copy "%JAVA_HOME%\lib\security\java.security" "%JAVA_HOME%\lib\security\java.security.old" > nul
  copy "%package_dir%\java.security" "%JAVA_HOME%\lib\security\java.security" > nul
)

REM  # create trustagent.version file
echo. ==Create trustagent.version==
> "%package_version_filename%"  echo. "# Installed Trust Agent on %date% %time%"
>> "%package_version_filename%"  echo. "TRUSTAGENT_VERSION=%VERSION%"
>> "%package_version_filename%"  echo "TRUSTAGENT_RELEASE=\"%BUILD%\""

echo. ==Registering tagent in start up==
REM register_startup_script /usr/local/bin/tagent tagent 21 >>$logfile 2>&1

REM fix_existing_aikcert() {
REM  local aikdir=${intel_conf_dir}/cert
REM  if [ -f $aikdir/aikcert.cer ]; then
REM    # trust agent aikcert.cer is in broken PEM format... it needs newlines every 76 characters to be correct
REM    cat $aikdir/aikcert.cer | sed 's/.\{76\}/&\n/g' > $aikdir/aikcert.pem
REM    rm $aikdir/aikcert.cer
REM    if [ -f ${package_config_filename} ]; then 
REM       # update aikcert.filename=aikcert.cer to aikcert.filename=aikcert.pem
REM       update_property_in_file aikcert.filename ${package_config_filename} aikcert.pem
REM    fi
REM  fi
REM }
REM fix_existing_aikcert

REM # collect all the localhost ip addresses and make the list available as the
REM # default if the user has not already set the TRUSTAGENT_TLS_CERT_IP variable
ECHO. ==Find the IP address of the host==
set DEFAULT_TRUSTAGENT_TLS_CERT_IP=
for /f "tokens=14 delims= " %%a in ('ipconfig ^| findstr "IPv4"') do (
  IF "%DEFAULT_TRUSTAGENT_TLS_CERT_IP%"=="" (
    set DEFAULT_TRUSTAGENT_TLS_CERT_IP=%%a
  ) ELSE (
    set DEFAULT_TRUSTAGENT_TLS_CERT_IP=%%a,!DEFAULT_TRUSTAGENT_TLS_CERT_IP!
  )
)

IF "%TRUSTAGENT_TLS_CERT_IP%"=="" (
  set TRUSTAGENT_TLS_CERT_IP=%DEFAULT_TRUSTAGENT_TLS_CERT_IP%
)
REM echo.   TA IP Address: %TRUSTAGENT_TLS_CERT_IP%

REM # before running any tagent commands update the extensions cache file
ECHO. ==Update the extensions cache file before running any tagent commands 
>>"%logfile%" call "%trustagent_cmd%" setup update-extensions-cache-file --force

REM # create a trustagent username "mtwilson" with no password and all privileges
REM # which allows mtwilson to access it until mtwilson UI is updated to allow
REM # entering username and password for accessing the trust agent
ECHO. ==Create a trustagent username "mtwilson" with no password
>>"%logfile%" call "%trustagent_cmd%" password mtwilson --nopass *:*

REM FIXIT setup correct shiro.ini (should not hardcode the path in shiro.ini setup correct shiro.ini)
ECHO. ==Copy shiro-win.ini to shiro.ini
copy /Y "%intel_conf_dir%\shiro-win.ini" "%intel_conf_dir%\shiro.ini" > nul

REM # INSTALL the citbootdriver to support geotag
REM  cd "%bootdriver_dir%"
  REM #Remove the old driver first in case it is still there
REM  start /d "%bootdriver_dir%\" /b citbootdriversetup.exe uninstall
REM  del /F /Q "C:\Windows\System32\Drivers\citbootdriver.sys"
REM  REM #install the new one
REM  start /d "%bootdriver_dir%\" /b citbootdriversetup.exe install
REM  cd "%package_bin%"

REM # give tagent a chance to do any other setup (such as the .env file and pcakey)
REM # and make sure it's successful before trying to start the trust agent
REM # NOTE: only the output from start-http-server is redirected to the logfile;
REM #       the stdout from the setup command will be displayed
ECHO. ==Call trustagent setup
>>"%logfile%" call "%trustagent_cmd%" setup
REM ECHO. ==Start trustagent service
REM call "%trustagent_cmd%" start

REM # optional: register tpm password with mtwilson so pull provisioning can
REM #           be accomplished with less reboots (no ownership transfer)
REM prompt_with_default REGISTER_TPM_PASSWORD       "Register TPM password with service to support asset tag automation? [y/n]" ${REGISTER_TPM_PASSWORD}
REM if [[ "$REGISTER_TPM_PASSWORD" == "y" || "$REGISTER_TPM_PASSWORD" == "Y" || "$REGISTER_TPM_PASSWORD" == "yes" ]]; then 
REM #	prompt_with_default ASSET_TAG_URL "Asset Tag Server URL: (https://[SERVER]:[PORT]/mtwilson/v2)" ${ASSET_TAG_URL}
REM 	prompt_with_default MTWILSON_API_USERNAME "Username:" ${MTWILSON_API_USERNAME}
REM 	prompt_with_default_password MTWILSON_API_PASSWORD "Password:" ${MTWILSON_API_PASSWORD}
REM     export MTWILSON_API_USERNAME MTWILSON_API_PASSWORD
REM #	# json='[{ "subject": "'$UUID'", "selection": "'$selectionUUID'"}]'
REM #	# wget --secure-protocol=SSLv3 --no-proxy --ca-certificate=$CERT_FILE_LOCATION --password=$password --user=$username --header="Content-Type: application/json" --post-data="$json"
REM #	TPM_PASSWORD=`read_property_from_file tpm.owner.secret /opt/trustagent/configuration/trustagent.properties`
REM 	export HARDWARE_UUID=`dmidecode |grep UUID | awk '{print $2}'`
REM #	echo "registering $TPM_PASSWORD to $UUID"
REM #	wget --secure-protocol=SSLv3 --no-proxy --no-check-certificate --auth-no-challenge --password=$ASSET_TAG_PASSWORD --user=$ASSET_TAG_USERNAME --header="Content-Type: application/json" --post-data='{"id":"'$UUID'","password":"'$TPM_PASSWORD'"}' "$ASSET_TAG_URL/host-tpm-passwords"
REM     /usr/local/bin/tagent setup register-tpm-password
REM fi

Cerebro
------------

This fork has bundled two repos cerebro + cebrebro-docker into one repository and also has updated a couple of libraries to resolve open CVEs. The docker file has hard coded SAP repositories, please replace them with the docker one, as SAP is just mirroring the docker repos for performance and rate limiting reasons.

cerebro is an open source(MIT License) elasticsearch web admin tool built using Scala, Play Framework, AngularJS and Bootstrap.

### Requirements

cerebro needs Java 11 or newer to run.

### Installation
- Download from [https://github.com/lmenezes/cerebro/releases](https://github.com/lmenezes/cerebro/releases)
- Extract files
- Run bin/cerebro(or bin/cerebro.bat if on Windows)
- Access on http://localhost:9000

### Docker compose

- Install Docker compose
- Spin up a running OpenSearch cluster with node(s) and a dashboard running in a network "net1" (or possibly on the host network).
- Spin up a built image of this repository running in the same network "net1".

> The examples in this directory, at the moment, do not contain end-2-end setup with an OpenSearch cluster!


### Configuration

#### HTTP server address and port
You can run cerebro listening on a different host and port(defaults to 0.0.0.0:9000):

```
bin/cerebro -Dhttp.port=1234 -Dhttp.address=127.0.0.1
```

#### LDAP config

LDAP can be configured using environment variables. If you typically run cerebro using docker,
you can pass a file with all the env vars. The file would look like:

```bash
# Set it to ldap to activate ldap authorization
AUTH_TYPE=ldap

# Your ldap url
LDAP_URL=ldap://exammple.com:389

LDAP_BASE_DN=OU=users,DC=example,DC=com

# Usually method should  be "simple" otherwise, set it to the SASL mechanisms
LDAP_METHOD=simple

# user-template executes a string.format() operation where
# username is passed in first, followed by base-dn. Some examples
#  - %s => leave user untouched
#  - %s@domain.com => append "@domain.com" to username
#  - uid=%s,%s => usual case of OpenLDAP
LDAP_USER_TEMPLATE=%s@example.com

# User identifier that can perform searches
LDAP_BIND_DN=admin@example.com
LDAP_BIND_PWD=adminpass

# Group membership settings (optional)

# If left unset LDAP_BASE_DN will be used
# LDAP_GROUP_BASE_DN=OU=users,DC=example,DC=com

# Attribute that represent the user, for example uid or mail
# LDAP_USER_ATTR=mail

# If left unset LDAP_USER_TEMPLATE will be used
# LDAP_USER_ATTR_TEMPLATE=%s

# Filter that tests membership of the group. If this property is empty then there is no group membership check
# AD example => memberOf=CN=mygroup,ou=ouofthegroup,DC=domain,DC=com
# OpenLDAP example => CN=mygroup
# LDAP_GROUP=memberOf=memberOf=CN=mygroup,ou=ouofthegroup,DC=domain,DC=com

```

You can the pass this file as argument using:

```bash
 docker run -p 9000:9000 --env-file env-ldap  lmenezes/cerebro
```

There are some examples of configuration in the [examples folder](./examples).

#### Other settings

Other settings are exposed through the **conf/application.conf** file found on the application directory.

It is also possible to use an alternate configuration file defined on a different location:

```
bin/cerebro -Dconfig.file=/some/other/dir/alternate.conf
```

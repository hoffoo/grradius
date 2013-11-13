grradius
========

Radius testing client

usage
=======

```sh
usage: grr [options] action username password

Options must be passed as arguments or in the config file:
 -c,--config <arg>       config file. [./config]
 -d,--dictionary <arg>   dictionary file. [./dictionary]
 -h,--host <arg>         radius server
 -s,--secret <arg>       radius secret
 -pap,--pap              pap authentication
 -chap,--chap            chap authentication [default]
 -help                   print usage
```

notes
=======

The config file can contain any of the command line args. You can also
specify additional attributes in the following form:

```groovy
attributes = [
	"NAS-IP-Address": "1.1.1.1",
	"Service-Type": "Login-User",
	"NAS-Identifier": "grr"
]
```

Response and debugging info is printed to stdout



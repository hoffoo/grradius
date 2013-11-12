
package grr

@Grab(group='org.tinyradius', module='tinyradius', version='0.9.9')
@Grab(group='commons-cli', module='commons-cli', version='1.2')

import org.tinyradius.packet.*
import org.tinyradius.*
import org.tinyradius.util.RadiusClient
import org.apache.commons.cli.Options
import org.apache.commons.cli.PosixParser
import org.apache.commons.cli.HelpFormatter

class Client
{
	Map cfg

	Client(Map cfg)
	{
		client = new RadiusClient(cfg.host, cfg.secret)
		this.cfg = cfg
	}
	
	// regular authenticate, returns a response packet
	RadiusPacket auth(username, password)
	{
		def request = authReq(username, password)
		def result = client.authenticate(request)
		client.close()

		return result
	}

	AccessRequest authReq(username, password)
	{
		def request = new AccessRequest(username, password)
		request.setAuthProtocol(AUTH_TYPE)
		request.addAttribute("NAS-Identifier", cfg.nasIdentifier)
		request.addAttribute("NAS-IP-Address", cfg.nasIp)
		request.addAttribute("Service-Type", cfg.serviceType)

		return request
	}
}


def options = new Options()
options.addOption("c", "config", true, "config file")
options.addOption("h", "host", true, "radius server")
options.addOption("s", "secret", true, "radius secret")

def parser = new PosixParser()
args = parser.parse(options, args)
if (args.getArgList().size() != 2) {
	help(options)
	return
}

File cfgFile

if (!args.hasOption("config"))
	cfgFile = new File("config")
else 
	cfgFile = new File(args.getOptionValue('config'))

def cfgParsed = cfgFile.exists()
if (cfgParsed)
	cfgParsed = new ConfigSlurper().parse(cfgFile.toURL())

Map cfg = [:]
def addRequiredCfg = 
{ opt ->
	if (args.hasOption(opt))
		cfg[opt] = args.getOptionValue(opt)
	else if (!cfgParsed || !cfgParsed[opt])
		println "missing reqired option: $opt"
	else
		cfg[opt] = cfgParsed[opt]
}

addRequiredCfg("host")
addRequiredCfg("secret")



void help(options)
{
	def formatter = new HelpFormatter()
	formatter.printHelp(
		80,
		"grr [options] action username password",
		"\nOptions must be passed as arguments or in the config file:",
		options, 
		""
	)
}

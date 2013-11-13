package grr

@Grab(group='org.tinyradius', module='tinyradius', version='0.9.9')
@Grab(group='commons-cli', module='commons-cli', version='1.2')


import org.tinyradius.packet.*
import org.tinyradius.*
import org.tinyradius.dictionary.*
import org.tinyradius.util.RadiusClient
import org.apache.commons.cli.Options
import org.apache.commons.cli.PosixParser
import org.apache.commons.cli.HelpFormatter


def options = new Options()
options.addOption("c", "config", true, "config file. [./config]")
options.addOption("h", "host", true, "radius server")
options.addOption("s", "secret", true, "radius secret")
options.addOption("d", "dictionary", true, "dictionary file. [./dictionary]")
options.addOption("chap", "chap", false, "chap authentication [default]")
options.addOption("pap", "pap", false, "pap authentication")
options.addOption("help", false, "print usage")

def parser = new PosixParser()
args = parser.parse(options, args)

// missing args, print help and quit
if (args.getArgList().size() == 0 || args.hasOption("help")) {
	help(options)
	return
}

// use default config or passed -c arg

def configFile
def dictFile
try {
	configFile = new ConfigSlurper().parse(new File(args.getOptionValue("config") ?: "config").toURL())
} catch (IOException ex) { println ("couldnt load  config file") }
try {
	dictFile = new FileInputStream(new File(args.getOptionValue("dictionary")?:"dictionary"))
} catch (IOException ex) { println ("couldn't load dictionary file")}

Map cfg = [:]  // our final config - will be passed to Client

// add attributes and dictionary into our into o
if (configFile?.attributes)
	cfg.attributes = configFile.attributes
	cfg.dictionary = dictFile

def populateConfig = 
{
	boolean required = true

	def addCfgProp = 
	{ opt, val = null ->
		if (val != null)
			cfg[opt] = val
		else if(args.hasOption(opt))
			cfg[opt] = args.getOptionValue(opt)
		else if (!configFile[opt]) {
			if (required)
				println "missing reqired option: $opt"
		}
		else
			cfg[opt] = configFile[opt]
	}

	addCfgProp("host")
	addCfgProp("secret")
	addCfgProp("action", args.getArgList()[0])
	addCfgProp("username", args.getArgList()[1])
	addCfgProp("password", args.getArgList()[2])

	required = false

	addCfgProp("pap", args.hasOption("pap"))
	addCfgProp("chap", true)
}()


new Client(cfg)

class Client
{
	Map cfg
	Dictionary dictionary
	RadiusClient client
	def authMethod

	Client(Map cfg)
	{
		client = new RadiusClient(cfg.host, cfg.secret)
		dictionary = DictionaryParser.parseDictionary(cfg.dictionary)

		this.cfg = cfg

		if (cfg.pap)
			this.authMethod = AccessRequest.AUTH_PAP
		else if (cfg.chap)
			this.authMethod = AccessRequest.AUTH_CHAP

		try {
			this[cfg.action]()
		} catch (org.tinyradius.util.RadiusException r) {
			println("probably invalid secret")
		} catch (ExceptionInInitializerError exp) {
			println("error staring tinyradius - make sure your jar has default dictionary in it: see http://github.com/hoffoo/grrradius/issues/1")
		} catch (MissingPropertyException mp) {
			println("unknown action ${cfg.action}")
		}
	}
	
	def auth =
	{
		def request = new AccessRequest(cfg.username, cfg.password)
		request.setDictionary(dictionary)
		request.setAuthProtocol(authMethod)

		cfg.attributes.each
		{ k, v -> 
			request.addAttribute(k, v)
		}

		def result = client.authenticate(request)
		client.close()

		return result
	}
}

static void help(options)
{
	// sort long options at the bottom
	def comparator = [compare:
	{ a, b ->
		if (a.opt.size() + b.opt.size() == 2) {
			a.opt < b.opt ? -1 : 1
		} else {
			a.opt.size() > b.opt.size() ? 1 : -1
		}

	}] as Comparator

	def formatter = new HelpFormatter()
	formatter.setOptionComparator(comparator)

	formatter.printHelp(
		80,
		"grr [options] action username password",
		"\nOptions must be passed as arguments or in the config file:",
		options, 
		""
	)
}

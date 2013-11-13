package grr

@Grab(group='org.tinyradius', module='tinyradius', version='0.9.9')
@Grab(group='commons-cli', module='commons-cli', version='1.2')


import org.tinyradius.packet.*
import org.tinyradius.*
import org.tinyradius.dictionary.*
import org.tinyradius.util.RadiusClient
import org.apache.commons.cli.Options
import org.apache.commons.cli.GnuParser
import org.apache.commons.cli.HelpFormatter


def options = new Options()
options.addOption("c", "config", true, "config file. [./config]")
options.addOption("h", "host", true, "radius server")
options.addOption("s", "secret", true, "radius secret")
options.addOption("d", "dictionary", true, "dictionary file. [./dictionary]")
options.addOption("help", false, "print usage")

def parser = new GnuParser()
args = parser.parse(options, args)

// missing args, print help and quit
if (args.getArgList().size() == 0 || args.hasOption("help")) {
	help(options)
	return
}

// TODO can make this less clear by combining cfgParsed() and dictionary()?

// use default config or passed -c arg
def cfgParsed =
{
	File cfgFile
	if (args.hasOption("config"))
		cfgFile = new File(args.getOptionValue("config"))
	else 
		cfgFile = new File("config")

	// slurp config file or cfgParsed is false
	cfgParsed = cfgFile.exists()
	if (cfgParsed)
		cfgParsed = new ConfigSlurper().parse(cfgFile.toURL())

	return cfgParsed
}()

// read default dictionary or passed -d arg
def dictionary =
{
	File dictFile
	if (args.hasOption("dictionary"))
		dictFile = new File(args.getOptionValue("dictionary"))
	else
		dictFile = new File("dictionary")

	dictionary = dictFile.exists()
	if (dictionary)
		dictionary = new FileInputStream(dictFile)

	return dictionary
}()

Map cfg = [:]  // our final config - will be passed to Client

// add attributes and dictionary into our into o
if (cfgParsed && cfgParsed.attributes)
	cfg.attributes = cfgParsed.attributes

if (dictionary)
	cfg.dictionary = dictionary

def populateConfig = 
{
	def addRequiredCfg = 
	{ opt, val = null ->
		if (val != null)
			cfg[opt] = val
		else if(args.hasOption(opt))
			cfg[opt] = args.getOptionValue(opt)
		else if (cfgParsed == false|| !cfgParsed[opt])	
			println "missing reqired option: $opt"
		else
			cfg[opt] = cfgParsed[opt]
	}

	addRequiredCfg("host")
	addRequiredCfg("secret")
	addRequiredCfg("action", args.getArgList()[0])
	addRequiredCfg("username", args.getArgList()[1])
	addRequiredCfg("password", args.getArgList()[2])
}()


new Client(cfg)

class Client
{
	Map cfg
	Dictionary dictionary
	RadiusClient client

	Client(Map cfg)
	{
		client = new RadiusClient(cfg.host, cfg.secret)
		dictionary = DictionaryParser.parseDictionary(cfg.dictionary)

		this.cfg = cfg

		try {
			this[cfg.action]()
		} catch (ExceptionInInitializerError exp) {
			println("error staring tinyradius - make sure your jar has default dictionary in it: see http://github.com/hoffoo/grr-radius/issues/1")
		} catch (MissingPropertyException) {
			println("unknown action ${cfg.action}")
		}
	}
	
	def auth =
	{
		def request = new AccessRequest(cfg.username, cfg.password)
		request.setDictionary(dictionary)
		request.setAuthProtocol(AccessRequest.AUTH_CHAP)

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

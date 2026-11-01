@GrabConfig(systemClassLoader = true)
@Grab('info.picocli:picocli:4.7.6')
import groovy.cli.picocli.CliBuilder
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class TextScan {
    String rootDir
    String pattern
    boolean regex
    Pattern compiledPattern

    boolean run() {
        println "CLI parsed successfully: dir=${rootDir}, pattern=${pattern}, regex=${regex}"
        return true
    }
	
    static Pattern buildPattern(String text, boolean asRegex) {
        return asRegex ? Pattern.compile(text) : Pattern.compile(Pattern.quote(text))
    }

    static void main(String[] args) {
        def cli = new CliBuilder(usage: 'groovy TextScan.groovy -d <directory> -p <pattern> [options]')
        cli.d(names: ['-d', '--dir'], args: 1, required: true, argName: 'directory', 'Folder to scan')
        cli.p(names: ['-p', '--pattern'], args: 1, required: true, argName: 'pattern', 'Text to search for')
        cli.r(names: ['-r', '--regex'], 'Treat the pattern as a regular expression')
        cli.h(names: ['-h', '--help'], usageHelp: true, 'Show this help and exit')

        def options = cli.parse(args)
        if (!options) { System.exit(1) }

        if (options.h) {
            cli.usage()
            System.exit(0)    
        }

	Pattern compiled
	try {
            compiled = buildPattern(options.p, options.regex)
        } catch (PatternSyntaxException e) {
 	    System.err.println "Error: invalid regex '${options.p}': ${e.description}"
            cli.usage()
            System.exit(1)	
        }
	
	if (compiled.matcher('').find()) {
	    System.err.println "Error: pattern '${options.p}' matches empty text; make it match at least one character."
	    cli.usage()
 	    System.exit(1)
	}

        TextScan textScan = new TextScan(
            rootDir: options.d,
            pattern: options.p,
            regex: options.regex ?: false
        )

        if (!textScan.run()) {
            System.exit(1)
        }
    }
}

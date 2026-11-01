@GrabConfig(systemClassLoader = true)
@Grab('info.picocli:picocli:4.7.6')
import groovy.cli.picocli.CliBuilder
import groovy.io.FileType
import groovy.io.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class TextScan {
    static final String CHARSET = 'UTF-8'
    static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern('yyyy-MM-dd HH:mm:ss.SSS')

    String rootDir
    String pattern
    boolean regex
    Pattern compiledPattern

    boolean run() {
        if (!isDirValid(rootDir)) {
            System.err.println "Error: '${rootDir}' does not exist, is not a directory, or is not readable."
            return false
        }

        log('INFO', "Start: scanning ${rootDir} for \"${pattern}\" (${regex ? 'regex' : 'literal'} mode)")

        List results = scan()
        report(results)
        return true
    }

    static Pattern buildPattern(String text, boolean asRegex) {
        return asRegex ? Pattern.compile(text) : Pattern.compile(Pattern.quote(text))
    }

    boolean isDirValid(String path) {
        if (!path) { return false }
        def dir = new File(path)
        return dir.isDirectory() && dir.canRead()
    }

    Map searchFile(File file) {
        int count = 0
        List lines = []

        file.eachLine(CHARSET) { String line, int lineNumber ->
            Matcher m = compiledPattern.matcher(line)
            StringBuilder preview = new StringBuilder()
            List columns = []

            while (m.find()) {
                columns << m.start() + 1
                m.appendReplacement(preview, Matcher.quoteReplacement("[[${m.group()}]]".toString()))
            }

            if (columns) {
                m.appendTail(preview)
                count += columns.size()
                lines << [line: lineNumber, columns: columns, preview: preview.toString()]
            }
        }

        return [count: count, lines: lines]
    }

    List scan() {
        Path root = Paths.get(rootDir).toAbsolutePath().normalize()
        List files = []

        root.toFile().traverse(
            type: FileType.FILES,
            nameFilter: ~/.*\.txt$/,
            preDir: { File dir ->
                Path dirPath = dir.toPath()
                if (dir.name.startsWith('.') || Files.isSymbolicLink(dirPath)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }
        ) { File file ->
            Path filePath = file.toPath()
            if (!file.name.startsWith('.') && !Files.isSymbolicLink(filePath)) {
                files << file
            }
        }

        return files.collect { File file ->
            Path relPath = root.relativize(file.toPath().toAbsolutePath().normalize())
            Map result = [file: relPath.toString(), relPath: relPath, hits: 0, lines: [], error: null]

            try {
                Map found = searchFile(file)
                result.hits = found.count
                result.lines = found.lines
            } catch (Exception e) {
                result.error = e.message ?: e.class.simpleName
            }
            return result
        }.sort { it.file }
    }

    void report(List results) {
        results.each { r ->
            if (r.error) {
                log('ERROR', "${r.file} -> could not read: ${r.error}")
            } else if (r.hits) {
                log('INFO', "${r.file} -> ${r.hits} ${plural(r.hits, 'hit')} (${lineLabel(r.lines)})")
                r.lines.each { l ->
                    log('INFO', "    line ${l.line}, col ${l.columns.join(', ')}: ${l.preview}")
                }
            } else {
                log('INFO', "${r.file} -> (no match)")
            }
        }
    }

    void log(String level, String message) {
        String line = "${LocalDateTime.now().format(LOG_TIME)} ${level.padRight(5)} ${message}"
        if (level == 'INFO') {
            println line
        } else {
            System.err.println line
        }
    }

    static String plural(int n, String word) {
        return n == 1 ? word : "${word}s"
    }

    static String lineLabel(List lines) {
        List numbers = lines*.line
        return "${numbers.size() == 1 ? 'line' : 'lines'} ${numbers.join(', ')}"
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
            System.err.println "Error: pattern '${options.p}' matches empty text."
            cli.usage()
            System.exit(1)
        }

        TextScan textScan = new TextScan(
            rootDir: options.d,
            pattern: options.p,
            regex: options.regex ?: false,
            compiledPattern: compiled
        )

        if (!textScan.run()) {
            cli.usage()
            System.exit(1)
        }
    }
}

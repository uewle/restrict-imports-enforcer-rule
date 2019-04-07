package de.skuzzle.enforcer.restrictimports.parser;

import com.google.common.base.Preconditions;
import de.skuzzle.enforcer.restrictimports.analyze.RuntimeIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public class SourceFileParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(SourceFileParser.class);

    private final LineSupplier supplier;

    public SourceFileParser(LineSupplier supplier) {
        this.supplier = supplier;
    }

    public ParsedFile analyze(Path sourceFilePath, SourceLineParser lineParser) {
        LOGGER.trace("Analyzing {} for imports", sourceFilePath);

        final List<ParsedFile.ImportStatement> imports = new ArrayList<>();

        try (final Stream<String> lines = this.supplier.lines(sourceFilePath)) {

            final Iterable<String> lineIt = lines.map(String::trim)::iterator;

            int row = 1;
            final String fileName = getFileName(sourceFilePath);
            String fqcn = "";
            String packageName = "";

            for (final Iterator<String> it = lineIt.iterator(); it.hasNext(); ++row) {
                final String line = it.next();
                if (line.isEmpty()) {
                    continue;
                }

                final Optional<String> packageDeclaration = lineParser.parsePackage(line);
                if (packageDeclaration.isPresent()) {
                    Preconditions.checkState(packageName.isEmpty(), "found duplicate package statement in '%s'", sourceFilePath);
                    // package ...; statement

                    // INVARIANT: our own package name occurs in the first non-empty line
                    // of the java source file (after trimming leading comments)
                    packageName = packageDeclaration.get();
                    fqcn = guessFQCN(packageName, fileName);
                    LOGGER.trace("Guessed full qualified class name from {} and {}: '{}'", packageName, fileName, fqcn);
                    continue;
                }

                final List<ParsedFile.ImportStatement> importDeclarations = lineParser.parseImport(line, row);
                imports.addAll(importDeclarations);
                if (importDeclarations.isEmpty()) {
                    // as we are skipping empty (and comment) lines, by the time we
                    // encounter a non-import line we can stop processing this file
                    break;
                }
            }

            return new ParsedFile(sourceFilePath, packageName, fqcn, imports);
        } catch (final IOException e) {
            throw new RuntimeIOException(String.format(
                    "Encountered IOException while analyzing %s for banned imports",
                    sourceFilePath), e);
        }
    }

    private String guessFQCN(String packageName, String sourceFileName) {
        return packageName.isEmpty()
                ? sourceFileName
                : packageName + "." + sourceFileName;
    }

    private String getFileName(Path file) {
        final String s = file.getFileName().toString();
        final int i = s.lastIndexOf(".");
        return s.substring(0, i);
    }
}

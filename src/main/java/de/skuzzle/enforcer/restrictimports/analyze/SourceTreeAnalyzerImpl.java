package de.skuzzle.enforcer.restrictimports.analyze;

import com.google.common.base.Preconditions;
import de.skuzzle.enforcer.restrictimports.io.RuntimeIOException;
import de.skuzzle.enforcer.restrictimports.parser.ImportStatementParser;
import de.skuzzle.enforcer.restrictimports.parser.lang.LanguageSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import java.util.stream.Stream;

final class SourceTreeAnalyzerImpl implements SourceTreeAnalyzer {

    private final Map<String, LanguageSupport> sourceFileParsers;
    private final ImportAnalyzer importAnalyzer;

    SourceTreeAnalyzerImpl() {
        this.importAnalyzer = new ImportAnalyzer();
        final ServiceLoader<LanguageSupport> serviceProvider = ServiceLoader.load(LanguageSupport.class);
        final Map<String, LanguageSupport> parsers = new HashMap<>();
        serviceProvider.forEach(parser -> {
            parser.getSupportedFileExtensions().forEach(extension -> {
                final String normalizedExtension = extension.startsWith(".")
                        ? extension.toLowerCase()
                        : "." + extension.toLowerCase();

                if (parsers.put(normalizedExtension, parser) != null) {
                    throw new IllegalStateException(
                            "There are multiple parsers to handle file extension: " + normalizedExtension);
                }
            });
        });
        Preconditions.checkState(!parsers.isEmpty(), "No SourceFileParser instances found!");
        this.sourceFileParsers = parsers;
    }

    @Override
    public AnalyzeResult analyze(AnalyzerSettings settings, BannedImportGroups groups) {
        final ImportStatementParser fileParser = ImportStatementParser.defaultInstance(settings.getSourceFileCharset());

        final Collection<MatchedFile> srcMatches = analyzeDirectories(groups, fileParser, settings.getSrcDirectories());
        final Collection<MatchedFile> testMatches = analyzeDirectories(groups, fileParser, settings.getTestDirectories());

        return AnalyzeResult.builder()
                .withMatches(srcMatches)
                .withMatchesInTestCode(testMatches)
                .build();
    }

    private Collection<MatchedFile> analyzeDirectories(BannedImportGroups groups, ImportStatementParser fileParser, Iterable<Path> directories) {
        final Collection<MatchedFile> matchedFiles = new ArrayList<>();
        for (final Path srcDir : directories) {
            listFiles(srcDir, new SourceFileMatcher())
                    .map(sourceFile -> fileParser.parse(sourceFile, sourceFileParsers.get(getFileExtension(sourceFile))))
                    .map(parsedFile -> importAnalyzer.matchFile(parsedFile, groups))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(matchedFiles::add);
        }
        return matchedFiles;
    }

    private Stream<Path> listFiles(Path root, Predicate<Path> filter) {
        try {
            if (!Files.exists(root)) {
                return Stream.empty();
            }

            return Files.find(root, Integer.MAX_VALUE, (path, bfa) -> filter.test(path));
        } catch (final IOException e) {
            throw new RuntimeIOException("Encountered IOException while listing files of " + root, e);
        }
    }

    private boolean isFile(Path path) {
        return !Files.isDirectory(path);
    }

    private class SourceFileMatcher implements Predicate<Path> {

        @Override
        public boolean test(Path path) {
            if (!isFile(path)) {
                return false;
            }

            final String extension = getFileExtension(path);

            if (extension == null) {
                return false;
            }

            return sourceFileParsers.containsKey(extension.toLowerCase());
        }
    }

    private String getFileExtension(Path path) {
        final String lowerCaseFileName = path.getFileName().toString().toLowerCase();
        final int index = lowerCaseFileName.lastIndexOf(".");

        if (index == -1) {
            return null;
        }

        return lowerCaseFileName.substring(index);
    }
}

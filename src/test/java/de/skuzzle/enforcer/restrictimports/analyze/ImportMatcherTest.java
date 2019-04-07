package de.skuzzle.enforcer.restrictimports.analyze;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import de.skuzzle.enforcer.restrictimports.parser.LineSupplier;
import de.skuzzle.enforcer.restrictimports.parser.ParsedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import de.skuzzle.enforcer.restrictimports.analyze.lang.JavaLineParser;

public class ImportMatcherTest {

    private final ImportMatcher subject = new ImportMatcher();

    private final ParsedFile parsedFile = parsedFile("File", "de.skuzzle.test",
            "de.skuzzle.sample.Test",
            "foo.bar.xyz",
            "de.skuzzle.sample.Test2",
            "de.skuzzle.sample.Test3",
            "de.foo.bar.Test");

    private ParsedFile parsedFile(String className, String packageName, String... lines) {
        final String fqcn = packageName + "." + className;
        final Path path = mock(Path.class);
        final Path fileName = mock(Path.class);
        when(path.getFileName()).thenReturn(fileName);
        when(fileName.toString()).thenReturn(className + ".java");
        final List<ParsedFile.ImportStatement> imports = new ArrayList<>();

        for (int lineNumber = 0; lineNumber < lines.length; ++lineNumber) {
            imports.add(new ParsedFile.ImportStatement(lines[lineNumber], lineNumber));
        }
        return new ParsedFile(path, packageName, fqcn, imports);

    }

    @Test
    public void testMatchBannedOnly() throws Exception {
        final BannedImportGroups groups = BannedImportGroups.builder()
                .withGroup(BannedImportGroup.builder()
                        .withBasePackages("foo.bar", "de.skuzzle.test.*")
                        .withBannedImports("de.skuzzle.sample.*"))
                .build();
           final Optional<MatchedFile> matches = this.subject.matchFile(parsedFile, groups);

        final PackagePattern expectedMatchedBy = PackagePattern
                .parse("de.skuzzle.sample.*");
        final ImmutableList<MatchedImport> expected = ImmutableList.of(
                new MatchedImport(0, "de.skuzzle.sample.Test", expectedMatchedBy),
                new MatchedImport(2, "de.skuzzle.sample.Test2", expectedMatchedBy),
                new MatchedImport(3, "de.skuzzle.sample.Test3", expectedMatchedBy));

        assertThat(matches.get().getMatchedImports()).isEqualTo(expected);
    }

    @Test
    public void testMatchWithInclude() throws Exception {
        final BannedImportGroups groups = BannedImportGroups.builder()
                .withGroup(BannedImportGroup.builder()
                        .withBasePackages("**")
                        .withBannedImports("de.skuzzle.sample.*")
                        .withAllowedImports("de.skuzzle.sample.Test2", "de.skuzzle.sample.Test4"))
                .build();
        final Optional<MatchedFile> matches = this.subject.matchFile(this.parsedFile, groups);

        final PackagePattern expectedMatchedBy = PackagePattern
                .parse("de.skuzzle.sample.*");
        final ImmutableList<MatchedImport> expected = ImmutableList.of(
                new MatchedImport(0, "de.skuzzle.sample.Test", expectedMatchedBy),
                new MatchedImport(3, "de.skuzzle.sample.Test3", expectedMatchedBy));

        assertThat(matches.get().getMatchedImports()).isEqualTo(expected);
    }

    @Test
    public void testExcludeFile() throws Exception {
        final BannedImportGroups groups = BannedImportGroups.builder()
                .withGroup(BannedImportGroup.builder()
                        .withBasePackages("**")
                        .withBannedImports("foo")
                        .withExcludedClasses("de.skuzzle.test.File")
                        .withReason("message"))
                .build();

        final Optional<MatchedFile> matches = this.subject.matchFile(this.parsedFile, groups);
        assertThat(matches).isEmpty();
    }

    @Test
    public void testLeadingEmptyLinesDefaultPackages() throws Exception {
        final ParsedFile parsedFile = parsedFile("File", "",
                "de.skuzzle.sample.Test");

        final BannedImportGroups groups = BannedImportGroups.builder()
                .withGroup(BannedImportGroup.builder()
                        .withBasePackages("**")
                        .withBannedImports("de.skuzzle.sample.**"))
                .build();

        assertThat(subject.matchFile(parsedFile, groups).get().getMatchedImports()).first()
                .isEqualTo(new MatchedImport(0,
                        "de.skuzzle.sample.Test", PackagePattern.parse("de.skuzzle.sample.**")));
    }

    @Test
    public void testExcludeWholeFileByBasePackage() throws Exception {
        final Optional<MatchedFile> matches = this.subject.matchFile(this.parsedFile,
                BannedImportGroups.builder()
                        .withGroup(BannedImportGroup.builder()
                                .withBasePackages("de.foo.bar")
                                .withBannedImports("de.skuzzle.sample.*"))
                        .build());
        assertThat(matches).isEmpty();
    }
}

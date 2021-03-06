package de.skuzzle.enforcer.restrictimports.analyze;

import java.util.Arrays;
import java.util.Objects;

final class PackagePatternImpl implements PackagePattern {

    private static final String STATIC_PREFIX = "static ";
    private final String[] parts;
    private final boolean staticc;

    PackagePatternImpl(String s) {
        this.staticc = s.startsWith(STATIC_PREFIX);
        if (staticc) {
            s = s.substring(STATIC_PREFIX.length());
        }

        this.parts = s.split("\\.");
        checkParts(s, this.parts);
    }

    private void checkParts(String full, String[] parts) {
        if (full.startsWith(".") || full.endsWith(".")) {
            throw new IllegalArgumentException(String.format("The pattern '%s' contains an empty part", full));
        }
        for (int i = 0; i < parts.length; i++) {
            final String part = parts[i];
            checkCharacters(full, part, i);
        }
    }

    private void checkCharacters(String full, String part, int partIndex) {
        final char[] chars = part.toCharArray();

        if (part.isEmpty()) {
            throw new IllegalArgumentException(String.format("The pattern '%s' contains an empty part", full));
        } else if ("*".equals(part) || "**".equals(part) || "'*'".equals(part)) {
            return;
        } else if (part.contains("*")) {
            throw new IllegalArgumentException(String.format(
                    "The pattern '%s' contains a part which mixes wildcards and normal characters", full));
        } else if (partIndex == 0 && "static".equals(part)) {
            return;
        } else if (!Character.isJavaIdentifierStart(chars[0])) {
            throw new IllegalArgumentException(String.format(
                    "The pattern '%s' contains a non-identifier character '%s'", full, chars[0]));
        }

        for (int i = 1; i < chars.length; i++) {
            final char c = chars[i];
            if (!Character.isJavaIdentifierPart(c)) {
                throw new IllegalArgumentException(String.format(
                        "The pattern '%s' contains a non-identifier character '%s'", full, chars[i]));
            }
        }
    }

    @Override
    public boolean matches(PackagePattern packagePattern) {
        if (packagePattern == this) {
            return true;
        } else if (packagePattern instanceof PackagePatternImpl) {
            final PackagePatternImpl ppi = (PackagePatternImpl) packagePattern;
            return matchesInternal(ppi.staticc, ppi.parts, this.staticc, this.parts);
        }
        return matches(packagePattern.toString());
    }

    @Override
    public boolean matches(String packageName) {
        final boolean matchIsStatic = packageName.startsWith(STATIC_PREFIX);
        if (matchIsStatic) {
            packageName = packageName.substring(STATIC_PREFIX.length());
        }
        final String[] matchParts = packageName.split("\\.");
        return matchesInternal(matchIsStatic, matchParts, this.staticc, this.parts);
    }

    private boolean matchesInternal(boolean matchIsStatic, String[] matchParts,
            boolean partsIsStatic, String[] parts) {
        if (matchIsStatic != partsIsStatic) {
            return false;
        } else if (parts.length > matchParts.length) {
            // if the pattern is longer than the string to match, match cant be true
            return false;
        }

        int patternIndex = 0;
        int matchIndex = 0;
        for (; patternIndex < parts.length
                && matchIndex < matchParts.length; ++patternIndex) {
            final String patternPart = this.parts[patternIndex];
            final String matchPart = matchParts[matchIndex];

            if ("**".equals(patternPart)) {
                if (patternIndex + 1 < parts.length) {
                    final String nextPatternPart = parts[patternIndex + 1];
                    while (matchIndex < matchParts.length
                            && !matchParts(nextPatternPart, matchParts[matchIndex])) {
                        ++matchIndex;
                    }
                } else {
                    matchIndex = matchParts.length;
                }
            } else if (matchParts(patternPart, matchPart)) {
                ++matchIndex;
            } else {
                return false;
            }
        }

        return patternIndex == parts.length && matchIndex == matchParts.length;
    }

    private static boolean matchParts(String patternPart, String matchPart) {
        if ("*".equals(patternPart) || "**".equals(patternPart)) {
            return true;
        } else if ("'*'".equals(patternPart)) {
            return matchPart.equals("*");
        }
        return patternPart.equals(matchPart);
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        if (staticc) {
            result.append(STATIC_PREFIX);
        }
        result.append(String.join(".", this.parts));
        return result.toString();
    }

    @Override
    public int compareTo(PackagePattern other) {
        final PackagePatternImpl o = (PackagePatternImpl) other;
        if (isMoreSpecificThan(o)) {
            return 1;
        } else if (o.isMoreSpecificThan(this)) {
            return -1;
        }
        return 0;
    }

    private boolean isMoreSpecificThan(PackagePattern other) {
        final PackagePatternImpl o = (PackagePatternImpl) other;

        final int numOfStarStarThis = count("**", this.parts);
        final int numOfStarStarOther = count("**", o.parts);

        if (numOfStarStarThis < numOfStarStarOther) {
            return true;
        } else if (numOfStarStarThis > numOfStarStarOther) {
            return false;
        }

        final int numOfStarThis = count("*", this.parts);
        final int numOfStarOther = count("*", o.parts);

        if (numOfStarThis < numOfStarOther) {
            return true;
        } else if (numOfStarThis > numOfStarOther) {
            return false;
        }

        final String thisLastPart = parts[this.parts.length - 1];
        final String otherLastPart = o.parts[o.parts.length - 1];

        if (thisLastPart.equals("**")) {
            return this.parts.length > o.parts.length;
        } else if (thisLastPart.equals("*")) {
            return otherLastPart.equals("**");
        }

        return parts.length > o.parts.length;
    }

    private int count(String s, String[] arr) {
        return (int) Arrays.stream(arr).filter(s::equals).count();
    }

    @Override
    public boolean isStatic() {
        return staticc;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(parts), staticc);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof PackagePatternImpl
                && this.staticc == ((PackagePatternImpl) obj).staticc
                && Arrays.equals(this.parts, ((PackagePatternImpl) obj).parts);
    }
}

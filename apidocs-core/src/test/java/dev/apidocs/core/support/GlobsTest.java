package dev.apidocs.core.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlobsTest {

    @Test
    void matchesDoubleStarAcrossDirectories() {
        assertThat(Globs.toPattern("**/legacy/**").matcher("src/main/java/com/x/legacy/Old.java").matches()).isTrue();
        assertThat(Globs.toPattern("**/legacy/**").matcher("legacy/Old.java").matches()).isTrue();
        assertThat(Globs.toPattern("**/*Test.java").matcher("a/b/FooTest.java").matches()).isTrue();
        assertThat(Globs.toPattern("**/*Test.java").matcher("FooTest.java").matches()).isTrue();
    }

    @Test
    void singleStarStaysInsideOneSegment() {
        assertThat(Globs.toPattern("*.java").matcher("A.java").matches()).isTrue();
        assertThat(Globs.toPattern("*.java").matcher("a/A.java").matches()).isFalse();
        assertThat(Globs.toPattern("src/?.java").matcher("src/A.java").matches()).isTrue();
    }

    @Test
    void escapesRegexCharacters() {
        assertThat(Globs.toPattern("a+b(1).java").matcher("a+b(1).java").matches()).isTrue();
        assertThat(Globs.toPattern("a.java").matcher("aXjava").matches()).isFalse();
    }
}

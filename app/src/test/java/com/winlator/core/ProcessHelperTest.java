package com.winlator.core;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

public class ProcessHelperTest {
    @Test
    public void removesQuotesAroundWindowsPaths() {
        assertArrayEquals(
            new String[]{"C:\\Program Files\\Example\\game.exe", "-windowed"},
            ProcessHelper.splitCommand("\"C:\\Program Files\\Example\\game.exe\" -windowed")
        );
    }

    @Test
    public void preservesQuotedArgumentsAndEmptyArguments() {
        assertArrayEquals(
            new String[]{"launcher", "--name", "My Game", ""},
            ProcessHelper.splitCommand("launcher --name \"My Game\" \"\"")
        );
    }

    @Test
    public void supportsEscapedSpacesAndSingleQuotes() {
        assertArrayEquals(
            new String[]{"C:\\Games\\My Game\\game.exe", "two words"},
            ProcessHelper.splitCommand("C:\\Games\\My\\ Game\\game.exe 'two words'")
        );
    }

    @Test
    public void rejectsUnterminatedQuotes() {
        assertThrows(IllegalArgumentException.class, () -> ProcessHelper.splitCommand("program \"unfinished"));
    }
}

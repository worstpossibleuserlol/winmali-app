package com.winlator.core;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ProcessHelperTest {
    @Test
    public void parsesProcStatWhenProcessNameContainsClosingParentheses() {
        ProcessHelper.PStat process = ProcessHelper.parseProcessStat(
            "4321 (wine server) helper) S 77 1 2 3 4");

        assertNotNull(process);
        assertEquals(4321, process.pid);
        assertEquals("wine server) helper", process.shortName);
        assertEquals(ProcessHelper.PState.SLEEPING, process.state);
        assertEquals(77, process.parentPID);
    }

    @Test
    public void rejectsMalformedProcStatLines() {
        assertNull(ProcessHelper.parseProcessStat("not a proc stat line"));
        assertNull(ProcessHelper.parseProcessStat("4321 (broken)"));
    }

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

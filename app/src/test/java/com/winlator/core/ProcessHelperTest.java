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
    public void buildsCpuAffinityMaskWithoutByteOverflow() {
        assertEquals(0x80000001, ProcessHelper.getAffinityMask("0,31"));
        assertEquals("80000001", ProcessHelper.getAffinityMaskAsHexString("0,31"));
        assertEquals(0, ProcessHelper.getAffinityMask("bad,-1,32"));
    }

    @Test
    public void affinityHexMaskPreservesCpuIndicesAboveThirtyOne() {
        assertEquals("180000000", ProcessHelper.getAffinityMaskAsHexString("31,32"));
        assertEquals("8000000000000000", ProcessHelper.getAffinityMaskAsHexString("63"));
        assertEquals(0, ProcessHelper.getAffinityMaskAsLong("bad,-1,64"));
    }

    @Test
    public void collectsOnlyActualDescendantsNotUnrelatedHigherPids() {
        ProcessHelper.PStat unrelated = process(900, 1);
        ProcessHelper.PStat child = process(120, 100);
        ProcessHelper.PStat grandchild = process(121, 120);
        ProcessHelper.PStat otherBranch = process(122, 999);

        java.util.List<ProcessHelper.PStat> result = ProcessHelper.collectDescendants(
            java.util.Arrays.asList(unrelated, child, grandchild, otherBranch), 100);

        assertEquals(2, result.size());
        assertEquals(120, result.get(0).pid);
        assertEquals(121, result.get(1).pid);
    }

    private static ProcessHelper.PStat process(int pid, int parentPid) {
        ProcessHelper.PStat process = new ProcessHelper.PStat();
        process.pid = pid;
        process.parentPID = parentPid;
        return process;
    }

    @Test
    public void preservesEmptyProcArgumentsWithoutDiscardingLaterArguments() {
        assertEquals(java.util.Arrays.asList("wine", "", "--version"),
            ProcessHelper.parseCommandLine("wine\0\0--version\0"));
    }

    @Test
    public void ignoresOnlyTheTrailingProcCommandLineTerminator() {
        assertEquals(java.util.Arrays.asList("wine", "--version"),
            ProcessHelper.parseCommandLine("wine\0--version\0"));
    }

    @Test
    public void rejectsUnterminatedQuotes() {
        assertThrows(IllegalArgumentException.class, () -> ProcessHelper.splitCommand("program \"unfinished"));
    }
}

package dev.mrakells.gtnhtelegram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class CompletionCollectorTest {
    private final CompletionCollector collector = new CompletionCollector(4000L);

    @Test
    public void mergesTeammatesWhoReportTheSameCompletion() {
        assertTrue(collector.report(TestData.completion(100, "q1", "Quest", "smak", "Alice"), 0L));
        assertFalse(collector.report(TestData.completion(100, "q1", "Quest", "smak", "Bob"), 2900L));
        assertFalse(collector.report(TestData.completion(100, "q1", "Quest", "smak", "Alice"), 3000L));

        assertTrue("still inside the window", collector.drain(3999L).isEmpty());

        List<QuestCompletion> ready = collector.drain(4000L);
        assertEquals(1, ready.size());
        assertEquals(Arrays.asList("Alice", "Bob"), ready.get(0).playerNames());
        assertEquals(Arrays.asList("uuid-of-Alice", "uuid-of-Bob"), ready.get(0).playerUuids());
        assertEquals("smak", ready.get(0).teamName());
        assertEquals(Integer.valueOf(7), ready.get(0).teamId());

        assertTrue("drained completions are gone", collector.drainAll().isEmpty());
    }

    @Test
    public void keepsDifferentCompletionsApart() {
        collector.report(TestData.completion(100, "q1", "First", "", "Alice"), 0L);
        collector.report(TestData.completion(100, "q2", "Second", "", "Alice"), 0L);
        // The same repeatable quest completed again later is a new completion.
        collector.report(TestData.completion(500, "q1", "First", "", "Alice"), 1000L);

        assertEquals(2, collector.drain(4000L).size());
        assertEquals(1, collector.drain(5000L).size());
    }

    @Test
    public void drainAllIgnoresTheWindow() {
        collector.report(TestData.completion(100, "q1", "Quest", "", "Alice"), 0L);

        assertEquals(1, collector.drainAll().size());
    }
}

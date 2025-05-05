package eu.nebulous.ems.test;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static eu.nebulous.ems.test.TestUtils.color;
import static eu.nebulous.ems.test.TestUtils.getResultColor;

@Slf4j
public class NebulousTestExecutionListener implements TestExecutionListener {

    record TestRecord(String parentDisplayName,
                      TestIdentifier testIdentifier,
                      TestExecutionResult result,
                      String skipReason)
    {
        public static TestRecord of(String parentDisplayName,
                                    TestIdentifier testIdentifier,
                                    TestExecutionResult result,
                                    String skipReason)
        {
            return new TestRecord(parentDisplayName, testIdentifier, result, skipReason);
        }
    }

    private final List<TestRecord> testResults = new LinkedList<>();
    private final Map<TestIdentifier,ReportEntry> additionalData = new HashMap<>();
    private String currentContainer;

    public void testPlanExecutionStarted(TestPlan testPlan) {
        testResults.clear();
        additionalData.clear();
        currentContainer = null;
    }

    public void testPlanExecutionFinished(TestPlan testPlan) {
        printTestResults();
    }

    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (! testIdentifier.isTest()) return;
        testResults.add(TestRecord.of(
                currentContainer, testIdentifier, null, reason));
    }

    public void executionStarted(TestIdentifier testIdentifier) {
        if (testIdentifier.isContainer() && testIdentifier.getParentId().isPresent()) {
            currentContainer = testIdentifier.getDisplayName();
        }
        printTestDivider(testIdentifier.getDisplayName());
    }

    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (testIdentifier.isContainer()) {
            currentContainer = testIdentifier.getDisplayName();
        } else {
            testResults.add(TestRecord.of(
                    currentContainer, testIdentifier, testExecutionResult, null));
        }
    }

    public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {
        additionalData.put(testIdentifier, entry);
    }

    // ------------------------------------------------------------------------

    private void printTestDivider(String s) {
        log.info(color("INFO", "━".repeat(80)));
        log.info(color("INFO", "━━━━━  {}"), s);
    }

    private void printTestResults() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n")
                .append(color("WHITE", """
                        ════════════════════════════════════════════════════════════════════════════════
                        ══════════                    NEBULOUS TEST RESULTS                   ══════════
                        ════════════════════════════════════════════════════════════════════════════════
                        
                        """));
        final AtomicReference<String> container = new AtomicReference<>(null);
        final AtomicInteger counter = new AtomicInteger(0);
        testResults.forEach(testRecord -> {
            if (! StringUtils.equals(container.get(), testRecord.parentDisplayName)) {
                container.set(testRecord.parentDisplayName);
                counter.set(1);
                sb.append(color("YELLOW", "━━━━━ "+container.get()+" ━━━━━")).append("\n");
            } else {
                counter.incrementAndGet();
            }
            if (testRecord.result!=null) {
                sb.append(String.format("%4d. Test ", counter.get()))
                        .append(color("", testRecord.testIdentifier.getDisplayName()))
                        .append(" : ")
                        .append(color(getResultColor(testRecord.result), testRecord.result.getStatus().name()))
                        .append("\n");
                if (testRecord.result.getStatus()==TestExecutionResult.Status.FAILED
                        && testRecord.result.getThrowable().isPresent())
                {
                    Throwable t = testRecord.result.getThrowable().get();
                    StackTraceElement frame = t.getStackTrace()[0];
                    String s = String.format("%s (%d): %s", t.getClass().getSimpleName(), frame.getLineNumber(), t.getMessage());
                    sb.append(color("WHITE", "         Error: "))
                            .append(color("EXCEPTION", s))
                            .append("\n");
                }
            } else {
                sb.append(String.format("%4d. Test ", counter.get()))
                        .append(color("", testRecord.testIdentifier.getDisplayName()))
                        .append(" : ")
                        .append(color("WARN", "Skipped"))
                        .append(color("", testRecord.skipReason))
                        .append("\n");
            }

            ReportEntry entry = additionalData.get(testRecord.testIdentifier);
            if (entry!=null) {
                String infoStr = entry.getKeyValuePairs().get("value");
                if (StringUtils.isNotBlank(infoStr))
                    sb.append(color("GREY", "         info: "))
                            .append(color(TestUtils.getFirstTerm(infoStr), infoStr))
                            .append("\n");
            }
        });
        log.info(sb.toString());
    }
}

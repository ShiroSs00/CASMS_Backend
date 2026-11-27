package com.casms.testng.listener;

import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe TestNG listener for CASMS automated suites.
 * Tracks execution counts, durations, and failure details across parallel classes.
 */
public class CasmsTestListener implements ITestListener, ISuiteListener {

    private final AtomicInteger passed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();
    private long suiteStartMs;

    @Override
    public void onStart(ISuite suite) {
        suiteStartMs = System.currentTimeMillis();
        System.out.println("============================================================");
        System.out.println("[CASMS TestNG] Starting suite: " + suite.getName());
        System.out.println("============================================================");
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        passed.incrementAndGet();
        long elapsed = result.getEndMillis() - result.getStartMillis();
        System.out.printf(
                "[PASS] %s.%s (%d ms)%n",
                result.getTestClass().getRealClass().getSimpleName(),
                result.getMethod().getMethodName(),
                elapsed);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        failed.incrementAndGet();
        Throwable err = result.getThrowable();
        System.err.printf(
                "[FAIL] %s.%s -> %s%n",
                result.getTestClass().getRealClass().getSimpleName(),
                result.getMethod().getMethodName(),
                err != null ? err.getMessage() : "Unknown failure");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        skipped.incrementAndGet();
        System.out.printf(
                "[SKIP] %s.%s%n",
                result.getTestClass().getRealClass().getSimpleName(),
                result.getMethod().getMethodName());
    }

    @Override
    public void onFinish(ISuite suite) {
        long duration = System.currentTimeMillis() - suiteStartMs;
        System.out.println("============================================================");
        System.out.printf(
                "[CASMS TestNG] Suite '%s' finished in %d ms | Passed=%d, Failed=%d, Skipped=%d%n",
                suite.getName(), duration, passed.get(), failed.get(), skipped.get());
        System.out.println("============================================================");
    }
}

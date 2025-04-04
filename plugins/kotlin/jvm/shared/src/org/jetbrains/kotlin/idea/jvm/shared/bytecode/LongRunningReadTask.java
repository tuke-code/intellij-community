// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.jvm.shared.bytecode;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LongRunningReadTask<RequestInfo, ResultData> {
    public enum State {
        NOT_INITIALIZED,
        INITIALIZED,
        STARTED,
        FINISHED,
        FINISHED_WITH_ACTUAL_DATA
    }

    private final Disposable parentDisposable;
    private ProgressIndicator progressIndicator = null;
    private RequestInfo requestInfo = null;
    private State currentState = State.NOT_INITIALIZED;

    protected LongRunningReadTask(Disposable disposable) {
        parentDisposable = disposable;
    }

    /** Should be executed in GUI thread */
    public boolean shouldStart(@Nullable LongRunningReadTask<RequestInfo, ResultData> previousTask) {
        ThreadingAssertions.assertEventDispatchThread();

        if (currentState != State.INITIALIZED) {
            throw new IllegalStateException("Task should be initialized state. Call init() method.");
        }

        // Cancel previous task if necessary
        if (previousTask != null && previousTask.currentState == State.STARTED) {
            if (requestInfo == null || !requestInfo.equals(previousTask.requestInfo)) {
                previousTask.progressIndicator.cancel();
            }
        }

        if (requestInfo == null) {
            if (previousTask != null && (previousTask.currentState == State.FINISHED_WITH_ACTUAL_DATA || previousTask.currentState == State.FINISHED)) {
                previousTask.hideResultOnInvalidLocation();
            }

            return false;
        }

        if (previousTask != null) {
            if (previousTask.currentState == State.STARTED) {
                // Start new task only if previous isn't working on similar request
                return !requestInfo.equals(previousTask.requestInfo);
            }
            else if (previousTask.currentState == State.FINISHED_WITH_ACTUAL_DATA) {
                if (requestInfo.equals(previousTask.requestInfo)) {
                    return false;
                }
            }
        }

        return true;
    }

    /** Should be executed in GUI thread */
    public final void run() {
        ThreadingAssertions.assertEventDispatchThread();

        if (currentState != State.INITIALIZED) {
            throw new IllegalStateException("Task should be initialized with init() method");
        }

        if (requestInfo == null) {
            throw new IllegalStateException("Invalid request for task beginning");
        }

        currentState = State.STARTED;

        beforeRun();

        progressIndicator = new ProgressIndicatorBase();

        final RequestInfo requestInfoCopy = cloneRequestInfo(requestInfo);

        ApplicationManager.getApplication().executeOnPooledThread(
                () -> runWithWriteActionPriority(
                        progressIndicator, parentDisposable,
                        () -> {
                            ResultData resultData = null;
                            try {
                                resultData = processRequest(requestInfoCopy);
                            } finally {
                                // Back to GUI thread for submitting result
                                final ResultData finalResult = resultData;
                                ApplicationManager.getApplication().invokeLater(() -> resultReady(finalResult));
                            }
                        }));
    }

    /**
     * Executed in GUI Thread.
     *
     * @return true if new request was successfully created, false if request is invalid and shouldn't be started
     */
    public final boolean init() {
        ThreadingAssertions.assertEventDispatchThread();

        requestInfo = prepareRequestInfo();
        currentState = State.INITIALIZED;

        return requestInfo != null;
    }

    private void resultReady(ResultData resultData) {
        ThreadingAssertions.assertEventDispatchThread();

        currentState = State.FINISHED;

        if (resultData != null) {
            RequestInfo actualInfo = prepareRequestInfo();
            if (requestInfo.equals(actualInfo)) {
                currentState = State.FINISHED_WITH_ACTUAL_DATA;
                onResultReady(actualInfo, resultData);
            }
        }
    }

    /**
     * This method should prepare a copy of request object that will be used during the processing of the
     * request in thread pool. If RequestInfo class is thread safe this method can return
     * a reference to already constructed object.
     *
     * By default this method will reconstruct request object with prepareRequestInfo method.
     *
     * Executed in GUI Thread.
     */
    @SuppressWarnings("UnusedParameters")
    protected @NotNull RequestInfo cloneRequestInfo(@NotNull RequestInfo requestInfo) {
        RequestInfo cloneRequestInfo = prepareRequestInfo();
        if (cloneRequestInfo == null) {
            throw new IllegalStateException("Cloned request object can't be null");
        }

        return cloneRequestInfo;
    }

    /**
     * Executed in GUI Thread.
     *
     * @return null if current request is invalid and task shouldn't be executed.
     */
    protected abstract @Nullable RequestInfo prepareRequestInfo();

    /**
     * Executed in GUI Thread.
     */
    protected void hideResultOnInvalidLocation() {}

    /**
     * Executed in GUI Thread right before task run. Do nothing by default.
     */
    protected void beforeRun() {}

    /**
     * Executed in thread pool under read lock with write priority.
     */
    protected abstract @Nullable ResultData processRequest(@NotNull RequestInfo requestInfo);

    /**
     * Executed in GUI Thread. Do nothing by default.
     */
    protected void onResultReady(@NotNull RequestInfo requestInfo, @Nullable ResultData resultData) {}

    /**
     * Execute action with immediate stop when write lock is required.
     *
     * {@link ProgressIndicatorUtils#runWithWriteActionPriority(Runnable, ProgressIndicator)}
     *
     */
    public static void runWithWriteActionPriority(
            final @NotNull ProgressIndicator indicator,
            @NotNull Disposable parentDisposable,
            final @NotNull Runnable action
    ) {
        Disposable disposable = Disposer.newDisposable();
        Disposer.register(parentDisposable, disposable);
        ApplicationListener listener = new ApplicationListener() {
            @Override
            public void beforeWriteActionStart(@NotNull Object action) {
                indicator.cancel();
            }
        };
        final Application application = ApplicationManager.getApplication();
        try {
            application.addApplicationListener(listener, disposable);
            ProgressManager.getInstance().runProcess(() -> application.runReadAction(action), indicator);
        }
        finally {
            Disposer.dispose(disposable);
        }
    }
}

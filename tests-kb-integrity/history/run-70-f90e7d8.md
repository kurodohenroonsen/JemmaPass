# CI · tests/kb-integrity · f90e7d8 · run #70

- Gradle outcome: **failure**
- Unit tests: **454** run · 22 failed · 0 errors · 0 skipped
- Debug APK: ✅ app-debug.apk (228 MB)
- Kotlin compile errors: 0

## Failing tests
```
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-022 three versions behind takes the patches in order, whatever their order in the manifest: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-029 there is no way back to an older version: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-025 a cumulative patch is preferred to a longer chain: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-023 a hole in the chain means no path: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-028 a loop in the manifest ends with no path instead of running for ever: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-020 a phone already up to date needs nothing: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-026 between two paths of the same length the lighter one wins: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-021 one version behind takes the single patch: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-024 no manifest entry means no path: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-031 the same step listed twice uses the lighter file: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-030 an unknown installed version has no path: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.PatchChainResolverTest.UC-UPD-027 a dead end does not hide the real path: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.DownloadIntegrityTest.UC-UPD-006 the right size without an expected fingerprint is unverified, never complete: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.DownloadIntegrityTest.UC-UPD-001 exact size and matching fingerprint is complete: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.DownloadIntegrityTest.UC-UPD-005 a file larger than announced is corrupt: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.DownloadIntegrityTest.UC-UPD-008 fingerprints are compared without case and without surrounding spaces: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.DownloadIntegrityTest.UC-UPD-004 the right size with another fingerprint is corrupt: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.DownloadIntegrityTest.UC-UPD-010 a fingerprint that is not 64 hexadecimal characters never gives complete: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.DownloadIntegrityTest.UC-UPD-002 a file missing five percent is truncated, whatever the fingerprint says: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.DownloadIntegrityTest.UC-UPD-007 the right size whose fingerprint was not computed is unverified: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.DownloadIntegrityTest.UC-UPD-003 an empty or absent file is truncated: kotlin.NotImplementedError: An operation is not implemented.
be.heyman.android.jemmapassdemo.kbupdate.DownloadIntegrityTest.UC-UPD-009 an unknown expected size never gives complete: kotlin.NotImplementedError: An operation is not implemented.
```
## Other errors
```
DownloadIntegrityTest > UC-UPD-006 the right size without an expected fingerprint is unverified, never complete FAILED
DownloadIntegrityTest > UC-UPD-001 exact size and matching fingerprint is complete FAILED
DownloadIntegrityTest > UC-UPD-005 a file larger than announced is corrupt FAILED
DownloadIntegrityTest > UC-UPD-008 fingerprints are compared without case and without surrounding spaces FAILED
DownloadIntegrityTest > UC-UPD-004 the right size with another fingerprint is corrupt FAILED
DownloadIntegrityTest > UC-UPD-010 a fingerprint that is not 64 hexadecimal characters never gives complete FAILED
DownloadIntegrityTest > UC-UPD-002 a file missing five percent is truncated, whatever the fingerprint says FAILED
DownloadIntegrityTest > UC-UPD-007 the right size whose fingerprint was not computed is unverified FAILED
DownloadIntegrityTest > UC-UPD-003 an empty or absent file is truncated FAILED
DownloadIntegrityTest > UC-UPD-009 an unknown expected size never gives complete FAILED
PatchChainResolverTest > UC-UPD-022 three versions behind takes the patches in order, whatever their order in the manifest FAILED
PatchChainResolverTest > UC-UPD-029 there is no way back to an older version FAILED
PatchChainResolverTest > UC-UPD-025 a cumulative patch is preferred to a longer chain FAILED
PatchChainResolverTest > UC-UPD-023 a hole in the chain means no path FAILED
PatchChainResolverTest > UC-UPD-028 a loop in the manifest ends with no path instead of running for ever FAILED
PatchChainResolverTest > UC-UPD-020 a phone already up to date needs nothing FAILED
PatchChainResolverTest > UC-UPD-026 between two paths of the same length the lighter one wins FAILED
PatchChainResolverTest > UC-UPD-021 one version behind takes the single patch FAILED
PatchChainResolverTest > UC-UPD-024 no manifest entry means no path FAILED
PatchChainResolverTest > UC-UPD-031 the same step listed twice uses the lighter file FAILED
PatchChainResolverTest > UC-UPD-030 an unknown installed version has no path FAILED
PatchChainResolverTest > UC-UPD-027 a dead end does not hide the real path FAILED
> Task :app:testDebugUnitTest FAILED
* What went wrong:
Execution failed for task ':app:testDebugUnitTest'.
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:testDebugUnitTest'.
BUILD FAILED in 8m 41s
```
## Log tail
```
	at org.gradle.internal.execution.steps.BuildCacheStep.executeWithoutCache(BuildCacheStep.java:189)
	at org.gradle.internal.execution.steps.BuildCacheStep.lambda$execute$1(BuildCacheStep.java:75)
	at org.gradle.internal.Either$Right.fold(Either.java:175)
	at org.gradle.internal.execution.caching.CachingState.fold(CachingState.java:62)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:73)
	at org.gradle.internal.execution.steps.BuildCacheStep.execute(BuildCacheStep.java:48)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:46)
	at org.gradle.internal.execution.steps.StoreExecutionStateStep.execute(StoreExecutionStateStep.java:35)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.executeBecause(SkipUpToDateStep.java:75)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.lambda$execute$2(SkipUpToDateStep.java:53)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:53)
	at org.gradle.internal.execution.steps.SkipUpToDateStep.execute(SkipUpToDateStep.java:35)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:37)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsFinishedStep.execute(MarkSnapshottingInputsFinishedStep.java:27)
	at org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep.executeDelegate(ResolveIncrementalCachingStateStep.java:49)
	at org.gradle.internal.execution.steps.ResolveIncrementalCachingStateStep.executeDelegate(ResolveIncrementalCachingStateStep.java:27)
	at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:71)
	at org.gradle.internal.execution.steps.AbstractResolveCachingStateStep.execute(AbstractResolveCachingStateStep.java:39)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:65)
	at org.gradle.internal.execution.steps.ResolveChangesStep.execute(ResolveChangesStep.java:36)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:107)
	at org.gradle.internal.execution.steps.ValidateStep.execute(ValidateStep.java:56)
	at org.gradle.internal.execution.steps.AbstractCaptureStateBeforeExecutionStep.execute(AbstractCaptureStateBeforeExecutionStep.java:64)
	at org.gradle.internal.execution.steps.AbstractCaptureStateBeforeExecutionStep.execute(AbstractCaptureStateBeforeExecutionStep.java:43)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.executeWithNonEmptySources(AbstractSkipEmptyWorkStep.java:125)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:61)
	at org.gradle.internal.execution.steps.AbstractSkipEmptyWorkStep.execute(AbstractSkipEmptyWorkStep.java:36)
	at org.gradle.internal.execution.steps.legacy.MarkSnapshottingInputsStartedStep.execute(MarkSnapshottingInputsStartedStep.java:38)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:36)
	at org.gradle.internal.execution.steps.LoadPreviousExecutionStateStep.execute(LoadPreviousExecutionStateStep.java:23)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.execute(HandleStaleOutputsStep.java:75)
	at org.gradle.internal.execution.steps.HandleStaleOutputsStep.execute(HandleStaleOutputsStep.java:41)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.lambda$execute$0(AssignMutableWorkspaceStep.java:35)
	at org.gradle.api.internal.tasks.execution.TaskExecution$4.withWorkspace(TaskExecution.java:289)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:31)
	at org.gradle.internal.execution.steps.AssignMutableWorkspaceStep.execute(AssignMutableWorkspaceStep.java:22)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:40)
	at org.gradle.internal.execution.steps.ChoosePipelineStep.execute(ChoosePipelineStep.java:23)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.lambda$execute$2(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:67)
	at org.gradle.internal.execution.steps.ExecuteWorkBuildOperationFiringStep.execute(ExecuteWorkBuildOperationFiringStep.java:39)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:46)
	at org.gradle.internal.execution.steps.IdentityCacheStep.execute(IdentityCacheStep.java:34)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:48)
	at org.gradle.internal.execution.steps.IdentifyStep.execute(IdentifyStep.java:35)
	at org.gradle.internal.execution.impl.DefaultExecutionEngine$1.execute(DefaultExecutionEngine.java:64)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.executeIfValid(ExecuteActionsTaskExecuter.java:127)
	at org.gradle.api.internal.tasks.execution.ExecuteActionsTaskExecuter.execute(ExecuteActionsTaskExecuter.java:116)
	at org.gradle.api.internal.tasks.execution.ProblemsTaskPathTrackingTaskExecuter.execute(ProblemsTaskPathTrackingTaskExecuter.java:41)
	at org.gradle.api.internal.tasks.execution.FinalizePropertiesTaskExecuter.execute(FinalizePropertiesTaskExecuter.java:46)
	at org.gradle.api.internal.tasks.execution.ResolveTaskExecutionModeExecuter.execute(ResolveTaskExecutionModeExecuter.java:51)
	at org.gradle.api.internal.tasks.execution.SkipTaskWithNoActionsExecuter.execute(SkipTaskWithNoActionsExecuter.java:57)
	at org.gradle.api.internal.tasks.execution.SkipOnlyIfTaskExecuter.execute(SkipOnlyIfTaskExecuter.java:74)
	at org.gradle.api.internal.tasks.execution.CatchExceptionTaskExecuter.execute(CatchExceptionTaskExecuter.java:36)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.executeTask(EventFiringTaskExecuter.java:77)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:55)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter$1.call(EventFiringTaskExecuter.java:52)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.api.internal.tasks.execution.EventFiringTaskExecuter.execute(EventFiringTaskExecuter.java:52)
	at org.gradle.execution.plan.LocalTaskNodeExecutor.execute(LocalTaskNodeExecutor.java:42)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:331)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$InvokeNodeExecutorsAction.execute(DefaultTaskExecutionGraph.java:318)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.lambda$execute$0(DefaultTaskExecutionGraph.java:314)
	at org.gradle.internal.operations.CurrentBuildOperationRef.with(CurrentBuildOperationRef.java:85)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:314)
	at org.gradle.execution.taskgraph.DefaultTaskExecutionGraph$BuildOperationAwareExecutionAction.execute(DefaultTaskExecutionGraph.java:303)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.execute(DefaultPlanExecutor.java:459)
	at org.gradle.execution.plan.DefaultPlanExecutor$ExecutorWorker.run(DefaultPlanExecutor.java:376)
	at org.gradle.internal.concurrent.ExecutorPolicy$CatchAndRecordFailures.onExecute(ExecutorPolicy.java:64)
	at org.gradle.internal.concurrent.AbstractManagedExecutor$1.run(AbstractManagedExecutor.java:48)


BUILD FAILED in 8m 41s
55 actionable tasks: 55 executed
```

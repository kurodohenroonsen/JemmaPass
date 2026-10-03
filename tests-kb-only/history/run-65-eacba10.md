# CI · tests/kb-only · eacba10 · run #65

- Gradle outcome: **failure**
- Unit tests: **445** run · 9 failed · 0 errors · 0 skipped
- Debug APK: ✅ app-debug.apk (228 MB)
- Kotlin compile errors: 0

## Failing tests
```
be.heyman.android.jemmapassdemo.kbonly.NoClinicalCodeInSourceTest.UC-KB-003 no LOINC code is written in the source outside the document structure: java.lang.AssertionError: 91 LOINC literals in 8 files. Result codes, pregnancy codes and answer lists are IPS value sets of the knowledge base :
  ips/IpsBloodGroup.kt : 1 — l.23 LOINC "882-1"
  ips/IpsOfficialDisplays.kt : 7 — l.18 LOINC "882-1", l.19 LOINC "718-7", l.20 LOINC "2089-1", l.21 LOINC
be.heyman.android.jemmapassdemo.kbonly.NoClinicalCodeInSourceTest.UC-KB-001 no ATC code or ATC class is written in the source: java.lang.AssertionError: 28 ATC literals in 3 files. Drug classes and codes come from the knowledge base (official ATC index), never from the source :
  kb/AllergyKeywords.kt : 13 — l.34 ATC "J01CA01", l.37 ATC "J01DB01", l.40 ATC "J01DH02", l.45 ATC "J01EE01", …
  kb/JemmaProfileHydrator.kt : 13 —
be.heyman.android.jemmapassdemo.kbonly.NoClinicalCodeInSourceTest.UC-KB-002 no SNOMED CT concept is written in the source: java.lang.AssertionError: 71 SNOMED-like literals in 7 files. Vaccines, procedures, devices, routes, blood groups are IPS value sets of the knowledge base :
  ips/IpsBloodGroup.kt : 8 — l.29 SNOMED CT "278147001", l.30 SNOMED CT "278148006", l.31 SNOMED CT "278149003", l.32 SNOMED CT "278152006", …

be.heyman.android.jemmapassdemo.kbonly.UiLabelsInResourcesTest.UC-KB-014 no catalogue of the source carries a French or Japanese label: java.lang.AssertionError: 284 lines of pillars/ carry a translated label in Kotlin. Interface labels belong to the string resources (code_label_*), one text per language : [IpsAllergyTypeCatalog.kt:15, IpsAllergyTypeCatalog.kt:16, IpsAllergyTypeCatalog.kt:33, IpsAllergyTypeCatalog.kt:38, IpsContactP
be.heyman.android.jemmapassdemo.kbonly.UiLabelsInResourcesTest.UC-KB-011 each route has its interface label in the default, French and Japanese resources: java.lang.AssertionError: 15 route labels missing from the string resources : [values/code_label_sct_26643006, values/code_label_sct_47625008, values/code_label_sct_6064005, values/code_label_sct_34206005, values/code_label_sct_447694001, values-fr/code_label_sct_26643006, values-fr/code_label_sct_4
be.heyman.android.jemmapassdemo.kbonly.CodeLabelResolverTest.UC-KB-021 the text QR asks for the label in the language of the QR, not of the phone: java.lang.AssertionError: the builder never asked the resolver for the device 14106009 (asked : []). The label of a curated code must come from the resolver handed to build().
be.heyman.android.jemmapassdemo.kbonly.CodeLabelResolverTest.UC-KB-022 the label given by the resolver is the one printed: java.lang.AssertionError: the text QR does not print the label of the resolver :
🏥 === JEMMA CLINICAL SUMMARY (FR) ===

📟 [ DISPOSITIFS MÉDICAUX ]
  ▪️ Stimulateur cardiaque (pacemaker)

✅ JEMMA on-device · `_j 1.2`

be.heyman.android.jemmapassdemo.kbonly.CodeLabelResolverTest.UC-KB-025 the resources hold the route and device labels in French and Japanese: java.lang.AssertionError: expected:<Orale> but was:<null>
be.heyman.android.jemmapassdemo.kbonly.CodeLabelResolverTest.UC-KB-023 without a resolver the stored English label is printed, never a label kept in the code: java.lang.AssertionError: with no interface label the QR must fall back to the label stored with the entry :
🏥 === JEMMA 臨床サマリー (JA) ===

📟 [ 医療機器 ]
  ▪️ 心臓ペースメーカー

✅ JEMMA on-device · `_j 1.2`

```
## Other errors
```
CodeLabelResolverTest > UC-KB-021 the text QR asks for the label in the language of the QR, not of the phone FAILED
CodeLabelResolverTest > UC-KB-022 the label given by the resolver is the one printed FAILED
CodeLabelResolverTest > UC-KB-025 the resources hold the route and device labels in French and Japanese FAILED
CodeLabelResolverTest > UC-KB-023 without a resolver the stored English label is printed, never a label kept in the code FAILED
NoClinicalCodeInSourceTest > UC-KB-003 no LOINC code is written in the source outside the document structure FAILED
NoClinicalCodeInSourceTest > UC-KB-001 no ATC code or ATC class is written in the source FAILED
NoClinicalCodeInSourceTest > UC-KB-002 no SNOMED CT concept is written in the source FAILED
UiLabelsInResourcesTest > UC-KB-014 no catalogue of the source carries a French or Japanese label FAILED
UiLabelsInResourcesTest > UC-KB-011 each route has its interface label in the default, French and Japanese resources FAILED
> Task :app:testDebugUnitTest FAILED
* What went wrong:
Execution failed for task ':app:testDebugUnitTest'.
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:testDebugUnitTest'.
BUILD FAILED in 9m 7s
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


BUILD FAILED in 9m 7s
55 actionable tasks: 55 executed
```

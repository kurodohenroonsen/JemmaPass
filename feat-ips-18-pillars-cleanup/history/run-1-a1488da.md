# CI · feat/ips-18-pillars-cleanup · a1488da · run #1

- Gradle outcome: **failure**
- Unit tests: **0** run · 0 failed · 0 errors · 0 skipped
- Debug APK: ❌ not produced
- Kotlin compile errors: 0

## Other errors
```
* What went wrong:
```
## Log tail
```
Downloading https://services.gradle.org/distributions/gradle-8.14-bin.zip
.............10%.............20%.............30%.............40%.............50%.............60%.............70%.............80%.............90%..............100%

Welcome to Gradle 8.14!

Here are the highlights of this release:
 - Java 24 support
 - GraalVM Native Image toolchain selection
 - Enhancements to test reporting
 - Build Authoring improvements

For more details see https://docs.gradle.org/8.14/release-notes.html

To honour the JVM settings for this build a single-use Daemon process will be forked. For more on this, please refer to https://docs.gradle.org/8.14/userguide/gradle_daemon.html#sec:disabling_the_daemon in the Gradle documentation.
Daemon JVM discovery is an incubating feature.

FAILURE: Build failed with an exception.

* What went wrong:
Unable to download toolchain matching the requirements ({languageVersion=21, vendor=JetBrains, implementation=vendor-specific, nativeImageCapable=false}) from 'null', due to: No defined toolchain download url for LINUX on x86_64 architecture.

* Try:
> Learn more about toolchain auto-detection and auto-provisioning at https://docs.gradle.org/8.14/userguide/toolchains.html#sec:auto_detection.
> Learn more about toolchain repositories at https://docs.gradle.org/8.14/userguide/toolchains.html#sub:download_repositories.
> Run with --info or --debug option to get more log output.
> Run with --scan to get full insights.
> Get more help at https://help.gradle.org.

* Exception is:
org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainDownloadException: Unable to download toolchain matching the requirements ({languageVersion=21, vendor=JetBrains, implementation=vendor-specific, nativeImageCapable=false}) from 'null', due to: No defined toolchain download url for LINUX on x86_64 architecture.
	at org.gradle.launcher.daemon.toolchain.DaemonJavaToolchainProvisioningService.getBuildPlatformToolchainUrl(DaemonJavaToolchainProvisioningService.java:118)
	at org.gradle.launcher.daemon.toolchain.DaemonJavaToolchainProvisioningService.tryInstall(DaemonJavaToolchainProvisioningService.java:73)
	at org.gradle.jvm.toolchain.internal.JavaToolchainQueryService.downloadToolchain(JavaToolchainQueryService.java:229)
	at org.gradle.jvm.toolchain.internal.JavaToolchainQueryService.lambda$query$2(JavaToolchainQueryService.java:199)
	at java.base/java.util.Optional.orElseGet(Optional.java:364)
	at org.gradle.jvm.toolchain.internal.JavaToolchainQueryService.query(JavaToolchainQueryService.java:199)
	at org.gradle.jvm.toolchain.internal.JavaToolchainQueryService.lambda$resolveToolchain$1(JavaToolchainQueryService.java:166)
	at java.base/java.util.concurrent.ConcurrentHashMap.computeIfAbsent(ConcurrentHashMap.java:1708)
	at org.gradle.jvm.toolchain.internal.JavaToolchainQueryService.resolveToolchain(JavaToolchainQueryService.java:164)
	at org.gradle.jvm.toolchain.internal.JavaToolchainQueryService.lambda$findMatchingToolchain$0(JavaToolchainQueryService.java:142)
	at org.gradle.api.internal.provider.DefaultProvider.calculateOwnValue(DefaultProvider.java:73)
	at org.gradle.api.internal.provider.AbstractMinimalProvider.calculateOwnPresentValue(AbstractMinimalProvider.java:82)
	at org.gradle.api.internal.provider.AbstractMinimalProvider.get(AbstractMinimalProvider.java:102)
	at org.gradle.launcher.daemon.client.DefaultDaemonStarter.startDaemon(DefaultDaemonStarter.java:114)
	at org.gradle.launcher.daemon.client.DefaultDaemonConnector.doStartDaemon(DefaultDaemonConnector.java:219)
	at org.gradle.launcher.daemon.client.DefaultDaemonConnector.startSingleUseDaemon(DefaultDaemonConnector.java:244)
	at org.gradle.launcher.daemon.client.SingleUseDaemonClient.execute(SingleUseDaemonClient.java:60)
	at org.gradle.launcher.daemon.client.SingleUseDaemonClient.execute(SingleUseDaemonClient.java:37)
	at org.gradle.launcher.cli.RunBuildAction.run(RunBuildAction.java:56)
	at org.gradle.internal.Actions$RunnableActionAdapter.execute(Actions.java:167)
	at org.gradle.launcher.cli.DefaultCommandLineActionFactory$ParseAndBuildAction.execute(DefaultCommandLineActionFactory.java:371)
	at org.gradle.launcher.cli.DefaultCommandLineActionFactory$ParseAndBuildAction.execute(DefaultCommandLineActionFactory.java:335)
	at org.gradle.launcher.cli.DebugLoggerWarningAction.execute(DebugLoggerWarningAction.java:74)
	at org.gradle.launcher.cli.DebugLoggerWarningAction.execute(DebugLoggerWarningAction.java:30)
	at org.gradle.launcher.cli.WelcomeMessageAction.execute(WelcomeMessageAction.java:97)
	at org.gradle.launcher.cli.WelcomeMessageAction.execute(WelcomeMessageAction.java:40)
	at org.gradle.launcher.cli.NativeServicesInitializingAction.execute(NativeServicesInitializingAction.java:50)
	at org.gradle.launcher.cli.NativeServicesInitializingAction.execute(NativeServicesInitializingAction.java:27)
	at org.gradle.launcher.cli.ExceptionReportingAction.execute(ExceptionReportingAction.java:41)
	at org.gradle.launcher.cli.ExceptionReportingAction.execute(ExceptionReportingAction.java:26)
	at org.gradle.launcher.cli.DefaultCommandLineActionFactory$WithLogging.execute(DefaultCommandLineActionFactory.java:483)
	at org.gradle.launcher.Main.doAction(Main.java:35)
	at org.gradle.launcher.bootstrap.EntryPoint.run(EntryPoint.java:52)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at org.gradle.launcher.bootstrap.ProcessBootstrap.runNoExit(ProcessBootstrap.java:72)
	at org.gradle.launcher.bootstrap.ProcessBootstrap.run(ProcessBootstrap.java:39)
	at org.gradle.launcher.GradleMain.main(GradleMain.java:31)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.base/java.lang.reflect.Method.invoke(Method.java:569)
	at org.gradle.wrapper.GradleWrapperMain.main(SourceFile:70)

```

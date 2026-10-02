# CI · ag/0027-impl · 03a5b83 · run #42

- Gradle outcome: **failure**
- Unit tests: **0** run · 0 failed · 0 errors · 0 skipped
- Debug APK: ❌ not produced
- Kotlin compile errors: 246

## Kotlin errors
```
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:332:22 Unresolved reference 'arguments'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:332:43 Unresolved reference 'ARG_PROFILE_ID'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:332:60 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:332:67 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:333:29 Unresolved reference 'profilesRepo'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:334:23 Unresolved reference 'profilesRepo'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:335:20 Unresolved reference 'mode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:335:55 Unresolved reference 'index'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:336:21 Cannot infer type for type parameter 'K'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:336:21 Cannot infer type for type parameter 'V'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:336:21 This declaration needs opt-in. Its usage must be marked with '@kotlin.ExperimentalStdlibApi' or '@OptIn(kotlin.ExperimentalStdlibApi::class)'
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:336:58 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:336:58 Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch:
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:336:64 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:336:66 Unresolved reference 'removeAt'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:336:75 Unresolved reference 'index'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:350:9 Unresolved reference 'viewLifecycleOwner'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:352:26 Unresolved reference 'crossCheckHelper'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:355:24 Unresolved reference 'lang'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:359:23 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:362:19 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:364:23 Function invocation 'context(...)' expected.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:370:27 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:373:27 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:374:21 Unresolved reference 'pickedCode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:375:21 Unresolved reference 'pickedDisplay'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:376:21 Unresolved reference 'pickedCodeSystem'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:377:21 Unresolved reference 'renderSubstanceLabel'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:386:15 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:386:83 Unresolved reference 'lang'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:389:25 Unresolved reference 'getString'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:390:24 Unresolved reference 'lang'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:393:23 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:396:17 Unresolved reference 'pickedCode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:397:17 Unresolved reference 'pickedDisplay'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:398:17 Unresolved reference 'pickedCodeSystem'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:399:17 Unresolved reference 'pickedAtc'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:407:40 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:408:39 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:419:40 Unresolved reference 'lang'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:420:41 Unresolved reference 'lang'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:421:41 Unresolved reference 'lang'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:423:45 Unresolved reference 'lang'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:426:25 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:427:25 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:428:31 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:430:31 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:436:48 Unresolved reference 'pickedRoute'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:439:25 Unresolved reference 'pickedRoute'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:441:31 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:445:17 Unresolved reference 'renderSubstanceLabel'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:450:19 Unresolved reference 'childFragmentManager'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:456:15 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:457:9 Unresolved reference 'pickedRoute'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:462:9 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:462:53 Unresolved reference 'pickedRoute'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:463:9 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:463:58 Unresolved reference 'pickedRoute'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:464:9 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:464:56 Unresolved reference 'pickedRoute'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:465:9 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:465:61 Unresolved reference 'pickedRoute'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:466:9 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:466:56 Unresolved reference 'pickedRoute'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:472:20 Unresolved reference 'pickedStatus'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:475:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:477:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:477:83 Unresolved reference 'lang'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:482:15 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:484:60 Unresolved reference 'lang'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:485:36 Unresolved reference 'requireContext'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:489:17 Unresolved reference 'pickedStatus'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:491:23 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:499:19 Unresolved reference 'pickedEffectiveIso'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:501:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:502:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:504:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:505:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:510:15 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:517:23 Unresolved reference 'pickedEffectiveIso'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:517:43 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:517:43 Cannot infer type for type parameter 'R'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:517:47 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:526:13 Unresolved reference 'pickedEffectiveIso'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:528:19 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:530:21 Unresolved reference 'childFragmentManager'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:534:15 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:535:9 Unresolved reference 'pickedEffectiveIso'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:542:20 Unresolved reference 'pickedReasonCode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:544:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:545:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:546:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:548:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:549:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:549:58 Unresolved reference 'pickedReasonDisplay'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:550:13 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:555:15 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:556:25 Unresolved reference 'pickedCode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:556:44 Unresolved reference 'pickedCodeSystem'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:559:9 Unresolved reference 'viewLifecycleOwner'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:560:23 Unresolved reference 'kb'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:560:41 Unresolved reference 'kbManager'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:560:52 Unresolved reference 'pickedCode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:560:64 Unresolved reference 'pickedCodeSystem'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:561:19 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:564:29 Unresolved reference 'getString'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:565:28 Unresolved reference 'lang'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:569:27 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:571:21 Unresolved reference 'pickedReasonCode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:572:21 Unresolved reference 'pickedReasonDisplay'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:573:21 Unresolved reference 'pickedReasonSystem'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:576:23 Unresolved reference 'childFragmentManager'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:581:15 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:582:9 Unresolved reference 'pickedReasonCode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:583:9 Unresolved reference 'pickedReasonDisplay'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:584:9 Unresolved reference 'pickedReasonSystem'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:609:14 Unresolved reference 'submitGuard'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:610:19 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:613:9 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:615:20 Unresolved reference 'pickedCode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:615:32 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:615:39 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:616:23 Unresolved reference 'pickedDisplay'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:616:38 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:616:45 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:617:21 Unresolved reference 'pickedRoute'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:617:34 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:617:41 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:618:25 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:619:24 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:620:22 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:621:22 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:624:19 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:625:28 Unresolved reference 'requireContext'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:635:23 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:636:32 Unresolved reference 'requireContext'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:638:17 Unresolved reference 'binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:645:25 Unresolved reference 'pickedEffectiveIso'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:645:45 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:645:52 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:646:35 Unresolved reference 'ISO_DATE_REGEX'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:647:19 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:648:13 Unresolved reference 'pickedEffectiveIso'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:651:15 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:651:84 Unresolved reference 'mode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:651:96 Unresolved reference 'index'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:654:22 Unresolved reference 'pickedStatus'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:655:26 Unresolved reference 'pickedReasonCode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:655:57 Unresolved reference 'pickedCodeSystem'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:664:9 Unresolved reference 'viewLifecycleOwner'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:670:27 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:676:30 Unresolved reference 'crossCheckHelper'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:677:34 Unresolved reference 'pickedAtc'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:679:28 Unresolved reference 'lang'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:690:46 Unresolved reference 'totalHits'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:691:27 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:696:23 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:697:34 Unresolved reference 'allergyHits'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:697:65 Unresolved reference 'ddiHits'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:697:91 Unresolved reference 'drugDiseaseHits'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:698:27 Function invocation 'context(...)' expected.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:704:31 Argument type mismatch: actual type is 'Any', but 'Context' was expected.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:705:30 Argument type mismatch: actual type is 'Any', but 'CrossCheckResult' was expected.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:708:31 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:712:31 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:719:23 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:721:17 Function invocation 'context(...)' expected.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:721:26 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:721:26 Cannot infer type for type parameter 'R'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:721:26 Unresolved reference. None of the following candidates is applicable because of a receiver type mismatch:
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:721:30 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:722:40 Unresolved reference 'getString'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:723:44 Unresolved reference 'show'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:742:19 Function invocation 'context(...)' expected.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:747:83 Cannot infer type for type parameter 'R'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:748:13 Unresolved reference 'getString'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:751:33 Unresolved reference 'getString'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:753:17 Unresolved reference 'getString'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:756:37 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:757:17 Unresolved reference 'getString'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:758:15 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:761:15 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:762:36 Argument type mismatch: actual type is 'Any', but 'Context' was expected.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:773:9 Unresolved reference 'submitGuard'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:774:9 Unresolved reference '_binding'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:791:15 Unresolved reference 'TAG'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:792:9 Unresolved reference 'setFragmentResult'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:793:13 Unresolved reference 'RESULT_KEY'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:794:13 Inapplicable candidate(s): fun bundleOf(vararg pairs: Pair<String, Any?>): Bundle
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:795:17 Unresolved reference 'ARG_MODE'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:795:26 Cannot infer type for type parameter 'A'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:795:26 Cannot infer type for type parameter 'B'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:795:29 Unresolved reference 'mode'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:796:17 Unresolved reference 'ARG_INDEX'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:796:27 Cannot infer type for type parameter 'A'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:796:27 Cannot infer type for type parameter 'B'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:796:30 Unresolved reference 'index'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:797:17 Unresolved reference 'ARG_CODE'.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:797:26 Cannot infer type for type parameter 'A'. Specify it explicitly.
e: file:///home/runner/work/JemmaPass/JemmaPass/JemmaPassAndroidDemo/app/src/main/java/be/heyman/android/jemmapassdemo/ui/profile/medications/MedicationFormBottomSheet.kt:797:26 Cannot infer type for type parameter 'B'. Specify it explicitly.
```
## Other errors
```
> Task :app:compileDebugKotlin FAILED
* What went wrong:
Execution failed for task ':app:compileDebugKotlin'.
org.gradle.api.tasks.TaskExecutionException: Execution failed for task ':app:compileDebugKotlin'.
BUILD FAILED in 5m 41s
```
## Log tail
```
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
Caused by: org.jetbrains.kotlin.gradle.tasks.CompilationErrorException: Compilation error. See log for more details
	at org.jetbrains.kotlin.gradle.tasks.TasksUtilsKt.throwExceptionIfCompilationFailed(tasksUtils.kt:21)
	at org.jetbrains.kotlin.compilerRunner.btapi.BuildToolsApiCompilationWork.execute(BuildToolsApiCompilationWork.kt:295)
	at org.gradle.workers.internal.DefaultWorkerServer.execute(DefaultWorkerServer.java:63)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:66)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1$1.create(NoIsolationWorkerFactory.java:62)
	at org.gradle.internal.classloader.ClassLoaderUtils.executeInClassloader(ClassLoaderUtils.java:100)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1.lambda$execute$0(NoIsolationWorkerFactory.java:62)
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:44)
	at org.gradle.workers.internal.AbstractWorker$1.call(AbstractWorker.java:41)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:210)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$CallableBuildOperationWorker.execute(DefaultBuildOperationRunner.java:205)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:67)
	at org.gradle.internal.operations.DefaultBuildOperationRunner$2.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:167)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.execute(DefaultBuildOperationRunner.java:60)
	at org.gradle.internal.operations.DefaultBuildOperationRunner.call(DefaultBuildOperationRunner.java:54)
	at org.gradle.workers.internal.AbstractWorker.executeWrappedInBuildOperation(AbstractWorker.java:41)
	at org.gradle.workers.internal.NoIsolationWorkerFactory$1.execute(NoIsolationWorkerFactory.java:59)
	at org.gradle.workers.internal.DefaultWorkerExecutor.lambda$submitWork$0(DefaultWorkerExecutor.java:174)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runExecution(DefaultConditionalExecutionQueue.java:194)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.access$700(DefaultConditionalExecutionQueue.java:127)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner$1.run(DefaultConditionalExecutionQueue.java:169)
	at org.gradle.internal.Factories$1.create(Factories.java:31)
	at org.gradle.internal.work.DefaultWorkerLeaseService.withLocks(DefaultWorkerLeaseService.java:263)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:127)
	at org.gradle.internal.work.DefaultWorkerLeaseService.runAsWorkerThread(DefaultWorkerLeaseService.java:132)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.runBatch(DefaultConditionalExecutionQueue.java:164)
	at org.gradle.internal.work.DefaultConditionalExecutionQueue$ExecutionRunner.run(DefaultConditionalExecutionQueue.java:133)
	... 2 more


BUILD FAILED in 5m 41s
37 actionable tasks: 37 executed
```

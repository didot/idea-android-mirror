## Generating kotlin-compiler-daemon jars
The prebuilts included here are generated from the androidx tree. The source code is
contained in `frameworks/support/compose/compiler/compiler-daemon` and the jar is automatically
generated by the `createArchive` gradle task.

These prebuilts are used as a fallback when they can not be obtained from the maven
repository.
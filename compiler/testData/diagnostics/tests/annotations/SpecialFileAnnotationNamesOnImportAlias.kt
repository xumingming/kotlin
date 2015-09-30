// FILE: test.kt
<!SPECIAL_ANNOTATION_NAME_SHOULD_DENOTE!>@file:JvmName<!>
<!SPECIAL_ANNOTATION_NAME_SHOULD_DENOTE!>@file:JvmMultifileClass<!>
package test

import annotations.MyName as JvmName
import annotations.MyMultifileClass as JvmMultifileClass

// FILE: annotations.kt
package annotations

@Target(AnnotationTarget.FILE)
annotation class MyName

@Target(AnnotationTarget.FILE)
annotation class MyMultifileClass

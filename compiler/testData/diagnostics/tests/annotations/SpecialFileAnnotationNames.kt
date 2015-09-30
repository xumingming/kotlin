<!SPECIAL_ANNOTATION_NAME_SHOULD_DENOTE!>@file:JvmName<!>
<!SPECIAL_ANNOTATION_NAME_SHOULD_DENOTE!>@file:JvmMultifileClass<!>
package test

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class JvmName

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class JvmMultifileClass

@JvmName
@JvmMultifileClass
class A
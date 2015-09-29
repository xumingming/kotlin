<!SPECIAL_FILE_ANNOTATION_NAME_USED_INCORRECTLY!>@file:JvmName<!>
<!SPECIAL_FILE_ANNOTATION_NAME_USED_INCORRECTLY!>@file:JvmMultifileClass<!>
package test

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class JvmName

@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
annotation class JvmMultifileClass

@JvmName
@JvmMultifileClass
class A
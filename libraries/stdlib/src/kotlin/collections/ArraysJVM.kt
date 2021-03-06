@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("ArraysKt")

package kotlin.collections

import java.nio.charset.Charset

/**
 * Converts the contents of this byte array to a string using the specified [charset].
 */
@Deprecated("Use ByteArray.toString(Charset) instead.", ReplaceWith("this.toString(charset(charset))"), level = DeprecationLevel.ERROR)
public fun ByteArray.toString(charset: String): String = String(this, charset(charset))

/**
 * Converts the contents of this byte array to a string using the specified [charset].
 */
@kotlin.internal.InlineOnly
public inline fun ByteArray.toString(charset: Charset): String = String(this, charset)

/**
 * Returns a *typed* array containing all of the elements of this collection.
 *
 * Allocates an array of runtime type `T` having its size equal to the size of this collection
 * and populates the array with the elements of this collection.
 */
public inline fun <reified T> Collection<T>.toTypedArray(): Array<T> {
    val thisCollection = this as java.util.Collection<T>
    return thisCollection.toArray(arrayOfNulls<T>(thisCollection.size())) as Array<T>
}

/** Returns the array if it's not `null`, or an empty array otherwise. */
public inline fun <reified T> Array<out T>?.orEmpty(): Array<out T> = this ?: arrayOf<T>()

/** Internal unsafe construction of array based on reference array type */
internal fun <T> arrayOfNulls(reference: Array<T>, size: Int): Array<T> {
    return java.lang.reflect.Array.newInstance(reference.javaClass.componentType, size) as Array<T>
}

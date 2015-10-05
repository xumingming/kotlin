import kotlin.test.*

fun <T> copyArray(vararg data: T): Array<out T> = data

fun box(): String {
    val sarr = arrayOf("OK")
    val sarr2 = copyArray(*sarr)
    sarr[0] = "Array was not copied"
    assertEquals(sarr2[0], "OK")

    return "OK"
}
fun foo(s: String, i: Int, li: Long): String = "$s $i $li"

fun box(): String {
    val test = foo("abc", 1, try { 1L } catch (e: Exception) { 10L })
    if (test != "abc 1 1") return "Failed, test==$test"

    return "OK"
}
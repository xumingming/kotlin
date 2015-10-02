import O.p
import O.f

object O {
    @JvmStatic
    fun f(): Int = 3

    @JvmStatic
    val p: Int = 6
}

fun box(): String {
    if (p + f() != 9) return "fail"

    return "OK"
}
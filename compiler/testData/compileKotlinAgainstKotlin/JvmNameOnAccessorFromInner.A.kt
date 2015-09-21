class A {
    private val OK: String = "OK"
        @JvmName("OK") get
    inner class B {
        fun ok(): String = OK
    }
}
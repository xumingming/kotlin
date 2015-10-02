fun Runnable(f: () -> Unit): Runnable = object : Runnable {
    public override fun run() {
        f()
    }
}

val x = <!OVERLOAD_RESOLUTION_AMBIGUITY!>Runnable<!> {  }
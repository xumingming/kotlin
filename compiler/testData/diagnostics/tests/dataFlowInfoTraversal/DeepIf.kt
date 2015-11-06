// !DIAGNOSTICS: -DEBUG_INFO_SMARTCAST
fun bar(x: Int) = x + 1

fun foo() {
    val x: Int? = null

    if (x != null) {
        bar(x)
        if (<!SENSELESS_COMPARISON!>x != null<!>) {
            bar(x)
            if (1 < 2) bar(x)
            if (1 > 2) bar(x)
        }
        if (<!SENSELESS_COMPARISON!>x == null<!>) {
            bar(x)
        }
        if (<!SENSELESS_COMPARISON!>x == null<!>) bar(x) else bar(x)
        bar(bar(x))
    } else if (<!SENSELESS_COMPARISON!><!ALWAYS_NULL!>x<!> == null<!>) {
        bar(<!ALWAYS_NULL, TYPE_MISMATCH!>x<!>)
        if (<!SENSELESS_COMPARISON!><!ALWAYS_NULL!>x<!> != null<!>) {
            bar(x)
            if (<!SENSELESS_COMPARISON!>x <!UNREACHABLE_CODE!>== null<!><!>) <!UNREACHABLE_CODE!>bar(x)<!>
            <!UNREACHABLE_CODE!>if (<!SENSELESS_COMPARISON!>x == null<!>) bar(x) else bar(x)<!>
            <!UNREACHABLE_CODE!>bar(bar(x) + bar(x))<!>
        } else if (<!SENSELESS_COMPARISON!><!ALWAYS_NULL!>x<!> == null<!>) {
            bar(<!ALWAYS_NULL, TYPE_MISMATCH!>x<!>)
        }
    }

}

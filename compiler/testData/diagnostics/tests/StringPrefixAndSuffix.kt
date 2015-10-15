// !DIAGNOSTICS: -UNUSED_PARAMETER

infix fun Any?.foo(a: Any) {}
operator fun Any?.contains(a: Any): Boolean = true

fun test(a: Any) {

    a <!UNSUPPORTED!>foo<!>""
    a <!UNSUPPORTED!>foo<!>"""sdf"""
    a <!UNSUPPORTED!>foo<!>'d'
    a <!UNSUPPORTED!>foo<!><!EMPTY_CHARACTER_LITERAL!>''<!>

    a <!UNSUPPORTED!>foo<!>""<!UNSUPPORTED!>foo<!> a
    a <!UNSUPPORTED!>foo<!>"""sdf"""<!UNSUPPORTED!>foo<!> a
    a <!UNSUPPORTED!>foo<!>'d'<!UNSUPPORTED!>foo<!> a
    a <!UNSUPPORTED!>foo<!><!EMPTY_CHARACTER_LITERAL!>''<!><!UNSUPPORTED!>foo<!> a

    a <!UNSUPPORTED!>in<!>"foo"
    a <!UNSUPPORTED!>in<!>"""foo"""
    a <!UNSUPPORTED!>in<!>'s'
    a <!UNSUPPORTED!>in<!><!EMPTY_CHARACTER_LITERAL!>''<!>

    a <!UNSUPPORTED!>!in<!>"foo"
    a <!UNSUPPORTED!>!in<!>"""foo"""
    a <!UNSUPPORTED!>!in<!>'s'
    a <!UNSUPPORTED!>!in<!><!EMPTY_CHARACTER_LITERAL!>''<!>

    a <!UNSUPPORTED!>foo<!>""1
    a <!UNSUPPORTED!>foo<!>""1.0
    1.0""2


//    foo(r"")
//    foo(r"""sdf""")
//    foo(r'x')
//    foo(r'')
//
//    foo(sdf"")
//    foo(sdf"""sdf""")
//    foo(sdf'x')
//    foo(sdf'')
//
//    foo(as"")
//    foo(as"""sdf""")
//    foo(as'x')
//    foo(as'')
//
//    foo(1"")
//    foo(1"""sdf""")
//    foo(1'x')
//    foo(1'')
//
//    foo(0x1"")
//    foo(0x1"""sdf""")
//    foo(0x1'x')
//    foo(0x1'')
//
//    foo(0b1"")
//    foo(0b1"""sdf""")
//    foo(0b1'x')
//    foo(0b1'')
//
//    foo(1.0"")
//    foo(1.0"""sdf""")
//    foo(1.0'x')
//    foo(1.0'')
//
//    foo(r""i)
//    foo(r"""sdf"""i)
//    foo(r'x'i)
//    foo(r''i)
}
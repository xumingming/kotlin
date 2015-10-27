class A1<T : F?, <!CYCLIC_GENERIC_UPPER_BOUND!>F : T?<!>>

class A2<T : F?, F : E, <!CYCLIC_GENERIC_UPPER_BOUND!>E : F?<!>>

class A3<T, <!CYCLIC_GENERIC_UPPER_BOUND!>F<!>> where T : F?, F : T?

class A4<T, F, <!CYCLIC_GENERIC_UPPER_BOUND!>E<!>> where T : F?, F : E, E : F
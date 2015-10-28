class KotlinAnnotations {

    @k.Anno1()
    @k.Anno2()
    public static void m1() {
    }

    @k.Anno1(c = 3)
    @k.Anno2(c = 3)
    public static void m2() {
    }

    @k.Anno1(d = 5)
    @k.Anno2(g = "asdas")
    public static void m3() {
    }

    @k.Anno1(c = 1, d = 5)
    @k.Anno2(c = {6, 5}, g = "asdas")
    public static void m4() {
    }

    @k.Anno1(x = 1)
    @k.Anno2(x = 2)
    public static void m5() {
    }
}
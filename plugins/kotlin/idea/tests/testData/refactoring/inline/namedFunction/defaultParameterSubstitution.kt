fun oldFun(p1: String, p2: Int = 0, p3: String? = null, p4: String = p1, p5: Int = p2) {
    newFun(p1, p2.toString(), p3, p4, p5)
}

fun newFun(p1: String, p2: String, p3: String?, p4: String, p5: Int) {}

fun foo() {
    old<caret>Fun("a")
}
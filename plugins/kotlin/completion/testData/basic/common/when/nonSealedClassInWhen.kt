// FIR_IDENTICAL
// FIR_COMPARISON
// IGNORE_K1

abstract class SEALED
class AAAA: SEALED()
object BBBB: SEALED()
class CCCC: SEALED()

class SomeClass
object SomeObject

fun foo(e: SEALED) {
    when (e) {
        <caret>
    }
}

// EXIST: is AAAA
// EXIST: BBBB
// EXIST: is CCCC
// EXIST: SEALED
// EXIST: is SomeClass
// EXIST: SomeObject
// EXIST: { lookupString: "else -> "}

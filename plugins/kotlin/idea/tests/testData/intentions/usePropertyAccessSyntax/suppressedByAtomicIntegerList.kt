// WITH_STDLIB
// WITH_JDK
// IS_APPLICABLE: false
// DISABLE_ERRORS
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    val i = AtomicInteger()
    val plain = i.get<caret>Plain()
}
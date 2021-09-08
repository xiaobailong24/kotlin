// EXPECTED_REACHABLE_NODES: 1829
// KJS_WITH_FULL_RUNTIME
// SKIP_DCE_DRIVEN

// FILE: a.kt
package a

import common.*
import kotlin.test.*

class A {
    @BeforeTest
    fun before() {
        call("a.A.before")
    }

    @AfterTest
    fun after() {
        call("a.A.after")
    }

    @Test
    fun passing() {
        call("a.A.passing")
    }

    @Test
    fun failing() {
        call("a.A.failing")
        raise("a.A.failing.exception")
        call("never happens")
    }

    @Ignore
    @Test
    fun ignored() {
        call("a.A.ignored")
    }

    @Test
    fun withException() {
        call("withException")
        raise("some exception")
        call("never happens")
    }

    inner class Inner {
        @Test
        fun innerTest() {
            call("a.A.Inner.innerTest")
        }
    }

    class Nested {
        @Test
        fun nestedTest() {
            call("a.A.Nested.nestedTest")
        }
    }

    companion object {
        @Test
        fun companionTest() {
            call("a.A.companionTest")
        }
    }
}


object O {
    @Test
    fun test() {
        call("a.O.test")
    }
}

// FILE: a_a.kt
package a.a

import common.*
import kotlin.test.*

class A {
    @BeforeTest
    fun before() {
        call("a.a.A.before")
    }

    @AfterTest
    fun after() {
        call("a.a.A.after")
    }

    @Test
    fun passing() {
        call("a.a.A.passing")
    }

    @Test
    fun failing() {
        call("a.a.A.failing")
        raise("a.a.A.failing.exception")
        call("never happens")
    }

    @Ignore
    @Test
    fun ignored() {
        call("a.a.A.ignored")
    }

    @Test
    fun withException() {
        call("withException")
        raise("some exception")
        call("never happens")
    }

    inner class Inner {
        @Test
        fun innerTest() {
            call("a.a.A.Inner.innerTest")
        }
    }

    class Nested {
        @Test
        fun nestedTest() {
            call("a.a.A.Nested.nestedTest")
        }
    }

    companion object {
        @Test
        fun companionTest() {
            call("a.a.A.companionTest")
        }
    }
}

object O {
    @Test
    fun test() {
        call("a.a.O.test")
    }
}

// FILE: a_a2.kt
// RECOMPILE
package a.a

import common.*
import kotlin.test.*

class B {
    @BeforeTest
    fun before() {
        call("a.a.B.before")
    }

    @AfterTest
    fun after() {
        call("a.a.B.after")
    }

    @Test
    fun passing() {
        call("a.a.B.passing")
    }

    @Test
    fun failing() {
        call("a.a.B.failing")
        raise("a.a.B.failing.exception")
        call("never happens")
    }

    @Ignore
    @Test
    fun ignored() {
        call("a.a.B.ignored")
    }

    @Test
    fun withException() {
        call("withException")
        raise("some exception")
        call("never happens")
    }

    inner class Inner {
        @Test
        fun innerTest() {
            call("a.a.B.Inner.innerTest")
        }
    }

    class Nested {
        @Test
        fun nestedTest() {
            call("a.a.B.Nested.nestedTest")
        }
    }

    companion object {
        @Test
        fun companionTest() {
            call("a.a.B.companionTest")
        }
    }
}


object O2 {
    @Test
    fun test() {
        call("a.a.O2.test")
    }
}

// FILE: main.kt

import common.*
import kotlin.test.Test

class Simple {
    @Test fun foo() {
        call("foo")
    }
}

fun box() = checkLog(false) {
    suite("a") {
        suite("A") {
            test("passing") {
                call("a.A.before")
                call("a.A.passing")
                call("a.A.after")
            }
            test("failing") {
                call("a.A.before")
                call("a.A.failing")
                raised("a.A.failing.exception")
                call("a.A.after")
                caught("a.A.failing.exception")
            }
            test("ignored", true) {
                call("a.A.before")
                call("a.A.ignored")
                call("a.A.after")
            }
            test("withException") {
                call("a.A.before")
                call("withException")
                raised("some exception")
                call("a.A.after")
                caught("some exception")
            }
            suite("Inner") {
                test("innerTest") {
                    call("a.A.Inner.innerTest")
                }
            }
            suite("Nested") {
                test("nestedTest") {
                    call("a.A.Nested.nestedTest")
                }
            }
            suite("Companion") {
                test("companionTest") {
                    call("a.A.companionTest")
                }
            }
        }
        suite("O") {
            test("test") {
                call("a.O.test")
            }
        }
    }
    suite("a.a") {
        suite("A") {
            test("passing") {
                call("a.a.A.before")
                call("a.a.A.passing")
                call("a.a.A.after")
            }
            test("failing") {
                call("a.a.A.before")
                call("a.a.A.failing")
                raised("a.a.A.failing.exception")
                call("a.a.A.after")
                caught("a.a.A.failing.exception")
            }
            test("ignored", true) {
                call("a.a.A.before")
                call("a.a.A.ignored")
                call("a.a.A.after")
            }
            test("withException") {
                call("a.a.A.before")
                call("withException")
                raised("some exception")
                call("a.a.A.after")
                caught("some exception")
            }
            suite("Inner") {
                test("innerTest") {
                    call("a.a.A.Inner.innerTest")
                }
            }
            suite("Nested") {
                test("nestedTest") {
                    call("a.a.A.Nested.nestedTest")
                }
            }
            suite("Companion") {
                test("companionTest") {
                    call("a.a.A.companionTest")
                }
            }
        }
        suite("O") {
            test("test") {
                call("a.a.O.test")
            }
        }
        suite("B") {
            test("passing") {
                call("a.a.B.before")
                call("a.a.B.passing")
                call("a.a.B.after")
            }
            test("failing") {
                call("a.a.B.before")
                call("a.a.B.failing")
                raised("a.a.B.failing.exception")
                call("a.a.B.after")
                caught("a.a.B.failing.exception")
            }
            test("ignored", true) {
                call("a.a.B.before")
                call("a.a.B.ignored")
                call("a.a.B.after")
            }
            test("withException") {
                call("a.a.B.before")
                call("withException")
                raised("some exception")
                call("a.a.B.after")
                caught("some exception")
            }
            suite("Inner") {
                test("innerTest") {
                    call("a.a.B.Inner.innerTest")
                }
            }
            suite("Nested") {
                test("nestedTest") {
                    call("a.a.B.Nested.nestedTest")
                }
            }
            suite("Companion") {
                test("companionTest") {
                    call("a.a.B.companionTest")
                }
            }
        }
        suite("O2") {
            test("test") {
                call("a.a.O2.test")
            }
        }
    }
    suite("") {
        suite("Simple") {
            test("foo") {
                call("foo")
            }
        }
    }
}

// FILE: common.kt
package common

import kotlin.test.FrameworkAdapter
import kotlin.collections.*

private var sortingContext = SortingContext()

private var bodyContext: TestBodyContext? = null

fun call(name: String) = bodyContext!!.call(name)

fun raise(name: String): Nothing {
    bodyContext!!.raised(name)
    throw Exception(name)
}

@Suppress("INVISIBLE_MEMBER")
private val underscore = kotlin.test.setAdapter(object : FrameworkAdapter {
    override fun suite(name: String, ignored: Boolean, suiteFn: () -> Unit) {
        sortingContext.suite(name, ignored) { suiteFn() }
    }

    override fun test(name: String, ignored: Boolean, testFn: () -> dynamic) {
        sortingContext.test(name, ignored) { returned(testFn()) }
    }
})

interface SuiteContext {
    fun suite(name: String, ignored: Boolean = false, body: SuiteContext.() -> Unit)

    fun test(name: String, ignored: Boolean = false, body: TestBodyContext.() -> Unit = {})
}


interface TestBodyContext {
    fun call(name: String)

    fun raised(msg: String)

    fun caught(msg: String)

    fun returned(msg: dynamic)
}

private sealed class Entity(val name: String,
                            val ignored: Boolean)

private class Suite(name: String, ignored: Boolean, val body: SuiteContext.() -> Unit): Entity(name, ignored)

private class Test(name: String, ignored: Boolean, val body: TestBodyContext.() -> Unit): Entity(name, ignored)


private class SortingContext: SuiteContext {

    val structure = mutableListOf<Entity>()

    override fun suite(name: String, ignored: Boolean, body: SuiteContext.() -> Unit) {
        structure += Suite(name, ignored, body)
    }

    override fun test(name: String, ignored: Boolean, body: TestBodyContext.() -> Unit) {
        structure += Test(name, ignored, body)
    }

    fun <T: SuiteContext> replayInto(context: T): T {
        structure.sortedBy { it.name }.forEach {
            when (it) {
                is Suite -> context.suite(it.name, it.ignored) {
                    val oldSorter = sortingContext

                    sortingContext = SortingContext()
                    it.body(sortingContext)
                    sortingContext.replayInto(this)

                    sortingContext = oldSorter
                }
                is Test -> context.test(it.name, it.ignored) {
                    bodyContext = this
                    it.body(this)
                    bodyContext = null
                }
            }
        }

        return context
    }
}

private class LoggingContext : SuiteContext, TestBodyContext{
    val log: String
        get() = logHead + (lastRecord ?: "")

    private var indentation = ""

    override fun suite(name: String, ignored: Boolean, body: SuiteContext.() -> Unit) = indent {
        record("suite(\"$name\"${optionalIgnore(ignored)}) {")
        runSafely { this.body() }
        record("}")
    }

    override fun test(name: String, ignored: Boolean, body: TestBodyContext.() -> Unit) = indent {
        val num = record("test(\"$name\"${optionalIgnore(ignored)}) {")

        runSafely { this.body() }

        if (!writtenSince(num)) {
            record("test(\"$name\"${optionalIgnore(ignored)})", replaceLast = true)
        }
        else {
            record("}")
        }
    }

    override fun call(name: String) = indent {
        record("call(\"$name\")")
    }

    override fun raised(msg: String) = indent {
        record("raised(\"$msg\")")
    }

    override fun caught(msg: String) = indent {
        record("caught(\"$msg\")")
    }

    override fun returned(msg: dynamic) = indent {
        if (msg is String) record("returned(\"$msg\")")
    }

    private fun runSafely(body: () -> Unit) {
        try {
            body()
        }
        catch (t: Throwable) {
            caught(t.message ?: "")
        }
    }

    private fun indent(body: () -> Unit) {
        val prevIndentation = indentation
        indentation += "    "
        body()
        indentation = prevIndentation
    }


    private var logHead: String = ""
    private var lastRecord: String? = null
    private var counter = 0

    private fun writtenSince(num: Int) = counter > num

    private fun record(s: String, replaceLast: Boolean = false): Int {
        if (!replaceLast && lastRecord != null) {
            logHead += lastRecord
        }

        lastRecord = indentation + s + "\n"

        return ++counter
    }

    private fun optionalIgnore(ignored: Boolean) = if (ignored) ", true" else ""
}

fun checkLog(wrapInEmptySuite: Boolean = true, body: SuiteContext.() -> Unit): String {
    val expectedContext = SortingContext()
    if (wrapInEmptySuite) {
        expectedContext.suite("") {
            body()
        }
    } else {
        expectedContext.body()
    }

    val expectedLog = expectedContext.replayInto(LoggingContext()).log
    val actualLog = sortingContext.replayInto(LoggingContext()).log

    if (actualLog != expectedLog) {
        return "Failed test structure check. Expected: ${expectedLog}; actual: ${actualLog}."
    }
    else {
        return "OK"
    }
}

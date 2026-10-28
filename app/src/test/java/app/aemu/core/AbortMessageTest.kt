package app.aemu.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AbortMessageTest {
    private fun last(t: String) = Regex(ABORT_MSG).findAll(t).lastOrNull()?.value?.trim()

    @Test fun messageWithInnerQuotesIsKeptWhole() {
        val log = "F libc    : Fatal signal 6 (SIGABRT)\u0000Abort message: 'Binder driver '/dev/binder' could not be opened. Terminating.'\u0000"
        assertEquals("Abort message: 'Binder driver '/dev/binder' could not be opened. Terminating.'", last(log))
    }

    @Test fun plainMessageEndsAtItsQuote() {
        assertEquals("Abort message: 'Check failed: a == b'", last("x Abort message: 'Check failed: a == b'\nnext line"))
    }
}

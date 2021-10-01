package software.amazon.globalaccelerator.arns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ListenerArnTest {

    @Test
    fun validListenerArn() {
        val arn = "arn:aws:globalaccelerator::444607872184:accelerator/48e89142-d196-4918-8e71-63685e3fbff9/listener/7863470e"
        val result = ListenerArn(arn).listenerArn
        assertEquals(arn, result)
    }

    @Test
    fun invalidListenerArn() {
        val arn = "arn:aws:globalaccelerator::444607872184:accelerator/48e89142-d196-4918-8e71-63685e3fbff9/listener/7*863470e"
        val exception = assertThrows(RuntimeException::class.java) {
            ListenerArn(arn).listenerArn
        }
        assertEquals("Invalid Listener ARN $arn", exception.message)
    }

    @Test
    fun nullListenerArn() {
        val arn = null
        val exception = assertThrows(RuntimeException::class.java) {
            ListenerArn(arn).listenerArn
        }
        assertEquals("Accelerator ARN cannot be null or empty $arn", exception.message)
    }
}

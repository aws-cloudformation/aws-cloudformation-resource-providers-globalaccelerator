package software.amazon.globalaccelerator.arns

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ListenerArnTest {

    @Test
    fun validListenerArn() {
        val arn = "arn:aws:globalaccelerator::444607872184:accelerator/48e89142-d196-4918-8e71-63685e3fbff9/listener/7863470e"
        val result = ListenerArn(arn).listenerArn
        Assertions.assertEquals(result, arn)
    }

    @Test
    fun invalidListenerArn() {
        val arn = "arn:aws:globalaccelerator::444607872184:accelerator/48e89142-d196-4918-8e71-63685e3fbff9/listener/7*863470e"
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            ListenerArn(arn).listenerArn
        }
        Assertions.assertEquals("Invalid listener arn $arn", exception.message)
    }

    @Test
    fun nullListenerArn() {
        val arn = null
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            ListenerArn(arn).listenerArn
        }
        Assertions.assertEquals("Accelerator arn cannot be null or empty $arn", exception.message)
    }
}
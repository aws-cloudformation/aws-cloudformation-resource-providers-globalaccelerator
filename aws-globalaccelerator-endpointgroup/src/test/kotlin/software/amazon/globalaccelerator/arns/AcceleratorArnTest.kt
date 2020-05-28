package software.amazon.globalaccelerator.arns

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class AcceleratorArnTest {

    @Test
    fun validAcceleratorArn() {
        val arn = "arn:aws:globalaccelerator::444607872184:accelerator/48e89142-d196-4918-8e71-63685e3fbff9"
        val result = AcceleratorArn(arn).acceleratorArn
        Assertions.assertEquals(result, arn)
    }

    @Test
    fun invalidAcceleratorArn1() {
        val arn = "arn:globalaccelerator::444607872184:accelerator/48e89142-d196-4918-8e71-63685e3fbff9"
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            AcceleratorArn(arn).acceleratorArn
        }
        Assertions.assertEquals("Invalid Accelerator ARN $arn", exception.message)
    }

    @Test
    fun invalidAcceleratorArn2() {
        val arn = "arn:aws:globalaccelerat::444607872184:accelerator/48e89142-d196-4918-8e71-63685e3fbff9"
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            AcceleratorArn(arn).acceleratorArn
        }
        Assertions.assertEquals("Invalid Accelerator ARN $arn", exception.message)
    }

    @Test
    fun nullAcceleratorArn() {
        val arn = null
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            AcceleratorArn(arn).acceleratorArn
        }
        Assertions.assertEquals("Accelerator ARN cannot be null or empty $arn", exception.message)
    }
}

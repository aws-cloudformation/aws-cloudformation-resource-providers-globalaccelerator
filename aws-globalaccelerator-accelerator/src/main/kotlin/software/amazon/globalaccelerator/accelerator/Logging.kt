package software.amazon.globalaccelerator.accelerator

import software.amazon.cloudformation.proxy.Logger

fun Logger.logInfo(str: String) {
    this.log("[INFO]" + str)
}
fun Logger.logError(str: String) {
    this.log("[ERROR]" + str)
}
fun Logger.logDebug(str: String) {
    this.log("[DEBUG]" + str)
}

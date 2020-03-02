package software.amazon.globalaccelerator.accelerator

import software.amazon.cloudformation.proxy.Logger

/**
 * Log INFO messages
 *
 * @param str the message to emit to log
 */
fun Logger.info(str: String) {
    this.log("[INFO] - $str")
}

/**
 * Log ERROR messages
 *
 * @param str the message to emit to log
 */
fun Logger.error(str: String) {
    this.log("[ERROR] - $str")
}

/**
 * Log DEBUG messages
 *
 * @param str the message to emit to log
 */
fun Logger.debug(str: String) {
    this.log("[DEBUG] - $str")
}

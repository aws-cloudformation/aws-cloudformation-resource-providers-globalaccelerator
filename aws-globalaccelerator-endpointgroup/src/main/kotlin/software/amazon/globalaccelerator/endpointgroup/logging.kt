package software.amazon.globalaccelerator.endpointgroup

import software.amazon.cloudformation.proxy.Logger

fun Logger.info(str: String) {
    this.log("[INFO] - $str")
}
fun Logger.error(str: String) {
    this.log("[ERROR] - $str")
}
fun Logger.debug(str: String) {
    this.log("[DEBUG] - $str")
}

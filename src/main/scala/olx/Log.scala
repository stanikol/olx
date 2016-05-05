package olx

import ch.qos.logback.classic.Logger
import org.slf4j.{LoggerFactory, MDC}


/**
  * Created by stanikol on 05.05.16.
  */
object Log {
  val rootLogger = LoggerFactory.getLogger("ROOT")
  private val saveAdvLogger = LoggerFactory.getLogger("SCRAP")
  def saveAdv(adv: Adv, savePath: String) = {
    MDC.put("savePath", savePath)
    saveAdvLogger.info(adv.toString())
  }

}

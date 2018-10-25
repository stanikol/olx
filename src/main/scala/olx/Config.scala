package olx

import com.typesafe.config.ConfigFactory
object Config {

  val cfg = ConfigFactory.load()

  val numberOfDownloadThreads = cfg.getInt("olx.numberOfDownloadThreads")


}

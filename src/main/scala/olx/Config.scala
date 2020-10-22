package olx

import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

import com.typesafe.config.{ConfigFactory, Config => ConfigTypesafe}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object Config {

  case class MongoDb(url: String, database: String)
  private val cfg: ConfigTypesafe = ConfigFactory.parseFile(new File("olx.conf")).withFallback(ConfigFactory.load())

  val numberOfDownloadThreads: Int = cfg.getInt("olx.numberOfDownloadThreads")
  val retries: Int = cfg.getInt("olx.retries")
  val throttleElements: Int = cfg.getInt("olx.throttleElements")
  val throttleInterval: FiniteDuration = FiniteDuration(cfg.getDuration("olx.throttleInterval").toMillis, TimeUnit.MILLISECONDS)
  val proxies: Option[String] = Try(cfg.getString("olx.proxies")).toOption
  val proxiesChecked: String = cfg.getString("olx.proxiesChecked")
  val mongo: MongoDb = MongoDb(cfg.getString("olx.mongo.url"), cfg.getString("olx.mongo.database"))
  val userAgent: String = cfg.getString("olx.userAgent")
  val clientConnectingTimeout: FiniteDuration = FiniteDuration(cfg.getDuration("olx.clientConnectingTimeout").toMillis, TimeUnit.MILLISECONDS)

}

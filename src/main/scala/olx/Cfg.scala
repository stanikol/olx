package olx

import java.io.File

import akka.stream.ActorMaterializer
import play.api.libs.ws.ahc

import scala.collection.JavaConversions._
import scala.concurrent.duration.Duration

/**
  * Created by stanikol on 24.03.16.
  *
  *
  */
object Cfg {
//  val config = com.typesafe.config.ConfigFactory.load()
  val config = com.typesafe.config.ConfigFactory.parseFile(new File("/Users/snc/scala/olx/src/main/resources/application.conf"))

  def apply(s: String): String = config.getString(s)

  val savedir: File = new File(apply("olx.savedir"))

//  val firefox = apply("olx.firefox_binary")

  val targets: Map[String, String] =
    ( for( (k,v) <- config.getObject("olx.targets") )
        yield k -> v.render.replaceAll("^\"|\"$", "") ).toMap

  val number_of_fetchers = config.getInt("olx.number_of_fetchers")

  val error_message_start = config.getString("olx.error_message_start")

  val empty_page_stop  = config.getInt("olx.empty_page_stop")

  val kill_actor_when_no_response  = Duration.fromNanos(config.getDuration("olx.kill_actor_when_no_response").toNanos)

  val tick_time  = Duration.fromNanos(config.getDuration("olx.tick_time").toNanos)

  val save_path_time_suffix  = config.getString("olx.save_path_time_suffix")

//  val number_of_ads_to_download  = config.getInt("olx.number_of_ads_to_download")

  val target = config.getString("target")

  val url =
    if(Cfg.targets.contains(Cfg.target))
      Cfg.config.getString(s"olx.targetsIterator.$target")
    else
      config.getString("url")

  val terminate_after = Duration.fromNanos(config.getDuration("olx.terminate_after").toNanos)

  val brief_regexes = config.getStringList("olx.brief_regexes")

  val retries_when_same_next_page_occurs = config.getInt("olx.retries_when_same_next_page_occurs")

  val number_of_pages_without_new_links = config.getInt("olx.number_of_pages_without_new_links")

}

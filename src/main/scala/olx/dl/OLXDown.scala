package olx.dl

import akka.actor.ActorSystem
import olx.Cfg

import scala.concurrent.ExecutionContext

/**
  * Created by stanikol on 06.04.16.
  */
object OLXDown {

  def main(args: Array[String]) {

      val target = Cfg.target
      println(target)
      val url = Cfg.url
      if(url == "") {
        println("ERROR: use with -Dtarget= ... -Durl= ...")
        sys.exit(-1)
      }

      if(!Cfg.savedir.exists()) Cfg.savedir.mkdirs()

      implicit val ec = ExecutionContext.Implicits.global

      val actorSystem = ActorSystem("olxDownloaders")
      val fetchManager = actorSystem.actorOf(DlMan.props(target, Cfg.number_of_fetchers))
      println(Cfg.target)
      fetchManager ! DlMan.Start(url, Cfg.number_of_ads_to_download)

      actorSystem.scheduler.scheduleOnce(Cfg.terminate_after){
        actorSystem.terminate }



  }

}

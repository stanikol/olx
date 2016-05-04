package olx.down


/**
  * Created by stanikol on 21.04.16.
  */
object WSDown {

  import akka.actor._
  import akka.pattern.ask
  import olx.Cfg

  import scala.concurrent.ExecutionContext

  /**
    * Created by stanikol on 06.04.16.
    */

  var nextTarget = false

  class MainActor extends Actor {
    def receive: Receive = {
      case "Done" => nextTarget = true
    }
  }

    def main(args: Array[String]) {

      if(!Cfg.savedir.exists()) Cfg.savedir.mkdirs()

      implicit val ec = ExecutionContext.Implicits.global

      val actorSystem = ActorSystem("olxDownloaders")
      val downMan = actorSystem.actorOf(Props(classOf[DownMan]), name = "DownMan")

      for((target, url)<-Cfg.targets){
        println(s"Starting $target $url")
        val f = ask(downMan, DownMan.DownloadUrl(url,target))(Cfg.terminate_after)
        while(!f.isCompleted){}
        println(s"Finished $target $url")
      }
      println("Terminating all jobs !")
      actorSystem.terminate()

    }

}

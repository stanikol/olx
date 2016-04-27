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
      val downMan = actorSystem.actorOf(Props(classOf[DownMan], target), name = "DownMan")
      println(Cfg.target)

    val f = ask(downMan, DownMan.DownloadUrl(url))(Cfg.terminate_after)
    while (!f.isCompleted){}

    }

}

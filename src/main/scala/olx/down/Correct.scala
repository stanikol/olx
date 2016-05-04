package olx.down

import java.io.{File, FileWriter, FilenameFilter}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import olx.Adv

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Created by stanikol on 27.04.16.
  */
object Correct  extends  {
  implicit val ec = ExecutionContext.Implicits.global

//  val ads = olx.Adv.readDB

  def correctFile(src: File, dl: ActorRef): Unit = {
    val target = "CORRECT"
  //  assert(src.isFile)
    val dst = new File(target, src.getName)
    new File(target).mkdirs()
    val ads = olx.Adv.readFromFile(src).get


      for {ad <- ads.toList} {
        val badItems = ad.items.filter{ case (k,v) => !List("href","user").contains(k) && v.isEmpty }.keys
        val updatedAdFuture: Future[Adv] =
          if (badItems.nonEmpty) {
            println(s"Found invalid keys in ${ad.url}: $badItems")
            ask(dl, Downl.FetchAdv(ad.url))(55 seconds).mapTo[Adv]
          } else { Future.successful(ad) }
        updatedAdFuture.onSuccess { case updatedAd =>
          new FileWriter(dst, true) {
            write(updatedAd + ",\n")
            close()
          }
        }
      }
  }


  def main(args: Array[String]): Unit = {

    val actorSystem = ActorSystem("olxDownloaders")
    val dl = actorSystem.actorOf(Props(classOf[Downl]), name = "DL")


    val srcDir = new File("/Users/snc/scala/olx/down/")
    val srcFiles = srcDir.list(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.endsWith(".log")
    }).toList.map(s=> new File("/Users/snc/scala/olx/down/", s))

    srcFiles.foreach{ file=>
      println(file.getAbsolutePath)
      correctFile(file, dl)
    }
    println(s"Done.")
  }






}

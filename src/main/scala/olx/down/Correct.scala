package olx.down

import java.io.{File, FileWriter, FilenameFilter}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask
import olx.{Adv, Cfg, Data, Error}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Success, Try}
import scala.collection.JavaConversions._

/**
  * Created by stanikol on 27.04.16.
  */
object Correct  extends App {


//  val ads = olx.Adv.readDB

  def correctFile(src: File, dl: ActorRef): Unit = {
    val target = "CORRECT"
  //  assert(src.isFile)
    val dst = new File(target, src.getName)
    new File(target).mkdirs()
    val ads = olx.Adv.readFromFile(src).get


    new FileWriter(dst) {
      for {ad <- ads.toList} {
        val badItems = ad.items.filter(m => !List("href","user").contains(m._1) && m._2.isEmpty).keys
        val newAd = if (badItems.nonEmpty) {
          println(s"Found invalid keys in ${ad.url}: $badItems")
          val f = ask(dl, Downl.FetchAdv(ad.url))(11 seconds).mapTo[Adv]
          Try(Await.result(f, 11 seconds)) match {
            case Success(newAd: Adv) =>
              println(s"Finished $newAd")
              val er = newAd.items.filter(m => m._1 != "user" && m._2.isEmpty)
              assert(er.isEmpty, er.toString)
              newAd
            case _ =>
              println(s"Error ${ad.url}")
  //            Error(ad.url, "")
              ad
          }
        } else {
          ad
        }
        newAd match {
          case ad @ Adv(_) => write(ad + ",\n")
          case _ =>
        }
      }
      close()
    }
}


  val actorSystem = ActorSystem("olxDownloaders")
  val dl = actorSystem.actorOf(Props(classOf[Downl]), name = "DL")
  implicit val ec = ExecutionContext.Implicits.global

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

package olx.dl

import java.io.{File, FileWriter, PrintWriter}
import java.nio.charset.StandardCharsets

import olx.{Adv, Cfg}
import org.apache.commons.lang3.StringEscapeUtils._
import org.joda.time.format.ISODateTimeFormat

import scala.util.{Success, Try}

/**
  * Created by stanikol on 13.04.16.
  */
object Utils {


  def copyDown(from: File = new File("/Users/snc/scala/olx/down/"),
               to: File   = new File("/Users/snc/scala/olx/down2/"),
               level: Int = 1)  =
  {
    // TODO phones ! Часто два кототких телефона сливаются в один напр: 705-05-05 444-54-09 превращаются в 70505054445409. Tелефоны не разделены \n !!
    val obligedFields = List("siteid", "brief", "text", "added", "url", "user", "phones",
      "price", "head", "location", "userName", "section", "viewed", "scraped", "filename", "target")
    val ads = Adv.readFromFile(from).get
               .filter(ad => obligedFields.forall(ad.items.contains(_)))
                .map { ad =>
                  new Adv(
                    ad.parseBriefs ++
                      ad.items
                        .updated("filename", ad.getOrElse("filename", "no_filename_tag").split(File.separator).last
                                                  .split(" ").slice(0, level).mkString(" "))
                          .updated("phones", raw"\(\)\-\s\+".r.replaceAllIn(ad.getOrElse("phones"), ""))
            //                .updated("Добавлено", Try(ISODateTimeFormat.dateHourMinuteSecond.print(ad.parseTimeAdded)).getOrElse("") )
                            .updated("Добавлено", ISODateTimeFormat.dateHourMinuteSecond.print(ad.parseTimeAdded) )
                              .map { case (k, v) =>
                                (k, if(v.toUpperCase().startsWith(Cfg.error_message_start)) "" else v)
                              }
                  )
              }
    val byFilename = ads.groupBy(_.getOrElse("filename"))
    for((newFilename, adsf) <- byFilename.iterator){
      val newFile = new File(to.getAbsolutePath, s"${newFilename}.log")
      saveAds(adsf, newFile)
      println(s"Saved to ${newFile.getAbsolutePath} ${adsf.length} ads")
    }
  }

  def saveAds(ads: Stream[Adv], file: File) =
    new PrintWriter(file, StandardCharsets.UTF_8.toString){
      for(a <- ads) write(a.toString+",\n")
      close()
    }

  def exportToTSV(pathToSavedAds: String, tsvFilename:String): Unit =
  {
    val ads: List[Adv] = Adv.readFromFile(new File(pathToSavedAds)).get.map{ad => new Adv(ad.items ++ ad.parseBriefs)}.toList
    val headers: List[String] = ads.map(_.items.keySet)
        .foldLeft(Set.empty[String])((a, n) => a ++ n).toList
    val csvText = headers.mkString("\t") + "\n" +
      ads.map(ad =>
        headers.map{ a=>
          val item = ad.items.getOrElse(a, "") match {
            case i if i.toUpperCase.startsWith(Cfg.error_message_start.toUpperCase) => ""
            case i => i
          }
          escapeCsv(item)
        }.mkString("\t")
      ).mkString("\n")
    new PrintWriter(tsvFilename, StandardCharsets.UTF_8.toString){
      write(csvText)
      close()
    }
  }

}


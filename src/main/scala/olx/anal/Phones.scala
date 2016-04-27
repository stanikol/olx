package olx.anal

import olx.Adv

/**
  * Created by stanikol on 19.04.16.
  */
object Phones {

//  case class Freq(phone: String, userName: String, location: String, count: Int)
  def getPhoneInfo(ad: Adv) =
      for {
        phone0 <- ad.getOrElse("phones").split("\n").toList //if phone0.matches("""\d+""")
        phone = """[\-\s\(\)]""".r.replaceAllIn(phone0, "")
      } yield (phone, ad.parseUserID, ad.getOrElse("userName"), ad.getOrElse("location"))
//      } yield (phone, ad.parseUserID, ad.getOrElse("userName"))
//      } yield (ad.parseUserID, ad.getOrElse("userName"))

  def freqs = {
    val phones =
      for {
        ad        <- Adv.readFromFile().get if ad.items("phones").length > 0
        phoneInfo <- getPhoneInfo(ad)
      } yield phoneInfo
    phones.groupBy(x=>x).mapValues(_.length).toList.sortWith(_._2 > _._2)
  }

}

package olx


import spray.json.RootJsonFormat
import spray.json.DefaultJsonProtocol._

final case class Order(olxUrl: String, max: Int, collection: String)

object Order {
  implicit val orderFormat: RootJsonFormat[Order] = jsonFormat3(Order.apply)
}

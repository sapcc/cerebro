package models

case class Host(name: String, authentication: Option[List[ESAuth]] = None, headersWhitelist: Seq[String] = Seq.empty)

package models

import javax.inject.Singleton

import com.google.inject.{ImplementedBy, Inject}
import play.api.Configuration

import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}


@ImplementedBy(classOf[HostsImpl])
trait Hosts {

  def getHostNames(): Seq[String]

  def getHost(name: String): Option[Host]

}
// HERE
@Singleton
class HostsImpl @Inject()(config: Configuration) extends Hosts {

  val hosts: Map[String, Host] = Try(config.underlying.getConfigList("hosts").asScala.map(Configuration(_))) match {
    case Success(hostsConf) => hostsConf.map { hostConf =>
      val host = hostConf.getOptional[String]("host").get
      val name = hostConf.getOptional[String]("name").getOrElse(host)
      val headersWhitelist = hostConf.getOptional[Seq[String]](path = "headers-whitelist").getOrElse(Seq.empty[String])

      val username = hostConf.getOptional[String]("auth.username")
      val password = hostConf.getOptional[String]("auth.password")
      val username2 = hostConf.getOptional[String]("auth2.username")
      val password2 = hostConf.getOptional[String]("auth2.password")
      val creds = List.empty[ESAuth]
      (username, password) match {
        case (Some(username), Some(password)) => creds :+ Some(ESAuth(username, password))
        case _ => None
      }
      (username2, password2) match {
        case (Some(username2), Some(password2)) => creds :+ Some(ESAuth(username2, password2))
        case _ => None
      }
      Console.println("username password = " + username + " " + password)
      Console.println("username2 password2 = " + username2 + " " + password2)
      Console.println("creds = " + creds)
      name -> Host(host, Some(creds), headersWhitelist)
    }.toMap

    case Failure(_) => Map()
  }

  def getHostNames() = hosts.keys.toSeq

  def getHost(name: String) = hosts.get(name)

}

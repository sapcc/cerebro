package models

import javax.inject.Singleton
import java.util.NoSuchElementException
import exceptions.{MissingHostsCredentials}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

import com.google.inject.{ImplementedBy, Inject}
import play.api.Configuration
import elastic.{HTTPElasticClient, ElasticResponse, Error, Success}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}
import java.net.ConnectException

import akka.actor.ActorSystem
import akka.stream.Materializer
import play.api.libs.ws._
import scala.concurrent.{ExecutionContext, Future}

import java.net.ConnectException
import play.api.Logger

@ImplementedBy(classOf[HostsImpl])
trait Hosts {

  def getHostNames(): Seq[String]

  def getHost(name: String): Option[Host]

}

@Singleton
class HostsImpl @Inject()(config: Configuration, client: WSClient)(implicit system: ActorSystem, mat: Materializer) extends Hosts {
  private val log = Logger("application")

  def fetch(request: WSRequest, retries: Int = 3, delay: FiniteDuration = 5.seconds): Future[WSResponse] = {
    request.execute().flatMap { response =>
      if (response.status == 200) {
        log.info(s"OK Response")
        Future.successful(response) // Return response if status is 200
      } else {
        log.warn(s"Received status ${response.status}, ${response.body}")
        Future.failed(new Exception(s"Failed after retries. Last status: ${response.status}"))
      }
    }
  }

  def execute[T](uri: String,
                 method: String,
                 body: Option[String] = None,
                 target: ElasticServer,
                 headers: Seq[(String, String)] = Seq()) : Future[Option[elastic.ElasticResponse]] = {
    val authentication = target.host.authentication
    val url = s"${target.host.name.replaceAll("/+$", "")}$uri"

    val mergedHeaders = headers ++ target.headers

    val request =
      authentication.foldLeft(client.url(url).withMethod(method).withHttpHeaders(mergedHeaders: _*)) {
      case (request, auth) =>
        {
          request.withAuth(auth.username, auth.password, WSAuthScheme.BASIC).withHttpHeaders("WWW-Authenticate" -> "Basic")
        }
    }
    fetch(body.fold(request)(request.withBody((_))))
    .map {case response => Some(ElasticResponse(response)) }
    .recover {case ex => None}
  }

  def clusterHealth(target: ElasticServer): Future[Option[ElasticResponse]] = {
    val path = "/_cluster/health"
    execute(path, "GET", None, target)
  }


  val hosts: Map[String, Host] = Try(config.underlying.getConfigList("hosts").asScala.map(Configuration(_))) match {
    case scala.util.Success(hostsConf) => hostsConf.map { hostConf =>
      val host = hostConf.getOptional[String]("host").get
      val name = hostConf.getOptional[String]("name").getOrElse(host)
      val headersWhitelist = hostConf.getOptional[Seq[String]](path = "headers-whitelist").getOrElse(Seq.empty[String])
      var i = 0
      var attemptedCredentials = List.empty[Option[models.Host]]
      while (i < 10 && attemptedCredentials.flatten.isEmpty) {
        attemptedCredentials = (for (failoverAuth <- List("auth", "auth2")) yield {
          log.info(s"Testing credentials provided in block ${failoverAuth}")
          val username = hostConf.getOptional[String](s"$failoverAuth.username").getOrElse("")
          val password = hostConf.getOptional[String](s"$failoverAuth.password").getOrElse("")
          val creds = Host(host, Some(ESAuth(username, password)), headersWhitelist)
          Await.result(clusterHealth(ElasticServer(creds, List())).map {
            case Some(response) => (response) match {
                case elastic.Success(status, health) => Some(creds)
                case elastic.Error(status, error) => None
            }
            case None => None
          }, Duration.Inf)
        })
        if (attemptedCredentials.flatten.isEmpty) {
          log.info("Backing off for 5s...")
          Thread.sleep(5000)
          log.info("Retrying credentials...")
        }
        i += 1
      }
      try {
        val okCreds = attemptedCredentials.flatten.head
        log.info(s"Found successful credentials; will use ${okCreds.authentication.get.username}.")
        name -> okCreds
      } catch {
        case e => 
        log.error(s"Missing credentials: ${e.getMessage}", throw MissingHostsCredentials(s"None of the provided credentials worked for '${name}', on '${host}'"))
        name -> Host(host, Some(ESAuth("MISSING_USERNAME","MISSING_PASSWORD")), headersWhitelist)
      }
    }.toMap
    case Failure(_) => Map()
  }

  def getHostNames() = hosts.keys.toSeq

  def getHost(name: String) = hosts.get(name)

}

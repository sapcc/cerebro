package exceptions

case class MissingHostsCredentials(str: String) extends Exception(str)

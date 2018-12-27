package io.github.ahappypie.unica

import akka.actor.{Actor, Props}
import io.github.ahappypie.unica.grpc.unica.{UnicaRequest, UnicaResponse}

object UnicaActor {
  def props: Props = Props(new UnicaActor)
}

class UnicaActor extends Actor {
  override def receive: Receive = {
    case req: UnicaRequest => sender ! UnicaResponse(generate())
  }

  def generate(): Long = {
    1L
  }
}

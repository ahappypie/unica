package io.github.ahappypie.unica

import akka.actor.{Actor, Props}
import io.github.ahappypie.unica.grpc.unica.UnicaRequest

object UnicaSupervisor {
  def props: Props = Props(new UnicaSupervisor)
}

class UnicaSupervisor extends Actor {
  override def receive: Receive = {
    case req: UnicaRequest => context.actorOf(UnicaActor.props).forward(req)
  }
}

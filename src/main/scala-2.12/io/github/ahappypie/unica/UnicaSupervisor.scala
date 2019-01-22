package io.github.ahappypie.unica

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import io.github.ahappypie.unica.grpc.unica.{UnicaRequest}

object UnicaSupervisor {
  def props(deploymentId: Long): Props = Props(new UnicaSupervisor(deploymentId))
}

class UnicaSupervisor(deploymentId: Long) extends Actor with ActorLogging {

  var worker: ActorRef = null

  override def preStart(): Unit = {
    super.preStart()
      worker = context.actorOf(UnicaActor.props(deploymentId))
  }

  override def receive: Receive = {
    case req: UnicaRequest => worker.forward(req)
  }
}

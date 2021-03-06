package sprest.routing

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.ExecutionContext
import scala.transient
import spray.testkit.Specs2RouteTest
import spray.routing.HttpService
import spray.http.StatusCodes._
import sprest.models._
import spray.json._
import scala.concurrent.Future

import sprest.security._

class RestSpec extends Specification
  with Specs2RouteTest
  with HttpService
  with RestRoutes
  with spray.httpx.SprayJsonSupport
  with DefaultJsonProtocol {

  case class MockUser(userId: String) extends User {
    type ID = String
  }

  case class MockSession(sessionId: String, user: MockUser) extends Session {
    type ID = String
  }

  type SessionImpl = MockSession

  val mockSession = MockSession("abcds", MockUser("first"))

  override def maybeSession = provide(Some(mockSession))

  def actorRefFactory = system

  case class IntModel(var id: Option[Int], name: String, userId: String) extends Model[Int]

  implicit val IntModelFormat = jsonFormat(IntModel, "id", "name", "userId")

  class IntDAO extends DAO[IntModel, Int]
    with MutableListDAO[IntModel, Int]
    with IntId {

    override protected def addImpl(m: IntModel)(implicit ec: ExecutionContext) = Future.successful {
      m.id = nextId
      _all += m
      m
    }

  }

  "REST routes" should {
    "GET returns all filtered by session context" in new RoutesContext {
      Get("/ints") ~> intRoutes ~> check {
        val result = entityAs[List[IntModel]]
        result must have size 3
      }
    }

    "POST adds" in new RoutesContext {
      Post("/ints", IntModel(None, "third", "first")) ~> intRoutes ~> check {
        val model = entityAs[IntModel]
        model.id must_== Some(4)
        model.name must_== "third"
      }
    }

    "GET by id returns single model" in new RoutesContext {
      Get("/ints/2") ~> intRoutes ~> check {
        val m = entityAs[IntModel]
        m.name must_== "second"
      }
    }

    "GET by id does not exists responds with NotFound" in new RoutesContext {
      Get("/ints/4") ~> intRoutes ~> check {
        status === NotFound
      }
    }

    "PUT by id updates model" in new RoutesContext {
      Put("/ints/2", IntModel(Some(2), "2nd", "2nd")) ~> intRoutes ~> check {
        entityAs[IntModel].name must_== "2nd"
      }
    }

    "DELETE by id removes model" in new RoutesContext {
      Get("/ints") ~> intRoutes ~> check {
        entityAs[List[IntModel]] must have size 3
        Delete("/ints/1") ~> intRoutes ~> check {
          Get("/ints") ~> intRoutes ~> check {
            entityAs[List[IntModel]] must have size 2
          }
        }
      }
    }
  }

  trait RoutesContext extends Scope {

    val intDAO = new IntDAO
    val intRoutes = restInt("ints", intDAO)

    intDAO.add(IntModel(None, "first", "first"))
    intDAO.add(IntModel(None, "second", "second"))
    intDAO.add(IntModel(None, "anotherfirst", "first"))
  }
}
